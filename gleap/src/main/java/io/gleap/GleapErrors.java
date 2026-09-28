package io.gleap;

import io.gleap.callbacks.ErrorCallback;

/**
 * Hands SDK errors to the app's {@link ErrorCallback}, if one is set. Without a callback the
 * error is dropped, so the SDK never crashes or spams the host app.
 */
final class GleapErrors {
    interface Action {
        void run() throws Exception;
    }

    private GleapErrors() {
    }

    /**
     * @param error   the error or exception that occurred
     * @param context where it occurred
     */
    static void report(Throwable error, String context) {
        try {
            ErrorCallback errorCallback = GleapCallbacks.getInstance().getErrorCallback();
            if (errorCallback != null) {
                errorCallback.onError(error, context);
            }
        } catch (Exception ignore) {
            // The error callback itself threw: ignore it rather than loop.
        }
    }

    /**
     * Runs the action; anything it throws (errors included) is reported with the context
     * instead of reaching the app.
     */
    static void guard(String context, Action action) {
        try {
            action.run();
        } catch (Error | Exception error) {
            report(error, context);
        }
    }
}
