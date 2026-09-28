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
    // After a failure the connection is tried again after 5 s, then 10, 20, 40 and at most 60 s
    // apart; a successful connect starts over at 5 s.
    private static final long FIRST_RECONNECT_DELAY_MS = 5000;
    private static final long MAX_RECONNECT_DELAY_MS = 60000;

    private OkHttpClient client;
    private WebSocket webSocket;
    private String currentUrl;
    // Written on the main thread, read on OkHttp's threads.
    private volatile boolean isDestroyed = false;
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

        internallyConnect(wsApiUrl + "?gleapId=" + gleapSession.getId() + "&gleapHash=" + gleapSession.getHash() + "&apiKey=" + sdkKey + "&sdkVersion=" + BuildConfig.VERSION_NAME);

        return true;
    }

    private void internallyConnect(String url) {
        currentUrl = url;

        Request request = new Request.Builder()
                .url(url)
                .build();
        webSocket = client.newWebSocket(request, this);
    }

    public void destroy() {
        isDestroyed = true;
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
            }
        } catch (Exception exp) {}
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {}

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(1000, null);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        if (!isDestroyed) {
            reconnect();
        }
    }

    private void reconnect() {
        final String url = currentUrl;
        if (client == null || url == null) {
            return;
        }

        // The wait is a delayed message on the main thread instead of a sleep on OkHttp's
        // thread; the connect itself is asynchronous.
        GleapMainThread.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isDestroyed) {
                    internallyConnect(url);
                }
            }
        }, reconnectDelay(failedAttempts.incrementAndGet()));
    }

    static long reconnectDelay(int failedAttempts) {
        long delay = FIRST_RECONNECT_DELAY_MS << Math.max(0, Math.min(failedAttempts - 1, 4));
        return Math.min(delay, MAX_RECONNECT_DELAY_MS);
    }
}