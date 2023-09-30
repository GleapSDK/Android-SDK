package io.gleap;

import org.json.JSONException;
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
    private boolean isDestroyed = false;  // Flag to know when to stop the reconnection attempts.

    public boolean connect() {
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        UserSession userSession = UserSessionController.getInstance().getUserSession();
        if (userSession == null) {
            return false;
        }

        String wsApiUrl = GleapConfig.getInstance().getWsApiUrl();
        String sdkKey = GleapConfig.getInstance().getSdkKey();

        internallyConnect(wsApiUrl + "?gleapId=" + userSession.getId() + "&gleapHash=" + userSession.getHash() + "&apiKey=" + sdkKey + "&sdkVersion=" + BuildConfig.VERSION_NAME);

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
        System.out.println("MESSAGE: " + text);
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
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        System.out.println("MESSAGE: " + bytes.hex());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(1000, null);
        System.out.println("CLOSE: " + code + " " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        t.printStackTrace();
        if (!isDestroyed) {
            reconnect();
        }
    }

    // This function tries to reconnect to the server.
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