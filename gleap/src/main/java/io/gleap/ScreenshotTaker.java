package io.gleap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

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
        try {
            if(!alreadyTakingScreenshot) {
                alreadyTakingScreenshot = true;
                GleapDetectorUtil.stopAllDetectors();
                if (GleapConfig.getInstance().getBugWillBeSentCallback() != null) {
                    GleapConfig.getInstance().getBugWillBeSentCallback().flowInvoced();
                }
                ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                    @Override
                    public void getImage(Bitmap bitmap) {
                        if (bitmap != null) {
                            openScreenshot(bitmap);
                            alreadyTakingScreenshot = false;
                        }
                    }
                });
            }
        }catch (GleapSessionNotInitialisedException exception) {
            GleapDetectorUtil.resumeAllDetectors();
            System.err.println("Gleap: Gleap Session not initialized.");
            alreadyTakingScreenshot = false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            alreadyTakingScreenshot = false;
        } catch (ExecutionException e) {
            e.printStackTrace();
            alreadyTakingScreenshot = false;
        }
    }

    public void  openScreenshot(Bitmap imageFile) {
        try {
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
                    Intent intent = new Intent(ActivityUtil.getCurrentActivity(), GleapMainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    gleapBug.setScreenshot(imageFile);
                    activity.startActivity(intent);
                }
            }
        } catch (Exception ex) {

        }
    }
}
