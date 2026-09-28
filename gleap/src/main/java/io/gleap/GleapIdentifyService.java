package io.gleap;

import android.os.AsyncTask;

import org.json.JSONObject;

import java.net.HttpURLConnection;

/**
 * Identifies the session's contact (POST /sessions/identify) with the pending identify action.
 * Started by the SDK; there is no need to run it from the app.
 */
public class GleapIdentifyService extends AsyncTask<Void, Void, Integer> {
    private static final String URL_POSTFIX = "/sessions/identify";

    @Override
    protected Integer doInBackground(Void... voids) {
        try {
            if (GleapSessionController.getInstance() == null) {
                return 200;
            }

            // If session is not ready yet, wait for session to load.
            GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
            if(gleapSession == null) {
                return 200;
            }

            GleapSessionProperties pendingAction = GleapSessionController.getInstance().getPendingIdentificationAction();
            if (pendingAction == null) {
                // Nothing to do.
                return 200;
            }

            // Reset the pending contact identification action.
            GleapSessionController.getInstance().setPendingIdentificationAction(null);

            // Verify if we need to run the identify call - check if already same data.
            GleapSessionProperties oldProps = GleapSessionController.getInstance().getGleapUserSession();
            if (oldProps != null && oldProps.equals(pendingAction)) {
                // Old equals new, nothing to do.
                return 200;
            }

            // Get payload to send.
            JSONObject jsonObject = pendingAction.getJSONPayload();
            
            // Add platform and deviceType
            jsonObject.put("platform", "android");
            jsonObject.put("deviceType", GleapHelper.getDeviceType());

            // Check if we do have an userId.
            if (!jsonObject.has( "userId")) {
                return 200;
            }

            final GleapSession session = gleapSession;
            final JSONObject payload = jsonObject;
            boolean success = GleapRetry.withBackoff("Identify request", Exception.class, new GleapRetry.Attempt() {
                @Override
                public void run() throws Exception {
                    performIdentifyRequest(session, payload);
                }
            });

            if (!success) {
                if (GleapSessionController.getInstance() != null) {
                    GleapSessionController.getInstance().clearUserSession();
                    GleapSessionController.getInstance().setSessionLoaded(true);
                }
            }

        } catch (Exception ignored) {}

        return 200;
    }

    private void performIdentifyRequest(GleapSession gleapSession, JSONObject jsonObject) throws Exception {
        HttpURLConnection conn = GleapHttp.openSessionPost(URL_POSTFIX, gleapSession);
        GleapHttp.writeJson(conn, jsonObject);

        try {
            JSONObject result = GleapHttp.readLastJsonLine(conn.getInputStream());
            GleapSessionController.getInstance().processSessionActionResult(result, true, false);
        } catch (Exception e) {
            GleapSessionController.getInstance().setSessionLoaded(true);
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().clearUserSession();
                GleapSessionController.getInstance().setSessionLoaded(true);
            }
            throw e;
        }
    }
}
