package io.gleap;

/**
 * Fetches a fresh identity token (JWT) from your backend for
 * {@link Gleap#setUserJwtProvider(GleapUserJwtProvider)}.
 */
public interface GleapUserJwtProvider {
    /**
     * Called on the main thread when the current identity token expired or expires within
     * 5 minutes. Fetch a new token and pass it to {@code callback.onToken(token)}, or
     * {@code null} when that failed. Without an answer within 10 seconds the call counts as
     * failed.
     *
     * @param callback receives the new token
     */
    void fetchToken(Callback callback);

    /**
     * Receives the token of {@link #fetchToken(Callback)}. Can be called from any thread.
     */
    interface Callback {
        /**
         * @param token the new identity token, or null when it could not be fetched
         */
        void onToken(String token);
    }
}
