package io.gleap;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Manual network logging ({@link Gleap#logNetwork}).
 */
class GleapHttpInterceptor {

    /**
     * Logs a request described by the caller.
     */
    public static void log(String urlConnection, RequestType requestType, int status, int duration, JSONObject request, JSONObject response) {
        Networklog networklog = new Networklog(urlConnection, requestType, status, duration, request, response);
        GleapBug.getInstance().addRequest(networklog);
    }

    public static void log(HttpsURLConnection httpsURLConnection, String requestBody, String responseBody) {
        record(httpsURLConnection, requestBody, responseBody);
    }

    public static void log(HttpsURLConnection httpsURLConnection, JSONObject requestBody, JSONObject responseBody) {
        record(httpsURLConnection,
                requestBody != null ? requestBody.toString() : null,
                responseBody != null ? responseBody.toString() : null);
    }

    /**
     * Logs a finished request of a connection: url, method, status and response headers are read
     * from the connection. The request headers can't be read after the request was sent.
     */
    private static void record(HttpURLConnection connection, String requestBody, String responseBody) {
        if (connection == null) {
            return;
        }
        try {
            Map<String, List<String>> fields = connection.getHeaderFields();
            long sentMillis = headerMillis(fields, "X-Android-Sent-Millis");
            long receivedMillis = headerMillis(fields, "X-Android-Received-Millis");
            int duration = sentMillis > 0 && receivedMillis >= sentMillis ? (int) (receivedMillis - sentMillis) : -1;
            long startMillis = sentMillis > 0 ? sentMillis : System.currentTimeMillis() - Math.max(0, duration);

            JSONObject request = new JSONObject();
            request.put("headers", new JSONObject());
            if (requestBody != null) {
                request.put("payload", Networklog.capBody(requestBody));
            }

            JSONObject response = new JSONObject();
            boolean success;
            try {
                int status = connection.getResponseCode();
                success = status > 0;
                if (success) {
                    response.put("status", status);
                    String message = connection.getResponseMessage();
                    response.put("statusText", message != null ? message : "");
                    response.put("headers", headersToJson(fields));
                    if (responseBody != null) {
                        response.put("responseText", Networklog.capBody(responseBody));
                    }
                } else {
                    response.put("errorText", "No valid HTTP response");
                }
            } catch (IOException error) {
                success = false;
                response.put("errorText", Networklog.describeError(error));
            }

            GleapBug.getInstance().addRequest(new Networklog(connection.getRequestMethod(),
                    connection.getURL().toString(), startMillis, duration, success, request, response));
        } catch (Throwable ignore) {
        }
    }

    private static JSONObject headersToJson(Map<String, List<String>> fields) {
        JSONObject headers = new JSONObject();
        if (fields == null) {
            return headers;
        }
        for (Map.Entry<String, List<String>> field : fields.entrySet()) {
            String name = field.getKey();
            // null is the status line; X-Android-* are added by the platform's HTTP client.
            if (name == null || name.toLowerCase(Locale.ROOT).startsWith("x-android-") || field.getValue() == null) {
                continue;
            }
            try {
                headers.put(name, Networklog.headerValue(name, field.getValue()));
            } catch (Exception ignore) {
            }
        }
        return headers;
    }

    private static long headerMillis(Map<String, List<String>> fields, String name) {
        try {
            if (fields != null) {
                for (Map.Entry<String, List<String>> field : fields.entrySet()) {
                    if (name.equalsIgnoreCase(field.getKey()) && field.getValue() != null && !field.getValue().isEmpty()) {
                        return Long.parseLong(field.getValue().get(0).trim());
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return -1;
    }
}
