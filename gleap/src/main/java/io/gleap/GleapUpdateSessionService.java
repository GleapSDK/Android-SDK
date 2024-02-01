package io.gleap;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class GleapUpdateSessionService extends AsyncTask<Void, Void, Integer> {
    private static final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions/partialupdate";

    @Override
    protected Integer doInBackground(Void... voids) {
        try {
            if (GleapSessionController.getInstance() == null) {
                return 200;
            }

            // Check if we have a session. If not, wait for the session to be fetched.
            GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
            if (gleapSession == null) {
                return 200;
            }

            GleapSessionProperties gleapSessionProperties = GleapSessionController.getInstance().getPendingUpdateAction();
            if(gleapSessionProperties == null) {
                return 200;
            }

            // Remove pending update action.
            GleapSessionController.getInstance().setPendingUpdateAction(null);

            try {
                URL url = new URL(httpsUrl);
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestMethod("POST");

                if (gleapSession.getId() != null && !gleapSession.getId().equals("")) {
                    conn.setRequestProperty("Gleap-Id", gleapSession.getId());
                }

                if (gleapSession.getHash() != null && !gleapSession.getHash().equals("")) {
                    conn.setRequestProperty("Gleap-Hash", gleapSession.getHash());
                }

                JSONObject jsonObject = gleapSessionProperties.getJSONPayload();

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    JSONObject result = null;
                    String input;
                    while ((input = br.readLine()) != null) {
                        result = new JSONObject(input);
                    }

                    GleapSessionController.getInstance().processSessionActionResult(result, false, false);
                } catch (Exception e) {}
            } catch (Exception e) {}
        } catch (Exception e) {}

        return 200;
    }
}
