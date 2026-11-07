package io.gleap;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks the app's {@link GleapUserJwtProvider} for a fresh identity token: one call at a time
 * (later requests wait for the running one), given up after 10 seconds, and after a failure
 * (null, not a JWT with a user id, an expired token, the token the server just refused, an
 * exception or the timeout) no new call for 30 seconds.
 * The provider stays set across {@code clearIdentity}.
 */
final class GleapUserJwtProviderGate {
    static final long TIMEOUT_MS = 10 * 1000;
    static final long PAUSE_AFTER_FAILURE_MS = 30 * 1000;

    interface TokenCallback {
        /**
         * @param token a fresh token, or null (no provider, failure, timeout or still pausing)
         */
        void onResult(String token);
    }

    interface Clock {
        long elapsedMillis();
    }

    interface Scheduler {
        void post(Runnable runnable);

        void postDelayed(Runnable runnable, long delayMs);
    }

    private static final Clock SYSTEM_CLOCK = new Clock() {
        @Override
        public long elapsedMillis() {
            return System.nanoTime() / 1000000;
        }
    };

    private static final Scheduler MAIN_THREAD = new Scheduler() {
        @Override
        public void post(Runnable runnable) {
            GleapMainThread.post(runnable);
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            GleapMainThread.postDelayed(runnable, delayMs);
        }
    };

    private static volatile GleapUserJwtProviderGate instance = new GleapUserJwtProviderGate(SYSTEM_CLOCK, MAIN_THREAD);

    private final Clock clock;
    private final Scheduler scheduler;
    private GleapUserJwtProvider provider;
    private List<TokenCallback> waiting;
    private int call;
    private boolean failed;
    private long failedAt;
    // The last token the server refused (memory only).
    private String refusedToken;

    GleapUserJwtProviderGate(Clock clock, Scheduler scheduler) {
        this.clock = clock;
        this.scheduler = scheduler;
    }

    static GleapUserJwtProviderGate getInstance() {
        return instance;
    }

    // Tests only.
    static void setInstanceForTesting(GleapUserJwtProviderGate gate) {
        instance = gate != null ? gate : new GleapUserJwtProviderGate(SYSTEM_CLOCK, MAIN_THREAD);
    }

    synchronized void setProvider(GleapUserJwtProvider provider) {
        this.provider = provider;
        // A new provider is asked right away, even after the old one failed.
        failed = false;
    }

    synchronized boolean hasProvider() {
        return provider != null;
    }

    synchronized boolean isInFlight() {
        return waiting != null;
    }

    /**
     * The server refused {@code token} (token_expired / token_invalid): the provider handing
     * back this exact token counts as a failure.
     */
    synchronized void rememberRefusedToken(String token) {
        if (GleapUserJwt.isUserJwt(token)) {
            refusedToken = token;
        }
    }

    /**
     * Asks the provider for a token; {@code callback} gets it, or null. Never throws.
     */
    void requestToken(TokenCallback callback) {
        final GleapUserJwtProvider current;
        final int thisCall;
        synchronized (this) {
            if (provider == null) {
                current = null;
                thisCall = -1;
            } else if (waiting != null) {
                waiting.add(callback);
                return;
            } else if (failed && clock.elapsedMillis() - failedAt < PAUSE_AFTER_FAILURE_MS) {
                current = null;
                thisCall = -1;
            } else {
                waiting = new ArrayList<>();
                waiting.add(callback);
                current = provider;
                thisCall = ++call;
            }
        }
        if (current == null) {
            callback.onResult(null);
            return;
        }

        scheduler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish(thisCall, null, "timed out after 10 s");
            }
        }, TIMEOUT_MS);
        scheduler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    current.fetchToken(new GleapUserJwtProvider.Callback() {
                        @Override
                        public void onToken(String token) {
                            finish(thisCall, token, token == null ? "it returned null" : null);
                        }
                    });
                } catch (Error | Exception error) {
                    finish(thisCall, null, "it threw " + error);
                }
            }
        });
    }

    private void finish(int thisCall, String token, String failure) {
        List<TokenCallback> callbacks;
        synchronized (this) {
            if (thisCall != call || waiting == null) {
                return;
            }
            callbacks = waiting;
            waiting = null;
            if (failure == null && GleapUserJwt.userIdOf(token) == null) {
                failure = "it did not return a JWT with a userId";
            }
            Long exp = failure == null ? GleapUserJwt.expirationOf(token) : null;
            if (exp != null && exp * 1000 <= System.currentTimeMillis()) {
                failure = "it returned an expired token";
            }
            if (failure == null && token != null && token.equals(refusedToken)) {
                failure = "it returned the token the server just refused";
            }
            failed = failure != null;
            failedAt = clock.elapsedMillis();
        }
        if (failure != null) {
            GleapLog.w("The JWT provider failed (" + failure + "). Asking again in 30 s at the earliest.");
            token = null;
        }
        for (TokenCallback callback : callbacks) {
            try {
                callback.onResult(token);
            } catch (Error | Exception error) {
                GleapLog.w("JWT provider result", error);
            }
        }
    }
}
