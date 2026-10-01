package io.gleap;

/**
 * Lets a wrapper SDK (React Native, Flutter, Capacitor) hand over the logs it buffers itself
 * before the native SDK collects the logs for a capture request (see
 * {@link Gleap#setLogFlushHandler(GleapLogFlushHandler)}).
 */
public interface GleapLogFlushHandler {
    /**
     * Called on the main thread right before the SDK collects the logs for a capture request.
     * Push the buffered logs to the SDK (e.g. {@link Gleap#attachConsoleLogs} and
     * {@link Gleap#attachNetworkLogs}), then call {@code done} from any thread. The SDK waits at
     * most 500 ms for {@code done} and collects the logs without waiting any longer; a later call
     * of {@code done} does nothing.
     *
     * @param done call it once the logs were handed over
     */
    void onFlushRequested(Runnable done);
}
