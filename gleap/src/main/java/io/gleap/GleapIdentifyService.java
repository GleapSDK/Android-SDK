package io.gleap;

import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class GleapIdentifyService extends AsyncTask<Void, Void, Integer> {
    private static final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions/identify";
    public boolean isLoaded = false;
    @Override
    protected Integer doInBackground(Void... voids) {
        UserSession userSession = UserSessionController.getInstance().getUserSession();
        if(userSession == null && !isLoaded && UserSessionController.getInstance().isSessionLoaded()) {
            return 200;
        }
        try {
            GleapUser gleapUser = UserSessionController.getInstance().getGleapUserSession();

            URL url = new URL(httpsUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestMethod("POST");

            if (userSession != null && userSession.getId() != null && !userSession.getId().equals("")) {
                conn.setRequestProperty("Gleap-Id", userSession.getId());
            }

            if (userSession != null && userSession.getHash() != null && !userSession.getHash().equals("")) {
                conn.setRequestProperty("Gleap-Hash", userSession.getHash());
            }

            JSONObject jsonObject = new JSONObject();
            if (gleapUser != null) {
                try {
                    if (gleapUser.getUserId() != null) {
                        jsonObject.put("userId", gleapUser.getUserId());
                    }

                    if (gleapUser.getGleapUserProperties() != null) {
                        jsonObject.put("email", gleapUser.getGleapUserProperties().getEmail());
                        jsonObject.put("name", gleapUser.getGleapUserProperties().getName());
                        jsonObject.put("userHash", gleapUser.getGleapUserProperties().getHash());
                    }
                } catch (Exception ex) {

                }
            }

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }



            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }

                conn.getInputStream().close();

                String id = null;
                String hash = null;

                if (result != null) {
                    if (result.has("gleapId")) {
                        id = result.getString("gleapId");
                    }

                    if (result.has("gleapHash")) {
                        hash = result.getString("gleapHash");
                    }


                    if (id != null && hash != null) {
                        UserSessionController.getInstance().mergeUserSession(id, hash);
                        isLoaded = true;
                    }
                }
                conn.getOutputStream().close();
                conn.disconnect();
            } catch (JSONException e) {
                UserSessionController.getInstance().clearUserSession();
                e.printStackTrace();
            }

        } catch (IOException e) {
            UserSessionController.getInstance().clearUser();
            isLoaded = true;
        }
        return 200;
    }
}
