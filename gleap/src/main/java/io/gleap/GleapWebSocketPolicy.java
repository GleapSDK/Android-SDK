package io.gleap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rules behind the realtime socket ({@link GleapWebSocketListener}), kept free of state so
 * they can be tested: when to open a socket, when to keep the one that is open, and how long to
 * wait before reconnecting after an error (5 s, doubled per failure up to 60 s, each ±20 % so
 * devices do not reconnect in step). Mirrors the iOS SDK's GleapWebSocketPolicy.
 */
final class GleapWebSocketPolicy {
    static final long FIRST_RECONNECT_DELAY_MS = 5000;
    static final long MAX_RECONNECT_DELAY_MS = 60000;
    // A socket that stayed open this long (or received a message) worked: the backoff starts over.
    static final long STABLE_AFTER_MS = 30000;

    enum Action {
        // Nothing to connect to (no session yet).
        NONE,
        // The open (or opening) socket already serves the wanted channel: leave it alone.
        KEEP,
        // The app is in the background or offline: open nothing now, foreground / network return retries.
        WAIT,
        // Open a socket for the wanted channel (closing any other one).
        CONNECT,
    }

    private GleapWebSocketPolicy() {
    }

    /**
     * What to do for the channel {@code desiredUrl} (gleapId, gleapHash and apiKey are in its
     * query) while {@code currentUrl} is the channel of the socket that is open or opening
     * ({@code socketAlive}), if any.
     */
    static Action action(String desiredUrl, String currentUrl, boolean socketAlive,
                         boolean inBackground, boolean online) {
        if (desiredUrl == null) {
            return Action.NONE;
        }
        if (inBackground || !online) {
            return Action.WAIT;
        }
        if (socketAlive && isSameChannel(desiredUrl, currentUrl)) {
            return Action.KEEP;
        }
        return Action.CONNECT;
    }

    /**
     * Whether the same channel is wanted: the same URL apart from the order of the query parameters.
     */
    static boolean isSameChannel(String url, String otherUrl) {
        if (url == null || otherUrl == null) {
            return false;
        }
        if (url.equals(otherUrl)) {
            return true;
        }
        int query = url.indexOf('?');
        int otherQuery = otherUrl.indexOf('?');
        String base = query < 0 ? url : url.substring(0, query);
        String otherBase = otherQuery < 0 ? otherUrl : otherUrl.substring(0, otherQuery);
        if (!base.equals(otherBase)) {
            return false;
        }
        return sortedParameters(query < 0 ? "" : url.substring(query + 1))
                .equals(sortedParameters(otherQuery < 0 ? "" : otherUrl.substring(otherQuery + 1)));
    }

    private static List<String> sortedParameters(String query) {
        List<String> parameters = new ArrayList<>();
        for (String parameter : query.split("&")) {
            if (!parameter.isEmpty()) {
                parameters.add(parameter);
            }
        }
        Collections.sort(parameters);
        return parameters;
    }

    /**
     * The wait before reconnecting after {@code failures} failed connections in a row (1 = the
     * first): 5 s doubled per failure up to 60 s, times a factor between 0.8 and 1.2, never more
     * than 60 s.
     *
     * @param random uniformly distributed in [0, 1)
     */
    static long reconnectDelay(int failures, double random) {
        long base = Math.min(FIRST_RECONNECT_DELAY_MS << Math.max(0, Math.min(failures - 1, 4)), MAX_RECONNECT_DELAY_MS);
        double factor = 1 - GleapPingBackoff.JITTER + 2 * GleapPingBackoff.JITTER * Math.min(Math.max(random, 0), 1);
        return Math.min(Math.round(base * factor), MAX_RECONNECT_DELAY_MS);
    }

    /**
     * Whether a socket that just failed or closed counts as having worked, so the backoff starts
     * over: it was open for at least 30 s or received a message. A socket that opens and drops
     * right away keeps backing off.
     *
     * @param openForMs how long it was open, negative when it never opened
     */
    static boolean shouldResetBackoff(long openForMs, boolean receivedMessage) {
        return receivedMessage || openForMs >= STABLE_AFTER_MS;
    }
}
