package io.gleap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;

/**
 * Request bodies in the OkHttp network logs: a long one is cut at 150 KB with the truncation
 * marker, like response bodies; one that writing would use up is never written.
 */
public class GleapOkHttpInterceptorTest {
    private static final MediaType JSON = MediaType.get("application/json");

    // The body each request reached the backend with.
    private final List<byte[]> sent = new ArrayList<>();

    @Before
    public void setUp() {
        GleapBug.resetForTesting();
    }

    @After
    public void tearDown() {
        Gleap.getInstance().setNetworkLogPropsToIgnore(null);
        GleapBug.resetForTesting();
    }

    @Test
    public void aLongRequestBodyIsCutWithTheMarkerAndIgnoredKeysAreMasked() throws Exception {
        Gleap.getInstance().setNetworkLogPropsToIgnore(new String[]{"token"});
        StringBuilder json = new StringBuilder("{\"token\":\"abc\",\"items\":\"");
        while (json.length() < 200 * 1024) {
            json.append("0123456789");
        }
        byte[] body = json.append("\"}").toString().getBytes(StandardCharsets.UTF_8);

        intercept(new Request.Builder().url("https://api.example.com/upload")
                .post(RequestBody.create(body, JSON)).build());

        assertArrayEquals("The backend gets the whole body", body, sent.get(0));
        String payload = payload("https://api.example.com/upload");
        String marker = Networklog.TRUNCATED_PREFIX + body.length + " bytes]";
        assertTrue(payload.startsWith("{\"token\":\"[REDACTED]\",\"items\":\"0123456789"));
        assertFalse(payload.contains("abc"));
        assertTrue(payload.endsWith(marker));
        // The masked value is longer: the head is cut again, the marker stays.
        assertEquals(Networklog.BODY_CAP + marker.length(), payload.length());
    }

    @Test
    public void oneShotAndUnknownLengthBodiesAreNotWritten() throws Exception {
        final int[] writes = {0};
        RequestBody oneShot = new CountingBody(writes, 10) {
            @Override
            public boolean isOneShot() {
                return true;
            }
        };
        RequestBody unknownLength = new CountingBody(writes, -1);

        intercept(new Request.Builder().url("https://api.example.com/one-shot").post(oneShot).build());
        intercept(new Request.Builder().url("https://api.example.com/stream").post(unknownLength).build());

        assertEquals("Only the backend wrote them", 2, writes[0]);
        assertEquals(Networklog.BODY_NOT_CAPTURED, payload("https://api.example.com/one-shot"));
        assertEquals(Networklog.BODY_NOT_CAPTURED, payload("https://api.example.com/stream"));
    }

    // ---------------------------------------------------------------------------------------------

    private void intercept(Request request) throws IOException {
        try (Response response = new GleapOkHttpInterceptor().intercept(new BackendChain(request))) {
            response.body().string();
        }
    }

    private static String payload(String url) throws Exception {
        JSONArray logs = GleapBug.getInstance().getNetworklogs();
        for (int i = 0; i < logs.length(); i++) {
            JSONObject entry = logs.getJSONObject(i);
            if (url.equals(entry.getString("url"))) {
                return entry.getJSONObject("request").getString("payload");
            }
        }
        throw new AssertionError("No network log for " + url + " in " + logs);
    }

    private static class CountingBody extends RequestBody {
        private final int[] writes;
        private final long length;

        CountingBody(int[] writes, long length) {
            this.writes = writes;
            this.length = length;
        }

        @Override
        public MediaType contentType() {
            return JSON;
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            writes[0]++;
            sink.writeUtf8("{\"a\":1234}");
        }
    }

    /**
     * The app's backend: writes the request body once, as OkHttp does, and answers {}.
     */
    private final class BackendChain implements Interceptor.Chain {
        private final Request request;

        BackendChain(Request request) {
            this.request = request;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response proceed(Request request) throws IOException {
            Buffer received = new Buffer();
            if (request.body() != null) {
                request.body().writeTo(received);
            }
            sent.add(received.readByteArray());
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create("{}", JSON))
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }
}
