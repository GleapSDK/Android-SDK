package io.gleap;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Loads the configuration from the server.
 */
class ConfigLoader extends AsyncTask<GleapBug, Void, JSONObject> {
    private final OnHttpResponseListener listener;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    public ConfigLoader(OnHttpResponseListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        try {
            listener.onTaskComplete(result);
        } catch (GleapAlreadyInitialisedException e) {
        }
    }

    @Override
    protected JSONObject doInBackground(GleapBug... gleapBugs) {


        String sdkKey = GleapConfig.getInstance().getSdkKey();
        if (sdkKey == null || sdkKey.trim().isEmpty()) {
            Log.e("Gleap", "SDK key is missing in ConfigLoader");
            return new JSONObject();
        }

        String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/config/" + GleapConfig.getInstance().getSdkKey();

        boolean success = false;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                URL url = new URL(httpsUrl + "/?lang=" + GleapConfig.getInstance().getLanguage());
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.connect();
                readResponse(con);
                success = true;
                break;
            } catch (IOException e) {
                lastException = e;
                Log.w("Gleap", "Config load attempt " + attempt + " failed", e);

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
            Log.e("Gleap", "All config load attempts failed after " + MAX_RETRIES + " retries", lastException);
        }

        JSONObject response = new JSONObject();
        try{
            response.put("status", 200);
        }catch (Exception ignore) {}

        return response;
    }

    private void readResponse(HttpURLConnection con) throws IOException {
        if (con != null) {

            try {
                BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(con.getInputStream()));
                String input;
                JSONObject result = null;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }
                br.close();

                if (result != null) {
                    Handler mainThreadHandler = new Handler(Looper.getMainLooper());

                    GleapConfig.getInstance().initConfig(result);

                    if(result.has("flowConfig")) {
                        mainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // Config loaded. Add layout.
                                GleapInvisibleActivityManger.getInstance().addLayoutToActivity(null);
                            }
                        });

                        if(GleapConfig.getInstance().getConfigLoadedCallback() != null) {
                            if(result.has("flowConfig")) {
                                GleapConfig.getInstance().getConfigLoadedCallback().configLoaded(result.getJSONObject("flowConfig"));
                            }
                        }

                        if(GleapConfig.getInstance().getInitializedCallback() != null) {
                            GleapConfig.getInstance().getInitializedCallback().initialized();
                        }
                    } else {
                        Gleap.getInstance().handleError(new Exception("Config could not be loaded. Incorrect API key."), "Gleap config loader");
                    }
                }

                con.disconnect();
            } catch (IOException | JSONException e) {
                Gleap.getInstance().handleError(e, "Gleap config loader");
            }

        }

    }
}
