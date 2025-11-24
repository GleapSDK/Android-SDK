package io.gleap;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class GleapIdentifyService extends AsyncTask<Void, Void, Integer> {
    private static final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions/identify";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

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
            jsonObject.put("platform", "Android");
            jsonObject.put("deviceType", GleapHelper.getDeviceType());

            // Check if we do have an userId.
            if (!jsonObject.has( "userId")) {
                return 200;
            }

            // Attempt the request with retry logic
            boolean success = false;
            Exception lastException = null;
            
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    performIdentifyRequest(gleapSession, jsonObject);
                    success = true;
                    break;
                } catch (Exception e) {
                    lastException = e;
                    Log.w("Gleap", "Identify request attempt " + attempt + " failed", e);
                    
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
                Log.e("Gleap", "All identify request attempts failed after " + MAX_RETRIES + " retries", lastException);
                
                if (GleapSessionController.getInstance() != null) {
                    GleapSessionController.getInstance().clearUserSession();
                    GleapSessionController.getInstance().setSessionLoaded(true);
                }
            }

        } catch (Exception ignored) {}

        return 200;
    }

    private void performIdentifyRequest(GleapSession gleapSession, JSONObject jsonObject) throws Exception {
        URL url = new URL(httpsUrl);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        // Append credentials.
        if (gleapSession.getId() != null && !gleapSession.getId().equals("")) {
            conn.setRequestProperty("Gleap-Id", gleapSession.getId());
        }
        if (gleapSession.getHash() != null && !gleapSession.getHash().equals("")) {
            conn.setRequestProperty("Gleap-Hash", gleapSession.getHash());
        }

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
