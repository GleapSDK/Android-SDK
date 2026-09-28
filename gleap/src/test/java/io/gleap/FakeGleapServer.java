package io.gleap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

/**
 * Stands in for the Gleap API in local unit tests: every request the SDK makes through
 * {@link GleapHttp} is recorded and answered with the queued response for its path.
 */
class FakeGleapServer implements GleapHttp.ConnectionFactory {
    static final class Response {
        final int status;
        final String body;
        final IOException error;
        final Map<String, String> headers = new LinkedHashMap<>();

        Response(int status, String body, IOException error) {
            this.status = status;
            this.body = body;
            this.error = error;
        }
    }

    final List<Request> requests = new ArrayList<>();
    private final Map<String, Deque<Response>> responses = new LinkedHashMap<>();
    private final Map<String, Runnable> whileAnswering = new LinkedHashMap<>();

    /**
     * Answers the next request whose path starts with {@code pathPrefix}; the last queued
     * response for a path is repeated.
     */
    FakeGleapServer respond(String pathPrefix, int status, String body) {
        queue(pathPrefix).add(new Response(status, body, null));
        return this;
    }

    /**
     * {@link #respond(String, int, String)} with a response header (e.g. Retry-After).
     */
    FakeGleapServer respondWithHeader(String pathPrefix, int status, String body, String name, String value) {
        Response response = new Response(status, body, null);
        response.headers.put(name, value);
        queue(pathPrefix).add(response);
        return this;
    }

    /**
     * Forgets the responses queued for the path.
     */
    FakeGleapServer clear(String pathPrefix) {
        responses.remove(pathPrefix);
        return this;
    }

    /**
     * Runs {@code action} while a request to exactly this path is in flight: after it was sent,
     * before its answer is read (e.g. the app logs out meanwhile).
     */
    FakeGleapServer whileAnswering(String path, Runnable action) {
        whileAnswering.put(path, action);
        return this;
    }

    FakeGleapServer fail(String pathPrefix, IOException error) {
        queue(pathPrefix).add(new Response(0, null, error));
        return this;
    }

    private Deque<Response> queue(String pathPrefix) {
        Deque<Response> queue = responses.get(pathPrefix);
        if (queue == null) {
            queue = new ArrayDeque<>();
            responses.put(pathPrefix, queue);
        }
        return queue;
    }

    /**
     * The requests to exactly this path, or to paths below it when it ends with a slash.
     */
    List<Request> requestsTo(String path) {
        List<Request> matching = new ArrayList<>();
        for (Request request : requests) {
            String requestPath = request.getURL().getPath();
            if (path.endsWith("/") ? requestPath.startsWith(path) : requestPath.equals(path)) {
                matching.add(request);
            }
        }
        return matching;
    }

    Request last(String pathPrefix) {
        List<Request> matching = requestsTo(pathPrefix);
        return matching.isEmpty() ? null : matching.get(matching.size() - 1);
    }

    @Override
    public HttpURLConnection open(URL url) {
        // The longest matching path prefix answers.
        Response response = new Response(404, "", null);
        String match = null;
        for (String prefix : responses.keySet()) {
            if (url.getPath().startsWith(prefix) && (match == null || prefix.length() > match.length())) {
                match = prefix;
            }
        }
        if (match != null) {
            Deque<Response> queue = responses.get(match);
            response = queue.size() > 1 ? queue.poll() : queue.peek();
        }
        Request request = new Request(url, response, whileAnswering.get(url.getPath()));
        requests.add(request);
        return request;
    }

    /**
     * One recorded request, answered like HttpURLConnection does: an error status throws from
     * getInputStream and the body is in getErrorStream.
     */
    static final class Request extends HttpURLConnection {
        final Map<String, String> headers = new LinkedHashMap<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final Response response;
        private Runnable whileAnswering;
        int connectTimeout = 0;
        int readTimeout = 0;

        Request(URL url, Response response, Runnable whileAnswering) {
            super(url);
            this.response = response;
            this.whileAnswering = whileAnswering;
        }

        private void answer() {
            Runnable action = whileAnswering;
            whileAnswering = null;
            if (action != null) {
                action.run();
            }
        }

        String bodyText() {
            return new String(body.toByteArray(), StandardCharsets.UTF_8);
        }

        JSONObject bodyJson() throws Exception {
            return new JSONObject(bodyText());
        }

        @Override
        public void connect() throws IOException {
            if (response.error != null) {
                throw response.error;
            }
            connected = true;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public String getRequestProperty(String key) {
            return headers.get(key);
        }

        @Override
        public void setConnectTimeout(int timeout) {
            connectTimeout = timeout;
        }

        @Override
        public int getConnectTimeout() {
            return connectTimeout;
        }

        @Override
        public void setReadTimeout(int timeout) {
            readTimeout = timeout;
        }

        @Override
        public int getReadTimeout() {
            return readTimeout;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            connect();
            return body;
        }

        @Override
        public int getResponseCode() throws IOException {
            connect();
            answer();
            return response.status;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            answer();
            if (response.status == 404) {
                throw new FileNotFoundException(url.toString());
            }
            if (response.status >= 400) {
                throw new IOException("Server returned HTTP response code: " + response.status);
            }
            return new ByteArrayInputStream(response.body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String getHeaderField(String name) {
            if (response.error != null || name == null) {
                return null;
            }
            for (Map.Entry<String, String> header : response.headers.entrySet()) {
                if (header.getKey().equalsIgnoreCase(name)) {
                    return header.getValue();
                }
            }
            return null;
        }

        @Override
        public InputStream getErrorStream() {
            if (response.status < 400 || response.body == null) {
                return null;
            }
            return new ByteArrayInputStream(response.body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
