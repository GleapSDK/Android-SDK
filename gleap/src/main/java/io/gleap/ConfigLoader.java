package io.gleap;

import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Loads the configuration from the server.
 */
class ConfigLoader extends AsyncTask<GleapBug, Void, Integer> {
    private final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/config/" + GleapConfig.getInstance().getSdkKey();
    private final OnHttpResponseListener listener;

    public ConfigLoader(OnHttpResponseListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onPostExecute(Integer result) {
        try {
            listener.onTaskComplete(result);
        } catch (GleapAlreadyInitialisedException e) {
        }
    }

    @Override
    protected Integer doInBackground(GleapBug... gleapBugs) {
        URL url;
        try {
            url = new URL(httpsUrl);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.connect();
            readResponse(con);
        } catch (IOException e) {
        }
        return 200;
    }

    private void readResponse(HttpsURLConnection con) throws IOException {

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
                    GleapConfig.getInstance().initConfig(result);
                    if(GleapConfig.getInstance().getConfigLoadedCallback() != null) {
                        if(result.has("flowConfig")) {
                            GleapConfig.getInstance().getConfigLoadedCallback().configLoaded(result.getJSONObject("flowConfig"));
                        }
                    }
                }

                con.disconnect();
            } catch (IOException | JSONException e) {
            }

        }

    }
}
