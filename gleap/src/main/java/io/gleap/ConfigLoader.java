package io.gleap;

import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Loads the configuration from the server.
 */
class ConfigLoader extends AsyncTask<GleapBug, Void, JSONObject> {
    private final OnHttpResponseListener listener;
    private final boolean isReload;

    public ConfigLoader(OnHttpResponseListener listener) {
        this(listener, false);
    }

    /**
     * @param isReload true when the config is fetched again for an already running SDK
     *                 (e.g. after a language change). The config itself is applied as
     *                 usual, but the one-time initialization side effects — adding the
     *                 overlay layout and firing the configLoaded / initialized
     *                 callbacks — are skipped so the host app is not told it just
     *                 initialized a second time.
     */
    public ConfigLoader(OnHttpResponseListener listener, boolean isReload) {
        this.listener = listener;
        this.isReload = isReload;
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
            GleapLog.e("SDK key is missing in ConfigLoader");
            return new JSONObject();
        }

        final String configUrl = GleapConfig.getInstance().getApiUrl() + "/config/" + GleapConfig.getInstance().getSdkKey();

        // Connection failures and server errors (5xx, e.g. 503 while the API is overloaded) are
        // retried; readResponse reports every other failure.
        final IOException[] serverError = {null};
        boolean loaded = GleapRetry.withBackoff("Config load", IOException.class, new GleapRetry.Attempt() {
            @Override
            public void run() throws Exception {
                serverError[0] = null;
                HttpURLConnection con = GleapHttp.open(configUrl + "/?lang=" + GleapConfig.getInstance().getLanguage());
                con.connect();
                int status = con.getResponseCode();
                if (status >= 500) {
                    con.disconnect();
                    serverError[0] = new IOException("The config request was answered with HTTP " + status);
                    throw serverError[0];
                }
                readResponse(con);
            }
        });
        if (!loaded && serverError[0] != null) {
            // Reported like any other answer the config could not be read from.
            GleapErrors.report(serverError[0], "Gleap config loader");
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
                JSONObject result = GleapHttp.readLastJsonLine(con.getInputStream());

                if (result != null) {
                    GleapConfig.getInstance().initConfig(result);

                    if(result.has("flowConfig")) {
                        if (isReload) {
                            // The overlay is already attached and the app has already been
                            // told the SDK is initialized — only the config content changed.
                            con.disconnect();
                            return;
                        }

                        GleapMainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                // Config loaded. Add layout.
                                GleapOverlayManager.getInstance().addLayoutToActivity(null);
                            }
                        });

                        if(GleapCallbacks.getInstance().getConfigLoadedCallback() != null) {
                            if(result.has("flowConfig")) {
                                GleapCallbacks.getInstance().getConfigLoadedCallback().configLoaded(result.getJSONObject("flowConfig"));
                            }
                        }

                        if(GleapCallbacks.getInstance().getInitializedCallback() != null) {
                            GleapCallbacks.getInstance().getInitializedCallback().initialized();
                        }
                    } else {
                        GleapErrors.report(new Exception("Config could not be loaded. Incorrect API key."), "Gleap config loader");
                    }
                }

                con.disconnect();
            } catch (IOException | JSONException e) {
                GleapErrors.report(e, "Gleap config loader");
            }

        }

    }
}
