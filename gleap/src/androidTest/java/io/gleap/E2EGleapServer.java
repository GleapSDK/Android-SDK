package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * A fake Gleap backend running on the device (127.0.0.1): remote config, sessions, pings, the
 * WebSocket, uploads, tickets and a minimal widget page. Tickets are recorded.
 */
final class E2EGleapServer {
    static final String SDK_KEY = "e2e-fake-sdk-key";

    private final MockWebServer server = new MockWebServer();
    // The bodies of the tickets (POST /bugs/v2) received so far.
    private final List<JSONObject> tickets = new CopyOnWriteArrayList<>();
    final AtomicInteger webSocketOpens = new AtomicInteger();

    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger sessionCounter = new AtomicInteger();
    private final Map<String, String> sessionHashes = new ConcurrentHashMap<>();

    E2EGleapServer() {
        // MockWebServer logs every request at INFO level into logcat: keep the app's logcat (read
        // for the console logs of tickets) readable.
        Logger.getLogger(MockWebServer.class.getName()).setLevel(Level.WARNING);
    }

    void start() throws IOException {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                try {
                    return answer(request, sequence.incrementAndGet());
                } catch (Exception e) {
                    return new MockResponse().setResponseCode(500).setBody("{\"error\":\"fake server: " + e + "\"}");
                }
            }
        });
        server.start(InetAddress.getByName("127.0.0.1"), 0);
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getPort();
    }

    String wsUrl() {
        return "ws://127.0.0.1:" + server.getPort() + "/ws";
    }

    String frameUrl() {
        return baseUrl() + "/widget/appnew";
    }

    int port() {
        return server.getPort();
    }

    /**
     * The ticket whose form data has this description, or null.
     */
    JSONObject ticketWithDescription(String description) {
        for (JSONObject ticket : tickets) {
            JSONObject formData = ticket.optJSONObject("formData");
            if (formData != null && description.equals(formData.optString("description"))) {
                return ticket;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private MockResponse answer(RecordedRequest request, int number) throws Exception {
        String path = request.getPath() != null ? request.getPath() : "";
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        byte[] body = request.getBody().readByteArray();

        if (path.equals("/ws")) {
            return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocketOpens.incrementAndGet();
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    webSocket.close(1000, null);
                }
            });
        }
        if (path.startsWith("/config/")) {
            JSONObject config = new JSONObject()
                    .put("flowConfig", flowConfig())
                    .put("projectActions", new JSONObject()
                            .put("bugreporting", new JSONObject().put("title", "Report a bug")));
            return json(200, config);
        }
        if (path.equals("/sessions")) {
            return json(200, session(request.getHeader("Gleap-Id")));
        }
        if (path.startsWith("/sessions/")) {
            return json(200, new JSONObject());
        }
        if (path.equals("/uploads/sdk")) {
            return json(200, new JSONObject().put("fileUrl", baseUrl() + "/files/screenshot-" + number + ".png"));
        }
        if (path.equals("/uploads/sdksteps") || path.equals("/uploads/attachments")) {
            JSONArray urls = new JSONArray();
            int parts = countParts(body);
            for (int i = 0; i < parts; i++) {
                urls.put(baseUrl() + "/files/" + number + "-" + i);
            }
            return json(200, new JSONObject().put("fileUrls", urls));
        }
        if (path.equals("/bugs/v2")) {
            tickets.add(new JSONObject(new String(body, StandardCharsets.UTF_8)));
            return json(201, new JSONObject().put("shareToken", "e2e-share-" + number));
        }
        if (path.startsWith("/widget/")) {
            return new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(WIDGET_PAGE);
        }
        return new MockResponse().setResponseCode(404).setBody("{}");
    }

    /**
     * The flowConfig served with the remote config. {@code replaysInterval} is 0 on purpose: it used
     * to crash the SDK on start.
     */
    private JSONObject flowConfig() throws Exception {
        return new JSONObject()
                .put("enableConsoleLogs", true)
                .put("feedbackButtonPosition", "HIDDEN")
                .put("buttonLogo", baseUrl() + "/static/logo.png")
                .put("enableReplays", false)
                .put("replaysInterval", 0)
                .put("color", "#485bff")
                .put("headerColor", "#485bff")
                .put("backgroundColor", "#ffffff")
                .put("activationMethodShake", false)
                .put("activationMethodScreenshotGesture", false)
                .put("activationMethodFeedbackButton", false);
    }

    private JSONObject session(String gleapId) throws Exception {
        String id = gleapId;
        String hash = id != null ? sessionHashes.get(id) : null;
        if (hash == null) {
            int number = sessionCounter.incrementAndGet();
            id = "e2e-session-" + number;
            hash = "e2e-hash-" + number;
            sessionHashes.put(id, hash);
        }
        return new JSONObject().put("gleapId", id).put("gleapHash", hash);
    }

    private static int countParts(byte[] body) {
        String text = new String(body, StandardCharsets.ISO_8859_1);
        int count = 0;
        int index = 0;
        while ((index = text.indexOf("Content-Disposition: form-data", index)) >= 0) {
            count++;
            index++;
        }
        return count;
    }

    private static MockResponse json(int status, JSONObject body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body.toString());
    }

    /**
     * The widget page: does the handshake GleapMainActivity waits for (the "ping" command through
     * GleapJSBridge), and keeps every message the SDK sends in {@code window.__messages}.
     */
    private static final String WIDGET_PAGE = "<!doctype html><html><head>"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>Gleap e2e widget</title></head>"
            + "<body style=\"background:#ffffff\"><h1 id=\"title\">Gleap e2e widget</h1>"
            + "<script>"
            + "window.__messages = [];"
            + "function sendMessage(message) { window.__messages.push(message); }"
            + "function gleapBridge(command) { GleapJSBridge.gleapCallback(JSON.stringify(command)); }"
            + "function messageNames() { return window.__messages.map(function (m) { return m.name; }); }"
            + "gleapBridge({name: 'ping'});"
            + "window.__pinged = true;"
            + "</script></body></html>";
}
