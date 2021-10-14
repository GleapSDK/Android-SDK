package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

class SilentBugReportUtil {
    public static void createSilentBugReport(Context context, String description, String severity) {
        try {
            GleapBug model = GleapBug.getInstance();
            Bitmap bitmap = ScreenshotUtil.takeScreenshot();
            JSONObject obj = new JSONObject();
            try {
                obj.put("description", description);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            model.setType("BUG");
            model.setData(obj);
            model.setSeverity(severity);
            if (bitmap != null) {
                model.setScreenshot(bitmap);
                try {
                    new HttpHelper(new SilentBugReportHTTPListener(), context).execute(model);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }catch (GleapSessionNotInitialisedException gleapSessionNotInitialisedException) {
            System.err.println("Gleap: Gleap Session not initialized.");
        }
    }

}
