package io.gleap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

class GleapHttpInterceptor {

    /**
     * Log Http calls sent from the device. Call this function at the end of your request.
     * Request and Response can be null
     *
     * @param urlConnection url the request sent to
     * @param requestType   type of request. (GET, POST, PUT, DELETE, PATCH)
     * @param status        http status code
     * @param duration      duration in milliseconds
     * @param request       JSON  Object including important informations of the request. Recommanded:
     *                      headers, payload
     * @param response      JSON  Object including important informations of the response. Recommanded:
     *                      headers, payload, body
     */
    public static void log(String urlConnection, RequestType requestType, int status, int duration, JSONObject request, JSONObject response) {
        Networklog networklog = new Networklog(urlConnection, requestType, status, duration, request, response);
        GleapBug.getInstance().addRequest(networklog);
    }

    public static void log(HttpsURLConnection httpsURLConnection, String requestBody, String responseBody) {
        JSONObject responseBodyJSON = new JSONObject();
        if(isJSONValid(responseBody)) {
            try {
                responseBodyJSON = new JSONObject(responseBody);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }else {
            try {
                responseBodyJSON.put("data", responseBody);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        JSONObject requesteBodyJSON = new JSONObject();
        if(isJSONValid(requestBody)) {
            try {
                requesteBodyJSON = new JSONObject(requestBody);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }else {
            try {
                requesteBodyJSON.put("data", requestBody);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        log(httpsURLConnection, requesteBodyJSON, responseBodyJSON);
    }


    public static void log(HttpsURLConnection httpsURLConnection, JSONObject requestBody, JSONObject responseBody) {
        JSONObject headers = generateJSONFromMap(httpsURLConnection.getHeaderFields());
        JSONObject request = new JSONObject();
        try {
            request.put("body", requestBody);
            request.put("headers", headers);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            BigDecimal from = new BigDecimal(0);
            if(headers.has("X-Android-Sent-Millis")) {
                from = new BigDecimal(httpsURLConnection.getHeaderFields().get("X-Android-Sent-Millis").get(0));
            }

            BigDecimal to =  new BigDecimal(-1);
            if(headers.has("X-Android-Received-Millis")) {
                to =  new BigDecimal(httpsURLConnection.getHeaderFields().get("X-Android-Received-Millis").get(0));
            }
            Networklog networklog = new Networklog(httpsURLConnection.getURL().toString(), mapStringToRequestType(httpsURLConnection), httpsURLConnection.getResponseCode(), to.subtract(from).intValue(), request, responseBody);
            GleapBug.getInstance().addRequest(networklog);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static RequestType mapStringToRequestType(HttpsURLConnection httpsURLConnection) {
        String type = httpsURLConnection.getRequestMethod();
        switch (type) {
            case "POST":
                return RequestType.POST;
            case "PUT":
                return RequestType.PUT;
            case "GET":
                return RequestType.GET;
            case "DELETE":
                return RequestType.DELETE;
        }
        return RequestType.GET;
    }

    private static JSONObject generateJSONFromMap(Map<String, List<String>> headers) {
        JSONObject result = new JSONObject();
        for (String key : headers.keySet()) {
            try {
                if (headers.get(key) != null && headers.get(key).get(0) != null) {
                    if(key != null) {
                        result.put(key, headers.get(key).get(0));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            // edited, to include @Arthur's comment
            // e.g. in case JSONArray is valid as well...
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

}
