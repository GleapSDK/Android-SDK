package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

class SilentBugReportUtil {
    public static void createSilentBugReport(Context context, String description, String severity, String type) {
        try {
            GleapBug model = GleapBug.getInstance();
            ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                @Override
                public void getImage(Bitmap bitmap) {
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("description", description);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    model.setType(type);
                    model.setData(obj);
                    model.setSeverity(severity);
                    model.setSilent(true);
                    if (bitmap != null) {
                        model.setScreenshot(bitmap);
                        try {
                            new HttpHelper(new SilentBugReportHTTPListener(), context).execute(model);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

        }catch (GleapSessionNotInitialisedException gleapSessionNotInitialisedException) {
            System.err.println("Gleap: Gleap Session not initialized.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static void createSilentBugReport(Context context, String description, String severity) {
      createSilentBugReport(context, description, severity, "BUG");
    }

}
