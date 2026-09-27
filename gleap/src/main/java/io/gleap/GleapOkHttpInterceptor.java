package io.gleap;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.GzipSource;
import okio.Okio;
import okio.Source;

/**
 * Records the requests of an OkHttp client in the Gleap network logs.
 * <pre>
 * OkHttpClient client = new OkHttpClient.Builder()
 *         .addInterceptor(new GleapOkHttpInterceptor())
 *         .build();
 * </pre>
 * Every request is logged with its method, url, headers, status, duration and text bodies (JSON,
 * XML, text and forms, up to 150 KB each). The response body is copied while the app reads it, so
 * requests and responses are never changed or delayed; binary and streaming bodies are left out.
 * Failed requests are logged with their error. The newest 30 requests are kept.
 * <p>
 * The network log blacklist and props to ignore (dashboard, {@link Gleap#setNetworkLogsBlacklist},
 * {@link Gleap#setNetworkLogPropsToIgnore}) are applied when a ticket is sent. Authorization and
 * cookie headers are always masked, requests to Gleap are never logged.
 * <p>
 * Added with {@code addInterceptor} it logs the requests as the app sends them. Added with
 * {@code addNetworkInterceptor} it logs every request that goes to the network, with the headers
 * OkHttp adds; redirects and retries are then logged separately.
 * <p>
 * This replaces the separate {@code io.gleap:gleap-okhttp-interceptor} artifact; remove that
 * dependency, the class name is the same.
 */
public final class GleapOkHttpInterceptor implements Interceptor {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final int KIND_TEXT = 0;
    // No content type: captured when it is UTF-8 text.
    private static final int KIND_UNKNOWN = 1;
    private static final int KIND_BINARY = 2;
    private static final int KIND_STREAMING = 3;

    public GleapOkHttpInterceptor() {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        long startMillis = System.currentTimeMillis();
        long startNanos = System.nanoTime();

        boolean record = true;
        JSONObject requestLog = null;
        try {
            record = !GleapNetworkLogSanitizer.isBlacklistedByConfig(request.url().toString());
            if (record) {
                requestLog = describeRequest(request);
            }
        } catch (Throwable ignore) {
        }

        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException | RuntimeException error) {
            if (record) {
                recordFailure(request, requestLog, startMillis, startNanos, error);
            }
            throw error;
        }

        if (!record) {
            return response;
        }
        try {
            return recordResponse(request, requestLog, response, startMillis, startNanos);
        } catch (Throwable ignore) {
            return response;
        }
    }

    private static JSONObject describeRequest(Request request) {
        JSONObject log = new JSONObject();
        try {
            JSONObject headers = headersToJson(request.headers());
            RequestBody body = request.body();
            if (body != null) {
                MediaType contentType = body.contentType();
                if (contentType != null && request.header("Content-Type") == null) {
                    // Application interceptors run before OkHttp copies the body type into the headers.
                    headers.put("Content-Type", contentType.toString());
                }
                log.put("payload", describeRequestBody(request, body, contentType));
            }
            log.put("headers", headers);
        } catch (Throwable ignore) {
        }
        return log;
    }

    private static String describeRequestBody(Request request, RequestBody body, MediaType mediaType) {
        try {
            if (body.isDuplex()) {
                return Networklog.STREAMING_BODY_OMITTED;
            }
            int kind = classify(mediaType != null ? mediaType.toString() : request.header("Content-Type"));
            if (kind == KIND_STREAMING) {
                return Networklog.STREAMING_BODY_OMITTED;
            }
            if (kind == KIND_BINARY || isEncoded(request.header("Content-Encoding"))) {
                return Networklog.BINARY_BODY_OMITTED;
            }
            if (body.isOneShot()) {
                // Writing it would consume the only copy.
                return Networklog.BODY_NOT_CAPTURED;
            }
            long length = body.contentLength();
            if (length == 0) {
                return "";
            }
            if (length < 0 || length > Networklog.BODY_CAP) {
                return Networklog.BODY_NOT_CAPTURED;
            }
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            long size = buffer.size();
            byte[] bytes = buffer.readByteArray(Math.min(size, Networklog.BODY_CAP));
            buffer.clear();
            if (kind == KIND_UNKNOWN && !isProbablyUtf8(bytes, bytes.length)) {
                return Networklog.BINARY_BODY_OMITTED;
            }
            return decode(bytes, bytes.length, charsetOf(mediaType), size > Networklog.BODY_CAP, String.valueOf(size));
        } catch (Throwable error) {
            return Networklog.BODY_NOT_CAPTURED;
        }
    }

    private static Response recordResponse(Request request, JSONObject requestLog, Response response,
                                           long startMillis, long startNanos) throws Exception {
        JSONObject responseLog = new JSONObject();
        responseLog.put("status", response.code());
        String message = response.message();
        responseLog.put("statusText", message != null ? message : "");
        responseLog.put("headers", headersToJson(response.headers()));

        Networklog entry = new Networklog(request.method(), request.url().toString(), startMillis,
                elapsedMillis(startNanos), true, requestLog != null ? requestLog : new JSONObject(), responseLog);

        Response result = response;
        ResponseBody body = response.body();
        String responseText = responseTextWithoutReading(request, response, body);
        if (responseText != null) {
            responseLog.put("responseText", responseText);
        } else {
            String encoding = response.header("Content-Encoding");
            BodyCapture capture = new BodyCapture(
                    charsetOf(body.contentType()),
                    classify(contentTypeOf(response, body)) == KIND_UNKNOWN,
                    encoding != null && "gzip".equalsIgnoreCase(encoding.trim()),
                    body.contentLength());
            result = response.newBuilder().body(new CapturingResponseBody(body, capture)).build();
            entry.setPendingResponseText(capture);
        }
        GleapBug.getInstance().addRequest(entry);
        return result;
    }

    /**
     * The response text when the body does not have to be read, or null when it is captured while
     * the app reads it.
     */
    private static String responseTextWithoutReading(Request request, Response response, ResponseBody body) {
        int code = response.code();
        if (code == 101) {
            // Switching protocols, e.g. a web socket.
            return Networklog.STREAMING_BODY_OMITTED;
        }
        if (body == null || "HEAD".equalsIgnoreCase(request.method()) || (code >= 100 && code < 200) || code == 204 || code == 304) {
            return "";
        }
        int kind = classify(contentTypeOf(response, body));
        if (kind == KIND_STREAMING) {
            return Networklog.STREAMING_BODY_OMITTED;
        }
        if (kind == KIND_BINARY) {
            return Networklog.BINARY_BODY_OMITTED;
        }
        String encoding = response.header("Content-Encoding");
        if (isEncoded(encoding) && !"gzip".equalsIgnoreCase(encoding.trim())) {
            return Networklog.BINARY_BODY_OMITTED;
        }
        if (body.contentLength() == 0) {
            return "";
        }
        return null;
    }

    private static void recordFailure(Request request, JSONObject requestLog, long startMillis, long startNanos, Throwable error) {
        try {
            JSONObject responseLog = new JSONObject();
            responseLog.put("errorText", Networklog.describeError(error));
            GleapBug.getInstance().addRequest(new Networklog(request.method(), request.url().toString(), startMillis,
                    elapsedMillis(startNanos), false, requestLog != null ? requestLog : new JSONObject(), responseLog));
        } catch (Throwable ignore) {
        }
    }

    private static JSONObject headersToJson(Headers headers) {
        JSONObject json = new JSONObject();
        // lower-case name -> first spelling, repeated headers are joined
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String lowerName = name.toLowerCase(Locale.ROOT);
            String value = GleapNetworkLogSanitizer.isCredentialHeader(name) ? GleapNetworkLogSanitizer.REDACTED : headers.value(i);
            try {
                String firstName = names.get(lowerName);
                if (firstName == null) {
                    names.put(lowerName, name);
                    json.put(name, value);
                } else if (!GleapNetworkLogSanitizer.REDACTED.equals(value)) {
                    json.put(firstName, json.optString(firstName) + ", " + value);
                }
            } catch (Exception ignore) {
            }
        }
        return json;
    }

    private static String contentTypeOf(Response response, ResponseBody body) {
        MediaType mediaType = body != null ? body.contentType() : null;
        return mediaType != null ? mediaType.toString() : response.header("Content-Type");
    }

    static int classify(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return KIND_UNKNOWN;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.contains("text/event-stream") || type.contains("ndjson") || type.contains("stream+json")
                || type.contains("multipart/x-mixed-replace") || type.contains("grpc")) {
            return KIND_STREAMING;
        }
        if (type.contains("json") || type.contains("xml") || type.contains("text/") || type.contains("javascript")
                || type.contains("x-www-form-urlencoded") || type.contains("graphql")) {
            return KIND_TEXT;
        }
        return KIND_BINARY;
    }

    private static boolean isEncoded(String contentEncoding) {
        return contentEncoding != null && !contentEncoding.trim().isEmpty() && !"identity".equalsIgnoreCase(contentEncoding.trim());
    }

    private static Charset charsetOf(MediaType mediaType) {
        try {
            if (mediaType != null) {
                Charset charset = mediaType.charset(UTF_8);
                if (charset != null) {
                    return charset;
                }
            }
        } catch (Throwable ignore) {
        }
        return UTF_8;
    }

    private static int elapsedMillis(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1000000L);
    }

    static String decode(byte[] bytes, int length, Charset charset, boolean truncated, String total) {
        int end = truncated && UTF_8.equals(charset) ? utf8SafeEnd(bytes, length) : length;
        String text = new String(bytes, 0, end, charset);
        return truncated ? text + Networklog.TRUNCATED_PREFIX + total + " bytes]" : text;
    }

    static boolean isProbablyUtf8(byte[] bytes, int length) {
        try {
            CharBuffer chars = UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, utf8SafeEnd(bytes, length)));
            int limit = Math.min(chars.length(), 1024);
            for (int i = 0; i < limit; i++) {
                char c = chars.charAt(i);
                if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                    return false;
                }
            }
            return true;
        } catch (CharacterCodingException notUtf8) {
            return false;
        }
    }

    // End of the last complete UTF-8 sequence, so a cut body does not end in a broken character.
    private static int utf8SafeEnd(byte[] bytes, int length) {
        for (int back = 1; back <= 3 && back <= length; back++) {
            int b = bytes[length - back] & 0xFF;
            if ((b & 0xC0) == 0x80) {
                continue;
            }
            int sequenceLength = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC0 ? 2 : 1;
            return sequenceLength > back ? length - back : length;
        }
        return length;
    }

    /**
     * The response body handed to the app: reads from the original body and copies the first
     * 150 KB of what the app reads into the capture.
     */
    private static final class CapturingResponseBody extends ResponseBody {
        private final ResponseBody delegate;
        private final BufferedSource source;

        CapturingResponseBody(ResponseBody delegate, BodyCapture capture) {
            this.delegate = delegate;
            this.source = Okio.buffer(new TeeSource(delegate.source(), delegate, capture));
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public BufferedSource source() {
            return source;
        }
    }

    private static final class TeeSource extends ForwardingSource {
        private final ResponseBody original;
        private final BodyCapture capture;

        TeeSource(Source delegate, ResponseBody original, BodyCapture capture) {
            super(delegate);
            this.original = original;
            this.capture = capture;
        }

        @Override
        public long read(Buffer sink, long byteCount) throws IOException {
            long read = super.read(sink, byteCount);
            try {
                if (read == -1) {
                    capture.onEnd(true);
                } else if (read > 0) {
                    capture.onRead(sink, sink.size() - read, read);
                }
            } catch (Throwable ignore) {
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    capture.onEnd(false);
                } catch (Throwable ignore) {
                }
                try {
                    original.close();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /**
     * The captured head of a response body. It is decoded when the log is sent, not while the app
     * reads, so reading stays as fast as without the interceptor.
     */
    private static final class BodyCapture implements Networklog.BodyText {
        private final Charset charset;
        private final boolean detectText;
        private final boolean gzip;
        private final long contentLength;
        private Buffer captured = new Buffer();
        private long totalBytes;
        private boolean overflow;
        private boolean finished;
        private boolean reachedEnd;
        private String text;

        BodyCapture(Charset charset, boolean detectText, boolean gzip, long contentLength) {
            this.charset = charset;
            this.detectText = detectText;
            this.gzip = gzip;
            this.contentLength = contentLength;
        }

        synchronized void onRead(Buffer sink, long offset, long byteCount) {
            if (finished) {
                return;
            }
            totalBytes += byteCount;
            long room = Networklog.BODY_CAP - captured.size();
            if (room > 0) {
                sink.copyTo(captured, offset, Math.min(room, byteCount));
            }
            if (byteCount > room) {
                overflow = true;
            }
        }

        synchronized void onEnd(boolean reachedEnd) {
            if (finished) {
                return;
            }
            finished = true;
            this.reachedEnd = reachedEnd;
        }

        @Override
        public synchronized String get() {
            if (!finished) {
                return Networklog.BODY_PENDING;
            }
            if (text == null) {
                try {
                    text = render();
                } catch (Throwable error) {
                    text = Networklog.BODY_NOT_CAPTURED;
                }
                captured = null;
            }
            return text;
        }

        private String render() throws IOException {
            if (captured.size() == 0) {
                return reachedEnd ? "" : Networklog.BODY_NOT_CAPTURED;
            }

            byte[] bytes;
            boolean truncated;
            String total;
            if (gzip) {
                // Added as a network interceptor: the body is still compressed.
                Buffer inflated = new Buffer();
                boolean complete = false;
                try {
                    GzipSource source = new GzipSource(captured);
                    while (inflated.size() <= Networklog.BODY_CAP) {
                        if (source.read(inflated, 8192) == -1) {
                            complete = true;
                            break;
                        }
                    }
                } catch (IOException incomplete) {
                    // The capture ends before the gzip stream does (or it is no gzip).
                }
                if (inflated.size() == 0) {
                    return Networklog.BINARY_BODY_OMITTED;
                }
                long size = inflated.size();
                bytes = inflated.readByteArray(Math.min(size, Networklog.BODY_CAP));
                truncated = !complete || size > Networklog.BODY_CAP;
                total = "more than " + bytes.length;
            } else {
                bytes = captured.readByteArray();
                if (overflow) {
                    truncated = true;
                    total = reachedEnd ? String.valueOf(totalBytes)
                            : contentLength >= 0 ? String.valueOf(contentLength) : "more than " + Networklog.BODY_CAP;
                } else if (!reachedEnd && contentLength > totalBytes) {
                    // The app closed the body before reading all of it.
                    truncated = true;
                    total = String.valueOf(contentLength);
                } else {
                    truncated = false;
                    total = null;
                }
            }

            if (detectText && !isProbablyUtf8(bytes, bytes.length)) {
                return Networklog.BINARY_BODY_OMITTED;
            }
            return decode(bytes, bytes.length, charset, truncated, total);
        }
    }
}
