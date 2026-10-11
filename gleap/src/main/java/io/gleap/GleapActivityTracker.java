package io.gleap;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * Knows the activity on screen: follows the activity lifecycle from {@code initialize} on.
 * {@link ActivityUtil#getCurrentActivity()} falls back to looking the activity up through
 * hidden framework fields when nothing was tracked (e.g. the SDK was initialized while the
 * activity was already shown).
 * <p>
 * Also knows whether the app is in the background (no started activity, none resumed) for the
 * realtime socket ({@link GleapWebSocketListener}). A process started without an activity (a
 * push, a job) counts as in the background until an activity starts.
 */
final class GleapActivityTracker implements Application.ActivityLifecycleCallbacks {
    private static final GleapActivityTracker INSTANCE = new GleapActivityTracker();
    private static boolean registered = false;

    // The resumed activity; read from any thread.
    private volatile WeakReference<Activity> resumed = new WeakReference<>(null);
    // Started activities seen since register (main thread). Activities started before the SDK
    // was initialized are not counted: the count never goes below 0 and a resumed activity
    // keeps the app in the foreground.
    private int started;

    private GleapActivityTracker() {
    }

    static synchronized void register(Application application) {
        if (registered || application == null) {
            return;
        }
        application.registerActivityLifecycleCallbacks(INSTANCE);
        registered = true;
        // Initialized while an activity is shown (e.g. from React Native or Flutter) or by a
        // process started for an activity: in the foreground. Started for a push or a job: in
        // the background until an activity starts.
        GleapWebSocketListener.setAppInBackground(!startedInForeground());
    }

    private static boolean startedInForeground() {
        try {
            if (ActivityUtil.getCurrentActivity() != null) {
                return true;
            }
            ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(info);
            return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    || info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
        } catch (Exception | Error ignore) {
            return true;
        }
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
        // Also an activity that started before the SDK was initialized.
        GleapWebSocketListener.setAppInBackground(false);
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
        started++;
        GleapWebSocketListener.setAppInBackground(false);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (started > 0) {
            started--;
        }
        // A rotation stops the activity and starts its new instance right away.
        if (started == 0 && resumed.get() == null && !activity.isChangingConfigurations()) {
            GleapWebSocketListener.setAppInBackground(true);
        }
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
