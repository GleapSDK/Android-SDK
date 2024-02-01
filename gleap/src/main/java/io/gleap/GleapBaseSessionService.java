package io.gleap;

import android.annotation.SuppressLint;
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

class GleapBaseSessionService extends AsyncTask<Void, Void, Integer> {
    private static final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions";

    @SuppressLint("WrongThread")
    @Override
    protected Integer doInBackground(Void... voids) {
        try {
            URL url = new URL(httpsUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            // Append credentials, if they exist.
            GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
            if (gleapSession != null && gleapSession.getId() != null && !gleapSession.getId().isEmpty()) {
                conn.setRequestProperty("Gleap-Id", gleapSession.getId());
            }
            if (gleapSession != null && gleapSession.getHash() != null && !gleapSession.getHash().isEmpty()) {
                conn.setRequestProperty("Gleap-Hash", gleapSession.getHash());
            }

            try (OutputStream os = conn.getOutputStream()) {
                JSONObject body = new JSONObject();
                body.put("lang", GleapConfig.getInstance().getLanguage());
                byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }

                GleapSessionController.getInstance().processSessionActionResult(result, true, true);
            } catch (Exception e) {
                GleapSessionController.getInstance().setSessionLoaded(true);
            }
        } catch (Exception e) {
            GleapSessionController.getInstance().setSessionLoaded(true);
        }

        return 200;
    }
}
