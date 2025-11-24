package io.gleap;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;
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
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    @SuppressLint("WrongThread")
    @Override
    protected Integer doInBackground(Void... voids) {
        // Attempt the request with retry logic
        boolean success = false;
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                performSessionRequest();
                success = true;
                break;
            } catch (Exception e) {
                lastException = e;
                Log.w("Gleap", "Session request attempt " + attempt + " failed", e);
                
                if (attempt < MAX_RETRIES) {
                    try {
                        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!success) {
            Log.e("Gleap", "All session request attempts failed after " + MAX_RETRIES + " retries", lastException);
            
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().setSessionLoaded(true);
            }
        }

        return 200;
    }

    private void performSessionRequest() throws Exception {
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
            Log.d("Gleap", "Gleap-Id: " + gleapSession.getId());
        }
        if (gleapSession != null && gleapSession.getHash() != null && !gleapSession.getHash().isEmpty()) {
            conn.setRequestProperty("Gleap-Hash", gleapSession.getHash());
            Log.d("Gleap", "Gleap-Hash: " + gleapSession.getHash());
        }

        try (OutputStream os = conn.getOutputStream()) {
            JSONObject body = new JSONObject();
            body.put("lang", GleapConfig.getInstance().getLanguage());
            body.put("platform", "Android");
            body.put("deviceType", GleapHelper.getDeviceType());
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


            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().processSessionActionResult(result, true, true);
            }
        } catch (Exception e) {
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().setSessionLoaded(true);
            }
            throw e;
        }
    }
}
