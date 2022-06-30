package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import io.gleap.FeedbackSentCallback;

class SilentBugReportUtil {
    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity, String type, JSONObject excludeData) {
        JSONObject obj = new JSONObject();
        excludeData = new JSONObject();
        try {
            excludeData.put("screenshot", true);
            excludeData.put("replay", true);
        } catch (Exception ex) {
        }  excludeData = new JSONObject();
        try {
            excludeData.put("screenshot", true);
            excludeData.put("replay", true);
        } catch (Exception ex) {
        }
        try {
            obj.put("description", description);
        } catch (JSONException e) {
        }
        GleapConfig.getInstance().setCrashStripModel(excludeData);
        GleapBug model = GleapBug.getInstance();
        model.setType(type);
        model.setData(obj);
        if (severity != null) {
            model.setSeverity(severity.name());
        } else {
            model.setSeverity(Gleap.SEVERITY.HIGH.name());
        }
        model.setSilent(true);
        new HttpHelper(new SilentBugReportHTTPListener(), context).execute(model);
       /* if (excludeData == null || excludeData.length() == 0) {
            excludeData = new JSONObject();
            try {
                excludeData.put("screenshot", true);
                excludeData.put("replay", true);
            } catch (Exception ex) {
            }
        }
        GleapConfig.getInstance().setCrashStripModel(excludeData);
        try {
            GleapBug model = GleapBug.getInstance();
            ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                @Override
                public void getImage(Bitmap bitmap) {
                    System.out.println("IMAGE TAKEN");
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("description", description);
                    } catch (JSONException e) {
                    }
                    model.setType(type);
                    model.setData(obj);
                    if (severity != null) {
                        model.setSeverity(severity.name());
                    } else {
                        model.setSeverity(Gleap.SEVERITY.LOW.name());
                    }
                    model.setSilent(true);


                    if (bitmap != null) {
                        model.setScreenshot(bitmap);


                        try {
                            System.out.println("SENDING");
                            new HttpHelper(new SilentBugReportHTTPListener(), context).execute(model);
                        } catch (Exception e) {
                        }
                    }
                }
            });

        } catch (GleapSessionNotInitialisedException gleapSessionNotInitialisedException) {
            System.err.println("Gleap: Gleap Session not initialized.");
        } catch (InterruptedException | ExecutionException e) {
        }*/
    }

    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity) {
        createSilentBugReport(context, description, severity, "CRASH", null);
    }

    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity, JSONObject excludeData, FeedbackSentCallback feedbackSentCallback) {
        if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null &&
                UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
            if (feedbackSentCallback != null) {
                GleapConfig.getInstance().setCrashFeedbackSentCallback(feedbackSentCallback);
            }

            createSilentBugReport(context, description, severity, "CRASH", excludeData);
        }
    }
}
