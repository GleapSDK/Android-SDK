package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * Knows the activity on screen: follows the activity lifecycle from {@code initialize} on.
 * {@link ActivityUtil#getCurrentActivity()} falls back to looking the activity up through
 * hidden framework fields when nothing was tracked (e.g. the SDK was initialized while the
 * activity was already shown).
 */
final class GleapActivityTracker implements Application.ActivityLifecycleCallbacks {
    private static final GleapActivityTracker INSTANCE = new GleapActivityTracker();
    private static boolean registered = false;

    // The resumed activity; read from any thread.
    private volatile WeakReference<Activity> resumed = new WeakReference<>(null);

    private GleapActivityTracker() {
    }

    static synchronized void register(Application application) {
        if (registered || application == null) {
            return;
        }
        application.registerActivityLifecycleCallbacks(INSTANCE);
        registered = true;
    }

    /**
     * @return the resumed activity, or null when none was seen resumed
     */
    static Activity resumedActivity() {
        return INSTANCE.resumed.get();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumed = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (resumed.get() == activity) {
            resumed = new WeakReference<>(null);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (resumed.get() == activity) {
            resumed = new WeakReference<>(null);
        }
    }
}
