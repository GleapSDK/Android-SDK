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
        GleapBug model = GleapBug.getInstance();
        ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
            @Override
            public void getImage(Bitmap bitmap) {
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
                        new HttpHelper(IGNORE_RESPONSE, context).execute(model);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity) {
        createSilentBugReport(context, description, severity, "CRASH", null);
    }

    public static void createSilentBugReport(Context context, String description, Gleap.SEVERITY severity, JSONObject excludeData) {

        if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                GleapSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
            
            createSilentBugReport(context, description, severity, "CRASH", excludeData);
        }
    }
}
