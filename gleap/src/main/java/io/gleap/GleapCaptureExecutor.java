package io.gleap;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs the capture work that blocks (log bundles, uploads, capture request calls) on up to two
 * threads of its own: a long recording upload does not hold back the SDK's other requests or a
 * log bundle. The threads only exist while there is work (they end after 30 s without).
 */
final class GleapCaptureExecutor {
    private static final Executor DEFAULT = create();
    // Tests only: runs the work right away.
    private static volatile Executor testExecutor;

    private GleapCaptureExecutor() {
    }

    static void execute(Runnable work) {
        Executor executor = testExecutor;
        (executor != null ? executor : DEFAULT).execute(work);
    }

    // Tests only; null restores the threads.
    static void setExecutorForTesting(Executor executor) {
        testExecutor = executor;
    }

    private static Executor create() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "gleap-capture");
                thread.setDaemon(true);
                return thread;
            }
        });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
