package io.gleap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import java.lang.ref.WeakReference;


/**
 * Takes a screenshot of the current view
 */
class ScreenshotTaker {
    private final GleapBug gleapBug;
    public ScreenshotTaker() {
        gleapBug = GleapBug.getInstance();
    }

    /**
     * Take a screenshot of the current view and opens it in the editor
     */
    public void takeScreenshot() {
       takeScreenshot(SurveyType.NONE);
    }

    protected void takeScreenshot(SurveyType type) {
        if (GleapConfig.getInstance().getPlainConfig() != null) {
            GleapDetectorUtil.stopAllDetectors();

            ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                @Override
                public void getImage(Bitmap bitmap) {
                    // Without a screenshot (it could not be taken) the widget opens without one.
                    openScreenshot(bitmap, type);
                }
            });
        }
    }

    public void openScreenshot(Bitmap imageFile, SurveyType type) {
        boolean opened = false;
        // Opened again after a capture closed it: to the app it never closed.
        boolean afterCapture = false;
        try {
            GleapOverlayManager.getInstance().setInvisible();
            Activity activity = ActivityUtil.getCurrentActivity();
            if (activity != null) {
                Context applicationContext = activity.getApplicationContext();
                if (applicationContext != null) {
                    if (GleapBug.getInstance().getPhoneMeta() != null) {
                        // The screen the widget opens over.
                        GleapBug.getInstance().getPhoneMeta().setLastScreen(activity.getClass().getSimpleName());
                    }
                    Activity activityToOpen = ActivityUtil.getCurrentActivity();
                    if (activityToOpen == null) {
                        return;
                    }

                    boolean isSingleInstanceMode = false;
                    if (activityToOpen != null) {
                        try {
                            PackageManager pm = activityToOpen.getPackageManager();
                            ActivityInfo info = pm.getActivityInfo(activityToOpen.getComponentName(), 0);
                            if (info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                                isSingleInstanceMode = true;
                            }
                        } catch (Exception e) {}
                    }

                    // Set the caller activity.
                    GleapMainActivity.callerActivity = new WeakReference<>(activityToOpen);

                    Intent intent = new Intent(activityToOpen, GleapMainActivity.class);
                    intent.putExtra("IS_SURVEY", type == SurveyType.SURVEY);
                    intent.putExtra("IS_SURVEY_FULL", type == SurveyType.SURVEY_FULL);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

                    if (isSingleInstanceMode) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }

                    gleapBug.setScreenshot(imageFile);

                    GleapOverlayManager.getInstance().clearMessages();

                    afterCapture = GleapCaptureCoordinator.getInstance().takeWidgetClosedForCapture();
                    final boolean reopenedAfterCapture = afterCapture;
                    if(!afterCapture && GleapCallbacks.getInstance().getWidgetOpenedCallback() != null) {
                        GleapCallbacks.getInstance().getWidgetOpenedCallback().invoke();
                    }

                    GleapMainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            // After a capture only a real change reaches the app (a message that
                            // arrived meanwhile); the count was reset when the widget first opened.
                            if (!reopenedAfterCapture || GleapOverlayManager.getInstance().messageCounter != 0) {
                                GleapOverlayManager.getInstance().setMessageCounter(0);
                            }
                        }
                    });

                    activity.startActivity(intent);
                    opened = true;
                }
            }
        } catch (Exception ex) {
            GleapLog.w("Could not open the widget", ex);
        } finally {
            if (!opened) {
                // The widget did not open: resume the activation methods, which takeScreenshot
                // paused, and show the feedback button again.
                GleapDetectorUtil.resumeAllDetectors();
                GleapOverlayManager.getInstance().setVisible();
                if (afterCapture) {
                    // The app still thinks the widget is open from before the capture.
                    GleapCaptureCoordinator.getInstance().widgetNotReopened();
                }
            }
        }
    }
}
