package io.gleap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * The capture request endpoints the SDK calls (shared routes: the SDK key and the session), and
 * the streaming upload of recordings. All calls block: run them on a background thread.
 */
final class GleapCaptureApi {
    static final String PATH_PREFIX = "/v3/shared/capture-requests/";
    static final String UPLOAD_PATH = "/uploads/attachments";
    // The server accepts log bundles up to 20 MB.
    static final int MAX_LOGS_BYTES = 20 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    /**
     * Upload progress, 0..1.
     */
    interface ProgressListener {
        void onProgress(float progress);
    }

    static final class Response {
        final int status;
        // The JSON answer, or null.
        final JSONObject json;

        Response(int status, JSONObject json) {
            this.status = status;
            this.json = json;
        }

        boolean ok() {
            return GleapHttp.isSuccess(status);
        }

        // The request is final (completed, skipped, cancelled or expired).
        boolean gone() {
            return status == 410;
        }
    }

    private static volatile OkHttpClient uploadClient;

    private GleapCaptureApi() {
    }

    /**
     * POST /claim: this device works on the request now.
     */
    static Response claim(String requestId) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("deviceId", GleapCapture.deviceId());
        body.put("platform", GleapCapture.PLATFORM);
        body.put("sdkType", GleapCapture.sdkType());
        body.put("sdkVersion", GleapCapture.sdkVersion());
        body.put("method", GleapCapture.METHOD_FRAMES);
        return post(requestPath(requestId, "claim"), body.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    /**
     * POST /event: declined, unsupported, failed or released (gives the claim back).
     */
    static Response event(String requestId, String type, String reason) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("type", type);
        if (reason != null) {
            body.put("reason", reason);
        }
        return post(requestPath(requestId, "event"), body.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    /**
     * POST /complete with the uploaded files.
     */
    static Response complete(String requestId, JSONObject body) throws IOException {
        return post(requestPath(requestId, "complete"), body.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    /**
     * POST /logs with a gzip compressed JSON bundle.
     */
    static Response postLogs(String requestId, byte[] gzippedJson) throws IOException {
        return post(requestPath(requestId, "logs"), gzippedJson, true);
    }

    static String requestPath(String requestId, String action) {
        if (!GleapCapture.isValidRequestId(requestId)) {
            throw new IllegalArgumentException("Invalid capture request id");
        }
        return PATH_PREFIX + requestId + "/" + action;
    }

    private static Response post(String path, byte[] body, boolean gzipped) throws IOException {
        GleapSession session = currentSession();
        HttpURLConnection conn = GleapHttp.open(GleapConfig.getInstance().getApiUrl() + path,
                gzipped ? GleapHttp.UPLOAD_READ_TIMEOUT_MS : GleapHttp.READ_TIMEOUT_MS);
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (gzipped) {
                conn.setRequestProperty("Content-Encoding", "gzip");
            }
            if (session != null) {
                conn.setRequestProperty("Gleap-Id", session.getId());
                conn.setRequestProperty("Gleap-Hash", session.getHash());
            }
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int status = conn.getResponseCode();
            return new Response(status, readJson(conn, status));
        } finally {
            conn.disconnect();
        }
    }

    private static GleapSession currentSession() {
        GleapSessionController controller = GleapSessionController.getInstance();
        return controller != null ? controller.getUserSession() : null;
    }

    private static JSONObject readJson(HttpURLConnection conn, int status) {
        InputStream stream = null;
        try {
            stream = GleapHttp.isSuccess(status) ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_RESPONSE_BYTES) {
                    return null;
                }
            }
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
            return text.startsWith("{") ? new JSONObject(text) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    /**
     * Uploads a file to /uploads/attachments (multipart field "file"), streamed from disk: the file
     * is never read into memory as a whole.
     *
     * @param callHolder receives the call, so it can be cancelled from another thread
     * @return the upload answer, {@code {"fileUrls": [...]}}
     */
    static JSONObject uploadFile(File file, String fileName, String mimeType, ProgressListener progress,
                                 AtomicReference<Call> callHolder) throws IOException {
        GleapSession session = currentSession();
        RequestBody fileBody = new FileBody(file, MediaType.parse(mimeType), progress);
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build();
        Request.Builder request = new Request.Builder()
                .url(GleapConfig.getInstance().getApiUrl() + UPLOAD_PATH)
                .header("api-token", GleapConfig.getInstance().getSdkKey())
                .header("Accept", "application/json")
                .post(body);
        if (session != null) {
            request.header("gleap-id", session.getId());
            request.header("gleap-hash", session.getHash());
        }

        OkHttpClient client = uploadClient();
        Call call = client.newCall(request.build());
        if (callHolder != null) {
            callHolder.set(call);
        }
        try (okhttp3.Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("The upload failed with HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            String text = responseBody != null ? responseBody.string() : "";
            try {
                return new JSONObject(text);
            } catch (JSONException e) {
                throw new IOException("Unexpected upload answer", e);
            }
        } finally {
            if (callHolder != null) {
                callHolder.compareAndSet(call, null);
            }
            // No idle connection (and cleanup thread) stays behind.
            try {
                client.connectionPool().evictAll();
            } catch (Throwable ignore) {
            }
        }
    }

    private static OkHttpClient uploadClient() {
        OkHttpClient client = uploadClient;
        if (client == null) {
            synchronized (GleapCaptureApi.class) {
                client = uploadClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(GleapHttp.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .writeTimeout(GleapHttp.UPLOAD_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .readTimeout(GleapHttp.UPLOAD_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .build();
                    uploadClient = client;
                }
            }
        }
        return client;
    }

    /**
     * A file as request body, written in chunks with progress.
     */
    private static final class FileBody extends RequestBody {
        private static final long CHUNK = 64 * 1024;
        private final File file;
        private final MediaType contentType;
        private final ProgressListener progress;

        FileBody(File file, MediaType contentType, ProgressListener progress) {
            this.file = file;
            this.contentType = contentType;
            this.progress = progress;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return file.length();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            long total = Math.max(1, file.length());
            long written = 0;
            float reported = -1;
            Buffer buffer = new Buffer();
            try (Source source = Okio.source(file)) {
                long read;
                while ((read = source.read(buffer, CHUNK)) != -1) {
                    sink.write(buffer, read);
                    written += read;
                    float value = Math.min(1f, written / (float) total);
                    if (progress != null && value - reported >= 0.01f) {
                        reported = value;
                        try {
                            progress.onProgress(value);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        }
    }
}
