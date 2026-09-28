package io.gleap;

import android.os.AsyncTask;

import org.json.JSONObject;

import java.net.HttpURLConnection;

/**
 * Starts or resumes the session (POST /sessions): the stored session id and hash, if any, are
 * sent along, the server answers with the session to use from now on.
 */
class GleapBaseSessionService extends AsyncTask<Void, Void, Integer> {
    interface SessionLoadedCallback {
        void invoke(boolean success);
    }

    private static final String URL_POSTFIX = "/sessions";

    private final SessionLoadedCallback sessionLoadedCallback;
    private boolean sessionEstablished = false;

    GleapBaseSessionService() {
        this(null);
    }

    GleapBaseSessionService(SessionLoadedCallback sessionLoadedCallback) {
        this.sessionLoadedCallback = sessionLoadedCallback;
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        // A logout (clearIdentity) from now on drops the answer: it would bring back the session
        // the request was sent with.
        final int generation = GleapSessionController.getInstance() != null
                ? GleapSessionController.getInstance().currentGeneration() : 0;
        boolean success = GleapRetry.withBackoff("Session request", Exception.class, new GleapRetry.Attempt() {
            @Override
            public void run() throws Exception {
                performSessionRequest(generation);
            }
        });

        if (!success) {
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().setSessionLoaded(true);
            }
        }

        sessionEstablished = success && GleapSessionController.getInstance() != null
                && GleapSessionController.getInstance().isSessionLoaded()
                && GleapSessionController.getInstance().getUserSession() != null;

        return 200;
    }

    @Override
    protected void onPostExecute(Integer result) {
        if (sessionLoadedCallback != null) {
            sessionLoadedCallback.invoke(sessionEstablished);
        }
    }

    private void performSessionRequest(int generation) throws Exception {
        // Append credentials, if they exist.
        HttpURLConnection conn = GleapHttp.openSessionPost(URL_POSTFIX,
                GleapSessionController.getInstance().getUserSession());

        JSONObject body = new JSONObject();
        body.put("lang", GleapConfig.getInstance().getLanguage());
        body.put("platform", "android");
        body.put("deviceType", GleapHelper.getDeviceType());
        GleapHttp.writeJson(conn, body);

        try {
            JSONObject result = GleapHttp.readLastJsonLine(conn.getInputStream());
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().processSessionActionResult(result, true, true, generation);
            }
        } catch (Exception e) {
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().setSessionLoaded(true);
            }
            throw e;
        }
    }
}
