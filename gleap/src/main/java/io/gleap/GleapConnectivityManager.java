package io.gleap;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors network connectivity and re-triggers Gleap initialization
 * when the device regains internet after starting offline.
 */
class GleapConnectivityManager {
    private static GleapConnectivityManager instance;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private long registeredAt = 0;

    // Ignore onAvailable calls within 10s of registration to avoid
    // colliding with the initial ConfigLoader/session load.
    private static final long INITIAL_SUPPRESSION_MS = 10_000;

    // Cooldown after a reconnect attempt before allowing another.
    private static final long RECONNECT_COOLDOWN_MS = 10_000;

    private GleapConnectivityManager() {}

    static synchronized GleapConnectivityManager getInstance() {
        if (instance == null) {
            instance = new GleapConnectivityManager();
        }
        return instance;
    }

    /**
     * Register a network callback that fires when internet becomes available.
     * Must be called with the application context to avoid activity leaks.
     */
    void register(Context appContext) {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }

            // Unregister any previous callback first.
            unregister(appContext);

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            registeredAt = System.currentTimeMillis();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    onNetworkAvailable();
                }
            };

            cm.registerNetworkCallback(request, networkCallback);
        } catch (Exception ignore) {
            // SecurityException if permission missing, or other system errors.
        }
    }

    /**
     * Unregister the network callback to prevent leaks.
     */
    void unregister(Context appContext) {
        try {
            if (networkCallback != null) {
                ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
                networkCallback = null;
            }
        } catch (Exception ignore) {
            // May throw if callback was not registered.
        }
    }

    private void onNetworkAvailable() {
        // Suppress during the initial load window to avoid colliding with
        // the GleapListener that runs in Gleap.initialize().
        if (System.currentTimeMillis() - registeredAt < INITIAL_SUPPRESSION_MS) {
            return;
        }

        // Debounce: only one reconnect attempt at a time.
        if (!isReconnecting.compareAndSet(false, true)) {
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean needsConfig = GleapConfig.getInstance().getPlainConfig() == null;
                    boolean needsSession = GleapSessionController.getInstance() == null
                            || GleapSessionController.getInstance().getUserSession() == null;

                    if (needsConfig) {
                        // Config was never loaded — re-run the full init sequence.
                        // GleapListener loads config AND creates a session.
                        GleapDetectorUtil.clearAllDetectors();
                        new Gleap.GleapListener();
                    } else if (needsSession) {
                        // Config loaded but no session — just create a session.
                        new GleapBaseSessionService().execute();
                    } else {
                        // Both loaded — connection was temporarily lost.
                        // Refresh the launcher UI and reconnect WebSocket.
                        GleapInvisibleActivityManger.getInstance().addLayoutToActivity(null);
                        GleapEventService.getInstance().startWebSocketListener();
                    }
                } catch (Exception ignore) {
                }

                // Reset the reconnecting flag after cooldown so subsequent
                // connectivity changes can trigger another attempt.
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isReconnecting.set(false);
                    }
                }, RECONNECT_COOLDOWN_MS);
            }
        });
    }
}
