package io.gleap;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Handler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

class GleapUserSessionLoader extends AsyncTask<Void, Void, Integer> {
    private static final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions";

    @SuppressLint("WrongThread")
    @Override
    protected Integer doInBackground(Void... voids) {
        try {
            URL url = new URL(httpsUrl);
            HttpURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestMethod("POST");
            UserSession userSession = UserSessionController.getInstance().getUserSession();

            if (userSession != null && userSession.getId() != null && !userSession.getId().equals("")) {
                conn.setRequestProperty("Gleap-Id", userSession.getId());
            }
            if(userSession != null && userSession.getHash() != null && !userSession.getHash().equals("")) {
                conn.setRequestProperty("Gleap-Hash", userSession.getHash());
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }

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
                        UserSessionController.getInstance().setSessionLoaded(true);
                    }

                    if(UserSessionController.getInstance().getGleapUserSession() != null) {
                        new GleapIdentifyService().execute();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 200;
    }
}
