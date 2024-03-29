package io.gleap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;


/**
 * Takes a screenshot of the current view
 */
class ScreenshotTaker {
    private final GleapBug gleapBug;
    public ScreenshotTaker() {
        gleapBug = GleapBug.getInstance();
    }
    private boolean alreadyTakingScreenshot = false;
    /**
     * Take a screenshot of the current view and opens it in the editor
     */
    public void takeScreenshot() {
       takeScreenshot(SurveyType.NONE);
    }

    protected void takeScreenshot(SurveyType type){
        if(GleapConfig.getInstance().getPlainConfig() != null) {
            try {
                if (!alreadyTakingScreenshot) {
                    GleapDetectorUtil.stopAllDetectors();

                    ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                        @Override
                        public void getImage(Bitmap bitmap) {
                            if (bitmap != null) {
                                openScreenshot(bitmap, type);
                                alreadyTakingScreenshot = false;
                            }
                        }
                    });
                }
            } catch (GleapSessionNotInitialisedException exception) {
                GleapDetectorUtil.resumeAllDetectors();
                System.err.println("Gleap: Gleap Session not initialized.");
                alreadyTakingScreenshot = false;
            } catch (InterruptedException e) {
                alreadyTakingScreenshot = false;
            } catch (ExecutionException e) {
                alreadyTakingScreenshot = false;
            }
        }
    }

    public void openScreenshot(Bitmap imageFile, SurveyType type) {
        try {
            GleapInvisibleActivityManger.getInstance().setInvisible();
            Activity activity = ActivityUtil.getCurrentActivity();
            if (activity != null) {
                Context applicationContext = activity.getApplicationContext();
                if (applicationContext != null) {
                    if (GleapBug.getInstance().getPhoneMeta() != null) {
                        GleapBug.getInstance().getPhoneMeta().setLastScreen(applicationContext.getClass().getSimpleName());
                    }
                    SharedPreferences pref = applicationContext.getSharedPreferences("prefs", 0);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString("descriptionEditText", ""); // Storing the description
                    editor.apply();
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
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

                    if (isSingleInstanceMode) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }

                    gleapBug.setScreenshot(imageFile);

                    GleapInvisibleActivityManger.getInstance().clearMessages();

                    if(GleapConfig.getInstance().getWidgetOpenedCallback() != null) {
                        GleapConfig.getInstance().getWidgetOpenedCallback().invoke();
                    }

                    Handler mainThreadHandler = new Handler(Looper.getMainLooper());
                    mainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            GleapInvisibleActivityManger.getInstance().setMessageCounter(0);
                        }
                    });

                    activity.startActivity(intent);
                }
            }
        } catch (Exception ex) {

        }
    }
}
