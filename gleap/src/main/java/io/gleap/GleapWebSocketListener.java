package io.gleap;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import gleap.io.gleap.BuildConfig;

public class GleapWebSocketListener extends WebSocketListener {
    // After a failure or a close by the server the connection is tried again after 5 s, then 10,
    // 20, 40 and at most 60 s apart, each ±20 % so devices do not reconnect in step; a successful
    // connect starts over at 5 s.
    private static final long FIRST_RECONNECT_DELAY_MS = 5000;
    private static final long MAX_RECONNECT_DELAY_MS = 60000;

    private OkHttpClient client;
    private WebSocket webSocket;
    private String currentUrl;
    // Written on the main thread, read on OkHttp's threads.
    private volatile boolean isDestroyed = false;
    // The socket is open (onOpen until it closes or fails).
    private volatile boolean connected = false;
    private final AtomicInteger failedAttempts = new AtomicInteger();

    public boolean connect() {
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(40, TimeUnit.SECONDS)
                .build();

        GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
        if (gleapSession == null) {
            return false;
        }

        String wsApiUrl = GleapConfig.getInstance().getWsApiUrl();
        String sdkKey = GleapConfig.getInstance().getSdkKey();

        internallyConnect(wsApiUrl + "?gleapId=" + gleapSession.getId() + "&gleapHash=" + gleapSession.getHash() + "&apiKey=" + sdkKey + "&sdkVersion=" + BuildConfig.VERSION_NAME
                + capsParameter());

        return true;
    }

    /**
     * Whether the socket is open right now (the pings tell the server, which then pushes on the
     * socket or answers the ping instead).
     */
    boolean isConnected() {
        return connected && !isDestroyed;
    }

    // What the SDK can capture for capture requests, e.g. "&caps=capture.screenshot,capture.logs".
    static String capsParameter() {
        String caps = GleapCapture.capsQueryValue(GleapCapture.currentCaps());
        return caps.isEmpty() ? "" : "&caps=" + caps;
    }

    private void internallyConnect(String url) {
        currentUrl = url;
        webSocket = openWebSocket(url);
    }

    // Tests replace the connection.
    WebSocket openWebSocket(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return client.newWebSocket(request, this);
    }

    // Tests replace the wait.
    void scheduleReconnect(Runnable reconnect, long delayMs) {
        // A delayed message on the main thread instead of a sleep on OkHttp's thread; the
        // connect itself is asynchronous.
        GleapMainThread.postDelayed(reconnect, delayMs);
    }

    /**
     * Closes the connection for good (logout, stop, a new session): no reconnect follows.
     */
    public void destroy() {
        isDestroyed = true;
        connected = false;
        if (webSocket != null) {
            webSocket.close(1000, "Goodbye");
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        if (isDestroyed) {
            return;
        }

        failedAttempts.set(0);
        connected = true;

        // Start event sending.
        GleapEventService.getInstance().start();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (isDestroyed) {
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(text);
            String eventName = jsonObject.getString("name");

            if (eventName != null && eventName.equalsIgnoreCase("update")) {
                JSONObject data = jsonObject.getJSONObject("data");
                GleapEventService.getInstance().processEventData(data);
            } else if ("capture-request".equals(eventName)) {
                // The server asks for the app's logs; handled whether the widget is open or not.
                GleapCaptureLogs.getInstance().onCaptureRequest(jsonObject.optJSONObject("data"));
            }
        } catch (Exception exp) {}
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {}

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        // The server closes: answer it, onClosed (or onFailure) follows.
        connected = false;
        webSocket.close(1000, null);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        connected = false;
        // A clean close by the server (a deploy, an idle timeout): connect again, unless the SDK
        // closed it itself (destroy).
        if (!isDestroyed) {
            reconnect();
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        connected = false;
        if (!isDestroyed) {
            reconnect();
        }
    }

    private void reconnect() {
        final String url = currentUrl;
        if (url == null) {
            return;
        }

        scheduleReconnect(new Runnable() {
            @Override
            public void run() {
                if (!isDestroyed) {
                    internallyConnect(url);
                }
            }
        }, reconnectDelay(failedAttempts.incrementAndGet(), Math.random()));
    }

    /**
     * The wait before reconnect attempt {@code failedAttempts}: 5 s doubled per attempt up to
     * 60 s, times a random factor between 0.8 and 1.2, never more than 60 s.
     *
     * @param random uniformly distributed in [0, 1)
     */
    static long reconnectDelay(int failedAttempts, double random) {
        long base = Math.min(FIRST_RECONNECT_DELAY_MS << Math.max(0, Math.min(failedAttempts - 1, 4)), MAX_RECONNECT_DELAY_MS);
        double factor = 1 - GleapPingBackoff.JITTER + 2 * GleapPingBackoff.JITTER * Math.min(Math.max(random, 0), 1);
        return Math.min(Math.round(base * factor), MAX_RECONNECT_DELAY_MS);
    }
}
