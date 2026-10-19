package io.gleap;

import android.os.AsyncTask;

import org.json.JSONObject;

import java.net.HttpURLConnection;

/**
 * Switches to the session the widget handed over (adopt-session): a guest verified their email
 * as another contact. POST /sessions with that session's id and hash answers with the session
 * only when the hash signs the id; anything else keeps the current session. Ignored when the
 * app identified its own user.
 */
class GleapAdoptSessionService extends AsyncTask<Void, Void, Void> {
    private static final String URL_POSTFIX = "/sessions";

    private final String gleapId;
    private final String gleapHash;

    GleapAdoptSessionService(String gleapId, String gleapHash) {
        this.gleapId = gleapId;
        this.gleapHash = gleapHash;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        GleapSessionController controller = GleapSessionController.getInstance();
        if (controller == null || controller.hasIdentifiedUser()) {
            return null;
        }
        GleapSession current = controller.getUserSession();
        if (current != null && gleapId.equals(current.getId())) {
            return null;
        }

        // A logout from now on drops the answer.
        int generation = controller.currentGeneration();
        try {
            HttpURLConnection conn = GleapHttp.openSessionPost(URL_POSTFIX, new GleapSession(gleapId, gleapHash));
            JSONObject body = new JSONObject();
            body.put("lang", GleapConfig.getInstance().getLanguage());
            body.put("platform", "android");
            body.put("deviceType", GleapHelper.getDeviceType());
            GleapHttp.writeJson(conn, body);

            if (!GleapHttp.isSuccess(conn.getResponseCode())) {
                return null;
            }
            JSONObject result = GleapHttp.readLastJsonLine(conn.getInputStream());
            if (result == null || !gleapId.equals(result.optString("gleapId"))) {
                return null;
            }
            // The app may have identified its user while the request was out.
            if (controller.hasIdentifiedUser()) {
                return null;
            }
            controller.processSessionActionResult(result, true, false, generation);
        } catch (Exception e) {
            GleapLog.w("Could not adopt the session", e);
        }
        return null;
    }
}
