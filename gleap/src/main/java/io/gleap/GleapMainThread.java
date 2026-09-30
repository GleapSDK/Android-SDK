package io.gleap;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

/**
 * Runs work on the main thread. The public API can be called from any thread; everything that
 * touches UI or SDK state hops over here.
 */
final class GleapMainThread {
    private static volatile Handler handler;
    // Unit tests run main-thread work on this executor instead of the (stubbed) Looper.
    private static volatile Executor testExecutor;

    private GleapMainThread() {
    }

    static void post(Runnable runnable) {
        Executor executor = testExecutor;
        if (executor != null) {
            executor.execute(runnable);
            return;
        }
        handler().post(runnable);
    }

    static void postDelayed(Runnable runnable, long delayMillis) {
        Executor executor = testExecutor;
        if (executor != null) {
            executor.execute(runnable);
            return;
        }
        handler().postDelayed(runnable, delayMillis);
    }

    /**
     * Runs right away when called on the main thread, posts otherwise (like
     * {@link Activity#runOnUiThread(Runnable)}).
     */
    static void runOnUiThread(Runnable runnable) {
        Executor executor = testExecutor;
        if (executor != null) {
            executor.execute(runnable);
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            handler().post(runnable);
        }
    }

    /**
     * Posts to the main thread, but only while an activity is shown. Without one it throws a
     * NullPointerException: the callers report it through their error handling and do
     * nothing, as the SDK always did.
     */
    static void postWithActivity(Runnable runnable) {
        requireCurrentActivity();
        post(runnable);
    }

    /**
     * {@link #runOnUiThread(Runnable)}, but only while an activity is shown (see
     * {@link #postWithActivity(Runnable)}).
     */
    static void runWithActivity(Runnable runnable) {
        requireCurrentActivity();
        runOnUiThread(runnable);
    }

    private static Activity requireCurrentActivity() {
        Activity activity = ActivityUtil.getCurrentActivity();
        if (activity == null) {
            throw new NullPointerException("Gleap: no activity is shown");
        }
        return activity;
    }

    private static Handler handler() {
        Handler current = handler;
        if (current == null) {
            synchronized (GleapMainThread.class) {
                current = handler;
                if (current == null) {
                    current = new Handler(Looper.getMainLooper());
                    handler = current;
                }
            }
        }
        return current;
    }

    // Tests only.
    static void setTestExecutor(Executor executor) {
        testExecutor = executor;
    }
}
