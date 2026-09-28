package io.gleap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The SDK's HTTP requests to the Gleap API. All of them authenticate with the SDK key and,
 * once there is one, the session id and hash. The API url is read when a request is made, so
 * setRegion and setApiUrl apply to every later request.
 */
final class GleapHttp {
    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private static final ConnectionFactory SYSTEM = new ConnectionFactory() {
        @Override
        public HttpURLConnection open(URL url) throws IOException {
            return (HttpURLConnection) url.openConnection();
        }
    };

    private static volatile ConnectionFactory factory = SYSTEM;

    private GleapHttp() {
    }

    static HttpURLConnection open(String url) throws IOException {
        return factory.open(new URL(url));
    }

    /**
     * A JSON POST to {@code apiUrl + path} with the session headers of the session requests
     * (sessions, identify, partial update): Gleap-Id and Gleap-Hash only when they are set.
     */
    static HttpURLConnection openSessionPost(String path, GleapSession session) throws IOException {
        HttpURLConnection conn = open(GleapConfig.getInstance().getApiUrl() + path);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        if (session != null && session.getId() != null && !session.getId().isEmpty()) {
            conn.setRequestProperty("Gleap-Id", session.getId());
        }
        if (session != null && session.getHash() != null && !session.getHash().isEmpty()) {
            conn.setRequestProperty("Gleap-Hash", session.getHash());
        }
        return conn;
    }

    /**
     * A JSON POST to {@code apiUrl + path} with the headers of the report and event requests.
     */
    static HttpURLConnection openReportPost(String path, GleapSession session) throws IOException {
        HttpURLConnection conn = open(GleapConfig.getInstance().getApiUrl() + path);
        conn.setRequestProperty("api-token", GleapConfig.getInstance().getSdkKey());
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        addReportSessionHeaders(conn, session);
        return conn;
    }

    /**
     * The session headers of the report, event and upload requests: set whenever there is a
     * session.
     */
    static void addReportSessionHeaders(HttpURLConnection conn, GleapSession session) {
        if (session != null) {
            conn.setRequestProperty("gleap-id", session.getId());
            conn.setRequestProperty("gleap-hash", session.getHash());
        }
    }

    static void writeJson(HttpURLConnection conn, JSONObject body) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    /**
     * Reads a JSON response the way the SDK always did: every line must be a JSON object, the
     * last one is the result (null for an empty body).
     */
    static JSONObject readLastJsonLine(InputStream stream) throws IOException, JSONException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            JSONObject result = null;
            String line;
            while ((line = reader.readLine()) != null) {
                result = new JSONObject(line);
            }
            return result;
        }
    }

    // Tests only; null restores the system connections.
    static void setConnectionFactoryForTesting(ConnectionFactory connectionFactory) {
        factory = connectionFactory != null ? connectionFactory : SYSTEM;
    }
}
