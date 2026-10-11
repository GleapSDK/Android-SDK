package io.gleap;

import android.os.SystemClock;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import gleap.io.gleap.BuildConfig;

/**
 * The realtime socket, one per session lifetime (replaced on logout / {@link #destroy()}). Its
 * state is guarded by the instance; OkHttp's callbacks, the timers (main thread) and the app
 * lifecycle all go through the synchronized methods. Mirrors the iOS SDK:
 * <ul>
 *     <li>{@link #connect()} only asks for the channel. The socket opens 1.5 s after the last ask,
 *     so a session start followed right away by an identify opens one socket, and an ask for the
 *     channel that is already open (or opening) keeps that socket.</li>
 *     <li>The SDK closes its sockets with a close code: 1000 (normal) when it replaces one or
 *     shuts down, 1001 (going away) when the app stays in the background.</li>
 *     <li>After an error or a close by the server it reconnects with a growing delay
 *     ({@link GleapWebSocketPolicy}: 5 s up to 60 s, ±20 %), which starts over once a socket
 *     received a message or stayed open for 30 s.</li>
 *     <li>In the background (after a 10 s grace) and while offline there is no socket and no
 *     reconnect; returning to the foreground or getting back online connects again.</li>
 * </ul>
 */
public class GleapWebSocketListener extends WebSocketListener {
    static final long CONNECT_DEBOUNCE_MS = 1500;
    static final long BACKGROUND_GRACE_MS = 10000;
    static final int CLOSE_NORMAL = 1000;
    static final int CLOSE_GOING_AWAY = 1001;
    private static final long PING_INTERVAL_SECONDS = 40;

    // One client for every socket: its threads and connection pool are reused (and idle threads
    // end on their own), instead of a client per connect.
    private static OkHttpClient sharedClient;

    // The app's state, shared by every listener (set by GleapActivityTracker and
    // GleapConnectivityManager).
    private static volatile boolean appInBackground = false;
    private static volatile boolean online = true;

    // The socket that is open or opening, null when there is none, and its channel.
    private WebSocket webSocket;
    private String currentUrl;
    // The channel that is wanted (null before the first connect()).
    private String desiredUrl;
    // Written under the lock, read on OkHttp's threads.
    private volatile boolean isDestroyed = false;
    // Failed connections in a row, for the reconnect delay.
    private int failures;
    // When the current socket opened (elapsed realtime, -1 before onOpen) and whether it received a message.
    private long openedAt = -1;
    private boolean receivedMessage;
    // Bumped by every scheduled connect: only the latest one runs.
    private int connectGeneration;
    // Bumped when the app enters or leaves the background: only the latest grace close runs.
    private int backgroundGeneration;

    private static synchronized OkHttpClient client() {
        if (sharedClient == null) {
            sharedClient = new OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                    .build();
        }
        return sharedClient;
    }

    /**
     * Asks for the channel of the current session: the socket (re)connects 1.5 s after the last
     * ask, unless the open socket already serves it.
     *
     * @return false without a session
     */
    public boolean connect() {
        GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
        if (gleapSession == null) {
            return false;
        }

        String wsApiUrl = GleapConfig.getInstance().getWsApiUrl();
        String sdkKey = GleapConfig.getInstance().getSdkKey();
        String url = wsApiUrl + "?gleapId=" + gleapSession.getId() + "&gleapHash=" + gleapSession.getHash() + "&apiKey=" + sdkKey + "&sdkVersion=" + BuildConfig.VERSION_NAME
                + capsParameter();

        synchronized (this) {
            if (isDestroyed) {
                return false;
            }
            desiredUrl = url;
            scheduleConnect(CONNECT_DEBOUNCE_MS);
        }
        return true;
    }

    // What the SDK can capture for capture requests, e.g. "&caps=capture.screenshot,capture.logs".
    static String capsParameter() {
        String caps = GleapCapture.capsQueryValue(GleapCapture.currentCaps());
        return caps.isEmpty() ? "" : "&caps=" + caps;
    }

    // Tests replace the connection.
    WebSocket openWebSocket(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return client().newWebSocket(request, this);
    }

    // Tests replace the wait.
    void schedule(Runnable runnable, long delayMs) {
        // A delayed message on the main thread instead of a sleep on OkHttp's thread; the
        // connect itself is asynchronous.
        GleapMainThread.postDelayed(runnable, delayMs);
    }

    // Tests replace the clock and the jitter.
    long now() {
        return SystemClock.elapsedRealtime();
    }

    double random() {
        return Math.random();
    }

    private synchronized void scheduleConnect(long delayMs) {
        final int generation = ++connectGeneration;
        schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (GleapWebSocketListener.this) {
                    if (generation != connectGeneration || isDestroyed) {
                        return;
                    }
                    connectIfNeeded();
                }
            }
        }, delayMs);
    }

    private synchronized void connectIfNeeded() {
        GleapWebSocketPolicy.Action action = GleapWebSocketPolicy.action(desiredUrl, currentUrl,
                webSocket != null, appInBackground, online);
        if (action != GleapWebSocketPolicy.Action.CONNECT) {
            return;
        }

        close(CLOSE_NORMAL, "reconnect");

        currentUrl = desiredUrl;
        openedAt = -1;
        receivedMessage = false;
        webSocket = openWebSocket(currentUrl);
    }

    private synchronized void close(int code, String reason) {
        WebSocket socket = webSocket;
        // Cleared first: the closed socket's callbacks then see it was replaced and do nothing.
        webSocket = null;
        currentUrl = null;
        if (socket != null) {
            socket.close(code, reason);
        }
    }

    /**
     * Closes the connection for good (logout, stop): no reconnect follows.
     */
    public synchronized void destroy() {
        isDestroyed = true;
        connectGeneration++;
        backgroundGeneration++;
        close(CLOSE_NORMAL, "Goodbye");
    }

    private synchronized boolean isCurrent(WebSocket socket) {
        return !isDestroyed && socket != null && socket == webSocket;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        synchronized (this) {
            if (!isCurrent(webSocket)) {
                return;
            }
            openedAt = now();
        }

        // Connected: send the queued events now.
        GleapEventService.getInstance().start();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        synchronized (this) {
            if (!isCurrent(webSocket)) {
                return;
            }
            receivedMessage = true;
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
        webSocket.close(CLOSE_NORMAL, null);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        // A clean close by the server (a deploy, an idle timeout): connect again. A socket the SDK
        // closed itself (replaced, background, destroy) is no longer the current one.
        reconnectAfterFailure(webSocket);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        reconnectAfterFailure(webSocket);
    }

    private synchronized void reconnectAfterFailure(WebSocket socket) {
        if (!isCurrent(socket)) {
            return;
        }

        long openFor = openedAt < 0 ? -1 : now() - openedAt;
        if (GleapWebSocketPolicy.shouldResetBackoff(openFor, receivedMessage)) {
            failures = 0;
        }
        failures++;

        // The socket is gone already.
        this.webSocket = null;
        currentUrl = null;

        // In the background or offline the scheduled connect does nothing; the return to the
        // foreground or the network connects again.
        scheduleConnect(GleapWebSocketPolicy.reconnectDelay(failures, random()));
    }

    // App lifecycle and network.

    /**
     * The app went to the background (no activity started) or came back.
     */
    static void setAppInBackground(boolean inBackground) {
        if (appInBackground == inBackground) {
            return;
        }
        appInBackground = inBackground;
        GleapWebSocketListener listener = GleapEventService.currentWebSocketListener();
        if (listener == null) {
            return;
        }
        if (inBackground) {
            listener.onAppBackground();
        } else {
            listener.onAppForeground();
        }
    }

    static boolean isAppInBackground() {
        return appInBackground;
    }

    /**
     * The device has a network with internet access (or lost the last one).
     */
    static void setOnline(boolean isOnline) {
        boolean wasOnline = online;
        online = isOnline;
        GleapWebSocketListener listener = GleapEventService.currentWebSocketListener();
        if (listener != null && isOnline && !wasOnline) {
            listener.onBackOnline();
        }
    }

    // Tests only.
    static void resetAppStateForTesting() {
        appInBackground = false;
        online = true;
    }

    synchronized void onAppBackground() {
        if (isDestroyed) {
            return;
        }
        // No connect while in the background.
        connectGeneration++;

        final int generation = ++backgroundGeneration;
        if (webSocket == null) {
            return;
        }

        // Keep the socket for a short grace (the user often comes right back), then close it cleanly.
        schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (GleapWebSocketListener.this) {
                    if (generation != backgroundGeneration || !appInBackground || isDestroyed) {
                        return;
                    }
                    close(CLOSE_GOING_AWAY, "background");
                }
            }
        }, BACKGROUND_GRACE_MS);
    }

    synchronized void onAppForeground() {
        if (isDestroyed) {
            return;
        }
        // Cancels a pending grace close: a socket that is still open stays.
        backgroundGeneration++;
        if (desiredUrl != null) {
            scheduleConnect(CONNECT_DEBOUNCE_MS);
        }
    }

    synchronized void onBackOnline() {
        if (isDestroyed || desiredUrl == null || webSocket != null) {
            return;
        }
        // Failures while offline say nothing about the server.
        failures = 0;
        scheduleConnect(CONNECT_DEBOUNCE_MS);
    }
}
