package io.gleap;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/**
 * {@link Gleap#setAuthTokens(Map)}: per-user tokens the project's API tools may send to the
 * app's backend ({@code {{session.authTokens.<name>}}}). Kept in memory only and posted to
 * {@code POST /sessions/auth-tokens}, which needs a signed session; after a signed identify on
 * a new session they are posted again.
 */
final class GleapAuthTokens {
    private static final String URL_POSTFIX = "/sessions/auth-tokens";
    private static final Pattern NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,39}$");
    private static final GleapAuthTokens instance = new GleapAuthTokens();
    // Runs the request (the SDK's serial request thread).
    private static volatile Executor executor = GleapExecutor.SERIAL;

    private final Map<String, String> tokens = new LinkedHashMap<>();
    // Names removed since the last delivery: sent as null.
    private final Set<String> removed = new HashSet<>();
    // The session that has every token; null while a change waits.
    private String sentFor;

    private GleapAuthTokens() {
    }

    static GleapAuthTokens getInstance() {
        return instance;
    }

    /**
     * Replaces the named tokens; a null value removes one. Invalid names are skipped with a
     * warning.
     */
    void set(Map<String, String> change) {
        if (change == null) {
            GleapLog.w("setAuthTokens expects a map of { name: token }.");
            return;
        }
        boolean changed = false;
        synchronized (this) {
            for (Map.Entry<String, String> entry : change.entrySet()) {
                String name = entry.getKey();
                if (name == null || !NAME.matcher(name).matches()) {
                    GleapLog.w("Auth token name \"" + name + "\" skipped: use a letter followed by up to 39 letters, digits, _ or -.");
                    continue;
                }
                if (entry.getValue() == null) {
                    tokens.remove(name);
                    removed.add(name);
                } else {
                    tokens.put(name, entry.getValue());
                    removed.remove(name);
                }
                changed = true;
            }
            if (changed) {
                sentFor = null;
            }
        }
        if (changed) {
            sendIfNeeded();
        }
    }

    /**
     * Logout: the tokens go with the user.
     */
    synchronized void clear() {
        tokens.clear();
        removed.clear();
        sentFor = null;
    }

    synchronized Map<String, String> tokensForTesting() {
        return new LinkedHashMap<>(tokens);
    }

    /**
     * Posts the tokens (and the names removed since the last delivery) once the session is
     * there and no identify is on its way, unless the current session has them all.
     */
    void sendIfNeeded() {
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        send();
                    } catch (Exception ignore) {
                    }
                }
            });
        } catch (Exception ignore) {
        }
    }

    private void send() throws Exception {
        GleapSessionController controller = GleapSessionController.getInstance();
        GleapSession session = controller != null ? controller.getUserSession() : null;
        // An identify on its way sends them when it lands.
        if (session == null || session.getId() == null || controller.isIdentifyInFlight()) {
            return;
        }
        final String sessionId = session.getId();
        final Map<String, String> sentTokens;
        final Set<String> sentRemoved;
        JSONObject body = new JSONObject();
        synchronized (this) {
            if ((tokens.isEmpty() && removed.isEmpty()) || sessionId.equals(sentFor)) {
                return;
            }
            sentTokens = new LinkedHashMap<>(tokens);
            sentRemoved = new HashSet<>(removed);
        }
        for (Map.Entry<String, String> entry : sentTokens.entrySet()) {
            body.put(entry.getKey(), entry.getValue());
        }
        for (String name : sentRemoved) {
            body.put(name, JSONObject.NULL);
        }

        HttpURLConnection conn = GleapHttp.openSessionPost(URL_POSTFIX, session);
        try {
            GleapHttp.writeJson(conn, new JSONObject().put("tokens", body));
            int status = conn.getResponseCode();
            if (GleapHttp.isSuccess(status)) {
                synchronized (this) {
                    GleapSession current = controller.getUserSession();
                    if (current != null && sessionId.equals(current.getId())) {
                        removed.removeAll(sentRemoved);
                        // A change made while the request was out is sent next.
                        if (tokens.equals(sentTokens) && removed.isEmpty()) {
                            sentFor = sessionId;
                        }
                    }
                }
            } else if (status == 401) {
                GleapLog.w("Auth tokens need a signed identity: call Gleap.getInstance().setUserJwt first. They are kept and sent after the next signed identify.");
            }
        } finally {
            conn.disconnect();
        }
    }

    // Tests only.
    static void setExecutorForTesting(Executor testExecutor) {
        executor = testExecutor != null ? testExecutor : GleapExecutor.SERIAL;
    }

    // Tests only.
    static void resetForTesting() {
        instance.clear();
    }
}
