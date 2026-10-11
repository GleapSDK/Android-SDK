package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.gleap.GleapWebSocketPolicy.Action;

/**
 * The realtime socket's rules: when a connect request opens a socket, keeps the open one or
 * waits, the reconnect backoff and when it starts over.
 */
public class GleapWebSocketPolicyTest {
    private static String channel(String gleapId, String gleapHash, String apiKey) {
        return "wss://ws.gleap.test?gleapId=" + gleapId + "&gleapHash=" + gleapHash + "&apiKey=" + apiKey + "&sdkVersion=1";
    }

    private static final String OPEN = channel("gid", "hash", "key");

    @Test
    public void anUnchangedChannelKeepsTheOpenSocket() {
        assertEquals(Action.KEEP, GleapWebSocketPolicy.action(OPEN, OPEN, true, false, true));
        String reordered = "wss://ws.gleap.test?gleapHash=hash&apiKey=key&gleapId=gid&sdkVersion=1";
        assertEquals(Action.KEEP, GleapWebSocketPolicy.action(reordered, OPEN, true, false, true));
    }

    @Test
    public void aChangedChannelOrADeadSocketConnects() {
        assertEquals("new gleapId", Action.CONNECT, GleapWebSocketPolicy.action(channel("gid2", "hash", "key"), OPEN, true, false, true));
        assertEquals("new gleapHash", Action.CONNECT, GleapWebSocketPolicy.action(channel("gid", "hash2", "key"), OPEN, true, false, true));
        assertEquals("new apiKey", Action.CONNECT, GleapWebSocketPolicy.action(channel("gid", "hash", "other"), OPEN, true, false, true));
        assertEquals("other host", Action.CONNECT, GleapWebSocketPolicy.action(OPEN.replace("ws.gleap.test", "ws.eu.gleap.test"), OPEN, true, false, true));
        assertEquals("the socket is gone", Action.CONNECT, GleapWebSocketPolicy.action(OPEN, OPEN, false, false, true));
        assertEquals("no socket yet", Action.CONNECT, GleapWebSocketPolicy.action(OPEN, null, false, false, true));
    }

    @Test
    public void noConnectInTheBackgroundOfflineOrWithoutASession() {
        assertEquals("background", Action.WAIT, GleapWebSocketPolicy.action(OPEN, null, false, true, true));
        assertEquals("background, new channel", Action.WAIT, GleapWebSocketPolicy.action(channel("gid2", "hash", "key"), OPEN, true, true, true));
        assertEquals("offline", Action.WAIT, GleapWebSocketPolicy.action(OPEN, null, false, false, false));
        assertEquals("no session", Action.NONE, GleapWebSocketPolicy.action(null, OPEN, true, false, true));
    }

    @Test
    public void reconnectBackoffDoublesFromFiveToSixtySecondsWithJitter() {
        long[] bases = {5000, 10000, 20000, 40000, 60000, 60000, 60000};
        for (int i = 0; i < bases.length; i++) {
            int failures = i + 1;
            assertEquals(failures + " failures", bases[i], GleapWebSocketPolicy.reconnectDelay(failures, 0.5));
            assertEquals("-20 % after " + failures, Math.round(bases[i] * 0.8), GleapWebSocketPolicy.reconnectDelay(failures, 0));
            assertEquals("+20 % after " + failures, Math.min(Math.round(bases[i] * 1.2), 60000),
                    GleapWebSocketPolicy.reconnectDelay(failures, 0.999999999));
        }
        assertEquals(5000, GleapWebSocketPolicy.reconnectDelay(0, 0.5));
        assertEquals(60000, GleapWebSocketPolicy.reconnectDelay(1000, 0.5));
        assertEquals("random is clamped", 16000, GleapWebSocketPolicy.reconnectDelay(3, -1));
        assertEquals("random is clamped", 24000, GleapWebSocketPolicy.reconnectDelay(3, 5));
    }

    @Test
    public void theBackoffStartsOverAfterAMessageOrThirtySecondsOpen() {
        assertFalse("never opened", GleapWebSocketPolicy.shouldResetBackoff(-1, false));
        assertFalse("closed right after opening", GleapWebSocketPolicy.shouldResetBackoff(200, false));
        assertFalse(GleapWebSocketPolicy.shouldResetBackoff(29999, false));
        assertTrue(GleapWebSocketPolicy.shouldResetBackoff(30000, false));
        assertTrue("received a message", GleapWebSocketPolicy.shouldResetBackoff(200, true));
    }
}
