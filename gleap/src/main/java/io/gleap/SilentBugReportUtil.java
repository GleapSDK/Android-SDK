package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

class SilentBugReportUtil {
    // Silent reports have no UI to update when the request finishes.
    private static final OnHttpResponseListener IGNORE_RESPONSE = new OnHttpResponseListener() {
        @Override
        public void onTaskComplete(JSONObject response) {
        }
    };

    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity, String type, JSONObject excludeData) {

        if (excludeData == null || (excludeData != null && excludeData.length() == 0)) {
            excludeData = new JSONObject();
            try {
                excludeData.put("screenshot", true);
                excludeData.put("replay", true);
            } catch (Exception ex) {
            }
        }
        GleapConfig.getInstance().setCrashStripModel(excludeData);

        // The screenshot is excluded by default: the report is sent right away. Otherwise it goes
        // with the screenshot, or without one when none could be taken.
        if (excludeData.optBoolean("screenshot", false)) {
            send(context, description, severity, type, null);
            return;
        }
        ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
            @Override
            public void getImage(Bitmap bitmap) {
                send(context, description, severity, type, bitmap);
            }
        });
    }

    private static void send(Context context, String description, Gleap.SEVERITY severity, String type, Bitmap screenshot) {
        GleapBug model = GleapBug.getInstance();
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
        model.setScreenshot(screenshot);

        try {
            HttpHelper.send(IGNORE_RESPONSE, context);
        } catch (Exception e) {
            GleapLog.w("Could not send the silent crash report", e);
        }
    }

    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity) {
        createSilentBugReport(context, description, severity, "CRASH", null);
    }

    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity, JSONObject excludeData) {

        if (!GleapDetectorUtil.isWidgetOpen() && GleapSessionController.getInstance() != null &&
                GleapSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
            
            createSilentBugReport(context, description, severity, "CRASH", excludeData);
        }
    }
}
