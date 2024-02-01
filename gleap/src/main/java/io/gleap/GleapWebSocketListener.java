package io.gleap;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import gleap.io.gleap.BuildConfig;

public class GleapWebSocketListener extends WebSocketListener {
    private OkHttpClient client;
    private WebSocket webSocket;
    private String currentUrl;
    private boolean isDestroyed = false;

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
        if (client != null) {
            try {
                Thread.sleep(5000); // Sleep for 5 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (currentUrl != null) {
                internallyConnect(currentUrl);
            }
        }
    }
}