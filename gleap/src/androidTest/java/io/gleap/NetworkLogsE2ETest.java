package io.gleap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

/**
 * Network logs recorded by the built-in {@link GleapOkHttpInterceptor}: a real OkHttp client talks
 * to the app's (fake) backend on the device, and the assertions check what the SDK sends to the
 * fake Gleap server inside a ticket.
 */
@RunWith(AndroidJUnit4.class)
public class NetworkLogsE2ETest extends E2ETestBase {
    private static final String TRUNCATED = "\n… [truncated, ";

    private MockWebServer appBackend;
    private OkHttpClient client;

    @Before
    public void setUp() throws Exception {
        appBackend = new MockWebServer();
        appBackend.start(InetAddress.getByName("127.0.0.1"), 0);
        client = new OkHttpClient.Builder()
                .addInterceptor(new GleapOkHttpInterceptor())
                .build();
    }

    @After
    public void tearDown() throws Exception {
        appBackend.shutdown();
    }

    @Test
    public void theAppReadsTheWhole1MbBodyWhileTheLogKeeps150KbWithTheTruncationMarker() throws Exception {
        byte[] body = randomText(1024 * 1024).getBytes(StandardCharsets.UTF_8);
        appBackend.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(new Buffer().write(body)));
        byte[] bigPayload = randomText(200 * 1024).getBytes(StandardCharsets.UTF_8);
        appBackend.enqueue(new MockResponse().setBody("{}"));

        byte[] read;
        try (Response response = client.newCall(new Request.Builder().url(url("/big")).build()).execute()) {
            read = response.body().bytes();
        }
        try (Response response = client.newCall(new Request.Builder().url(url("/upload"))
                .post(RequestBody.create(bigPayload, MediaType.get("application/json"))).build()).execute()) {
            response.body().string();
        }

        assertEquals(body.length, read.length);
        assertArrayEquals("The app gets the whole body", sha256(body), sha256(read));
        appBackend.takeRequest();
        RecordedRequest upload = appBackend.takeRequest();
        assertArrayEquals("The request reaches the backend unchanged", sha256(bigPayload), sha256(upload.getBody().readByteArray()));

        JSONArray logs = networkLogsOfNewTicket("big-body");
        JSONObject big = entry(logs, "/big");
        String responseText = big.getJSONObject("response").getString("responseText");
        String expectedMarker = TRUNCATED + body.length + " bytes]";
        assertTrue("Ends with the marker: …" + tail(responseText), responseText.endsWith(expectedMarker));
        assertEquals(new String(body, 0, Networklog.BODY_CAP, StandardCharsets.UTF_8),
                responseText.substring(0, responseText.length() - expectedMarker.length()));
        assertEquals(200, big.getJSONObject("response").getInt("status"));
        assertTrue(big.getBoolean("success"));
        assertEquals("GET", big.getString("type"));

        // A request body above the cap is not captured (it is not cut either).
        JSONObject uploadEntry = entry(logs, "/upload");
        assertEquals(Networklog.BODY_NOT_CAPTURED, uploadEntry.getJSONObject("request").getString("payload"));
        assertEquals("POST", uploadEntry.getString("type"));
    }

    @Test
    public void binaryAndStreamingBodiesAreReplacedByMarkersButReachTheAppAndTheBackend() throws Exception {
        byte[] png = randomBytes(20000);
        appBackend.enqueue(new MockResponse().setHeader("Content-Type", "image/png").setBody(new Buffer().write(png)));
        appBackend.enqueue(new MockResponse().setBody("ok"));
        String events = "data: one\n\ndata: two\n\n";
        appBackend.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setChunkedBody(events, 5));

        byte[] readPng;
        try (Response response = client.newCall(new Request.Builder().url(url("/image.png")).build()).execute()) {
            readPng = response.body().bytes();
        }
        byte[] payload = randomBytes(3000);
        try (Response response = client.newCall(new Request.Builder().url(url("/binary-upload"))
                .post(RequestBody.create(payload, MediaType.get("application/octet-stream"))).build()).execute()) {
            response.body().string();
        }
        String readEvents;
        try (Response response = client.newCall(new Request.Builder().url(url("/events")).build()).execute()) {
            readEvents = response.body().string();
        }

        assertArrayEquals(sha256(png), sha256(readPng));
        appBackend.takeRequest();
        assertArrayEquals(sha256(payload), sha256(appBackend.takeRequest().getBody().readByteArray()));
        assertEquals(events, readEvents);

        JSONArray logs = networkLogsOfNewTicket("binary-and-streaming");
        assertEquals("[binary body omitted]", entry(logs, "/image.png").getJSONObject("response").getString("responseText"));
        assertEquals("[binary body omitted]", entry(logs, "/binary-upload").getJSONObject("request").getString("payload"));
        assertEquals("[streaming body omitted]", entry(logs, "/events").getJSONObject("response").getString("responseText"));
    }

    @Test
    public void failedRequestsAreRethrownToTheAppAndLoggedAsFailedWithTheirError() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = socket.getLocalPort();
        }
        try (Response ignored = client.newCall(new Request.Builder()
                .url("http://127.0.0.1:" + closedPort + "/refused").build()).execute()) {
            fail("The refused connection must reach the app");
        } catch (ConnectException expected) {
            // Rethrown as is.
        }

        appBackend.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        OkHttpClient impatient = client.newBuilder().readTimeout(1, TimeUnit.SECONDS).build();
        try (Response ignored = impatient.newCall(new Request.Builder().url(url("/timeout")).build()).execute()) {
            fail("The timeout must reach the app");
        } catch (SocketTimeoutException expected) {
            // Rethrown as is.
        }

        JSONArray logs = networkLogsOfNewTicket("failures");
        JSONObject refused = entry(logs, "/refused");
        assertFalse(refused.getBoolean("success"));
        assertTrue(refused.getJSONObject("response").getString("errorText"),
                refused.getJSONObject("response").getString("errorText").startsWith("ConnectException"));
        assertFalse(refused.getJSONObject("response").has("status"));

        JSONObject timeout = entry(logs, "/timeout");
        assertFalse(timeout.getBoolean("success"));
        assertTrue(timeout.getJSONObject("response").getString("errorText"),
                timeout.getJSONObject("response").getString("errorText").startsWith("SocketTimeoutException"));
    }

    @Test
    public void ignoredKeysAreRemovedCredentialHeadersRedactedAndBlacklistedUrlsLeftOut() throws Exception {
        Gleap.getInstance().setNetworkLogPropsToIgnore(new String[]{"user.password", "token", "X-Api-Secret", "apiKey"});
        appBackend.enqueue(new MockResponse().setBody("{}"));
        // Recorded before the blacklist is set: still left out when the ticket is sent.
        call("/private/before");
        Gleap.getInstance().setNetworkLogsBlacklist(new String[]{"/private/"});

        String requestJson = "{\"user\":{\"name\":\"Ann\",\"password\":\"hunter2\"},\"password\":\"top-level-stays\","
                + "\"session\":{\"token\":\"tok-1\",\"nested\":[{\"TOKEN\":\"tok-2\",\"keep\":1}]}}";
        appBackend.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .addHeader("Set-Cookie", "sid=server-secret")
                .addHeader("x-api-secret", "response-secret")
                .setBody("{\"token\":\"resp-token\",\"user\":{\"password\":\"resp-pw\",\"name\":\"Ann\"},\"ok\":true}"));
        try (Response response = client.newCall(new Request.Builder()
                .url(url("/login?ApiKey=key-123&page=2"))
                .header("Authorization", "Bearer secret-bearer")
                .header("Cookie", "sid=client-secret")
                .header("X-Api-Secret", "request-secret")
                .header("X-Trace", "trace-1")
                .post(RequestBody.create(requestJson, MediaType.get("application/json")))
                .build()).execute()) {
            response.body().string();
        }

        appBackend.enqueue(new MockResponse().setBody("{}"));
        call("/private/after");

        // The backend got everything: the rules only apply to the logs.
        appBackend.takeRequest();
        RecordedRequest login = appBackend.takeRequest();
        assertEquals("Bearer secret-bearer", login.getHeader("Authorization"));
        assertTrue(login.getPath().contains("ApiKey=key-123"));

        JSONArray logs = networkLogsOfNewTicket("props-to-ignore");
        String logText = logs.toString();
        for (String secret : new String[]{"hunter2", "tok-1", "tok-2", "resp-token", "resp-pw", "secret-bearer",
                "client-secret", "server-secret", "request-secret", "response-secret", "key-123"}) {
            assertFalse("\"" + secret + "\" must not be sent: " + logText, logText.contains(secret));
        }

        JSONObject loginEntry = entry(logs, "/login");
        assertEquals(url("/login?page=2"), loginEntry.getString("url"));
        JSONObject requestHeaders = loginEntry.getJSONObject("request").getJSONObject("headers");
        assertEquals("[REDACTED]", requestHeaders.getString("Authorization"));
        assertEquals("[REDACTED]", requestHeaders.getString("Cookie"));
        assertFalse(requestHeaders.has("X-Api-Secret"));
        assertEquals("trace-1", requestHeaders.getString("X-Trace"));

        JSONObject payload = new JSONObject(loginEntry.getJSONObject("request").getString("payload"));
        assertEquals("Ann", payload.getJSONObject("user").getString("name"));
        assertFalse("user.password is removed", payload.getJSONObject("user").has("password"));
        assertEquals("Only the path user.password is removed", "top-level-stays", payload.getString("password"));
        assertFalse(payload.getJSONObject("session").has("token"));
        assertFalse(payload.getJSONObject("session").getJSONArray("nested").getJSONObject(0).has("TOKEN"));
        assertEquals(1, payload.getJSONObject("session").getJSONArray("nested").getJSONObject(0).getInt("keep"));

        JSONObject responseHeaders = loginEntry.getJSONObject("response").getJSONObject("headers");
        assertEquals("[REDACTED]", responseHeaders.getString("Set-Cookie"));
        assertFalse(responseHeaders.has("x-api-secret"));
        JSONObject responseBody = new JSONObject(loginEntry.getJSONObject("response").getString("responseText"));
        assertFalse(responseBody.has("token"));
        assertFalse(responseBody.getJSONObject("user").has("password"));
        assertTrue(responseBody.getBoolean("ok"));

        assertFalse(logText, logText.contains("/private/"));
    }

    // ---------------------------------------------------------------------------------------------

    private void call(String path) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url(path)).build()).execute()) {
            response.body().string();
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + appBackend.getPort() + path;
    }

    private JSONArray networkLogsOfNewTicket(String name) throws Exception {
        JSONObject ticket = E2EEnvironment.sendSilentReport(E2EEnvironment.unique("network-logs-" + name));
        return ticket.getJSONArray("networkLogs");
    }

    private JSONObject entry(JSONArray logs, String path) throws Exception {
        String prefix = url(path);
        JSONObject found = null;
        for (JSONObject entry : E2EEnvironment.objects(logs)) {
            String entryUrl = entry.optString("url");
            if (entryUrl.equals(prefix) || entryUrl.startsWith(prefix + "?")) {
                assertNull("Logged once: " + path, found);
                found = entry;
            }
        }
        if (found == null) {
            // The refused request goes to another port.
            for (JSONObject entry : E2EEnvironment.objects(logs)) {
                if (entry.optString("url").endsWith(path)) {
                    found = entry;
                }
            }
        }
        assertNotNull("No network log for " + path + " in " + abbreviate(logs.toString()), found);
        return found;
    }

    private static String tail(String text) {
        return text.length() > 80 ? text.substring(text.length() - 80) : text;
    }

    private static String abbreviate(String text) {
        return text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        // Make sure it is no UTF-8 text.
        bytes[0] = (byte) 0xC3;
        bytes[1] = (byte) 0x28;
        return bytes;
    }

    private static String randomText(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ,.:;-_";
        Random random = new Random(length);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }
}
