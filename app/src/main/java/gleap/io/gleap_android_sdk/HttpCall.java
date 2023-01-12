package gleap.io.gleap_android_sdk;

import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

import io.gleap.Gleap;

public class HttpCall extends AsyncTask {
    @Override
    protected Object doInBackground(Object[] objects) {
        HttpURLConnection conn = null;
        JSONObject result = null;
        try {
            URL url = new URL("https://613750b8eac1410017c18290.mockapi.io/key/1");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("username", "YO IM PRIAT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestMethod("GET");

            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("Key", "Value");
                requestBody.put("Key2", "Value");
            } catch (JSONException e) {
                e.printStackTrace();
            }



            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {

                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Gleap.getInstance().logNetwork(null, (String) null,null);
            Gleap.getInstance().logNetwork((HttpsURLConnection) conn, requestBody, requestBody);
            Gleap.getInstance().logNetwork((HttpsURLConnection) conn, requestBody, result);


            conn.disconnect();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}