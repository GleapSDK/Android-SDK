package io.gleap;

import android.os.AsyncTask;

import org.json.JSONObject;

import java.net.HttpURLConnection;

import gleap.io.gleap.BuildConfig;

/**
 * Updates the session's contact (POST /sessions/partialupdate) with the pending update action.
 * Started by the SDK; there is no need to run it from the app.
 */
public class GleapUpdateSessionService extends AsyncTask<Void, Void, Integer> {
    private static final String URL_POSTFIX = "/sessions/partialupdate";

    @Override
    protected Integer doInBackground(Void... voids) {
        try {
            if (GleapSessionController.getInstance() == null) {
                return 200;
            }
            // A logout (clearIdentity) from now on drops the answer.
            final int generation = GleapSessionController.getInstance().currentGeneration();

            // Check if we have a session. If not, wait for the session to be fetched.
            GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
            if (gleapSession == null) {
                return 200;
            }

            GleapSessionProperties pendingIdentificationAction = GleapSessionController.getInstance().getPendingIdentificationAction();
            if (pendingIdentificationAction != null) {
                // There was still a pending identification action - wait.
                return 200;
            }

            // Check if there is a pending update.
            GleapSessionProperties pendingUpdateAction = GleapSessionController.getInstance().getPendingUpdateAction();
            if(pendingUpdateAction == null) {
                return 200;
            }

            // Remove pending update action.
            GleapSessionController.getInstance().setPendingUpdateAction(null);

            try {
                HttpURLConnection conn = GleapHttp.openSessionPost(URL_POSTFIX, gleapSession);

                JSONObject dataPayload = pendingUpdateAction.getJSONPayload();
                dataPayload.put("platform", "android");
                dataPayload.put("deviceType", GleapHelper.getDeviceType());
                
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("data", dataPayload);
                jsonObject.put("sdkVersion", BuildConfig.VERSION_NAME);
                jsonObject.put("type", "android");

                GleapHttp.writeJson(conn, jsonObject);

                try {
                    JSONObject result = GleapHttp.readLastJsonLine(conn.getInputStream());
                    GleapSessionController.getInstance().processSessionActionResult(result, false, false, generation);
                } catch (Exception e) {
                    // Log the error.
                    GleapLog.e("Error processing update session action", e);
                }
            } catch (Exception e) {
                // Log the error.
                GleapLog.e("Error processing update session action", e);
            }
        } catch (Exception e) {
            // Log the error.
            GleapLog.e("Error processing update session action", e);
        }

        return 200;
    }
}
