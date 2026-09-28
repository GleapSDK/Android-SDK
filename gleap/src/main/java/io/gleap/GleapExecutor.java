package io.gleap;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs the SDK's background requests (config, session, identify, events, tickets) one after
 * another in the order they were started, like {@code AsyncTask.execute()}, but on Gleap's own
 * thread instead of the app-wide AsyncTask queue: the host app's tasks no longer wait for
 * Gleap's requests (and their retries), and Gleap's requests no longer wait for the app's.
 */
final class GleapExecutor {
    static final Executor SERIAL = createSerialExecutor();

    private GleapExecutor() {
    }

    private static Executor createSerialExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "gleap-requests");
                thread.setDaemon(true);
                return thread;
            }
        });
        // The thread ends after 30 s without work and starts again for the next request.
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
