package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;

/**
 * The WebSocket comes back after the server closed it, with the same backoff as after a failure,
 * but not after the SDK closed it itself.
 */
public class GleapWebSocketListenerTest {
    private static final WebSocket SOCKET = new WebSocket() {
        @Override
        public Request request() {
            return null;
        }

        @Override
        public long queueSize() {
            return 0;
        }

        @Override
        public boolean send(String text) {
            return true;
        }

        @Override
        public boolean send(ByteString bytes) {
            return true;
        }

        @Override
        public boolean close(int code, String reason) {
            return true;
        }

        @Override
        public void cancel() {
        }
    };

    private SdkTestEnvironment sdk;
    private final List<String> connects = new ArrayList<>();
    private final List<Long> waits = new ArrayList<>();
    private final List<Runnable> waiting = new ArrayList<>();
    private GleapWebSocketListener listener;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        listener = new GleapWebSocketListener() {
            @Override
            WebSocket openWebSocket(String url) {
                connects.add(url);
                return SOCKET;
            }

            @Override
            void scheduleReconnect(Runnable reconnect, long delayMs) {
                waits.add(delayMs);
                waiting.add(reconnect);
            }
        };
        assertTrue(listener.connect());
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    private static void assertWait(long base, long wait) {
        assertTrue(wait + " ms is not " + base + " ms ±20 %", wait >= base * 0.8 && wait <= base * 1.2);
    }

    @Test
    public void aCleanCloseByTheServerReconnectsWithTheBackoff() {
        listener.onClosing(SOCKET, 1001, "going away");
        assertEquals("onClosed reconnects, not onClosing", 0, waits.size());
        listener.onClosed(SOCKET, 1001, "going away");
        assertEquals(1, waits.size());
        assertWait(5000, waits.get(0));
        waiting.remove(0).run();
        assertEquals(2, connects.size());
        assertEquals(connects.get(0), connects.get(1));

        // Closed again before it opened: the wait doubles.
        listener.onClosed(SOCKET, 1001, "going away");
        assertWait(10000, waits.get(1));

        // A connect that opens starts over.
        waiting.remove(0).run();
        listener.onOpen(SOCKET, null);
        listener.onClosed(SOCKET, 1000, null);
        assertWait(5000, waits.get(2));

        // The jitter: ±20 %, never more than 60 s.
        assertEquals(4000, GleapWebSocketListener.reconnectDelay(1, 0));
        assertEquals(6000, GleapWebSocketListener.reconnectDelay(1, 0.999999999));
        assertEquals(48000, GleapWebSocketListener.reconnectDelay(10, 0));
        assertEquals(60000, GleapWebSocketListener.reconnectDelay(10, 0.999999999));
    }

    @Test
    public void aCloseByTheSdkDoesNotReconnect() {
        // Closed by the server; the reconnect waits.
        listener.onClosed(SOCKET, 1001, "going away");
        assertEquals(1, waiting.size());

        // Logout / stop meanwhile: neither the waiting reconnect nor the SDK's own close connects.
        listener.destroy();
        waiting.remove(0).run();
        listener.onClosing(SOCKET, 1000, "Goodbye");
        listener.onClosed(SOCKET, 1000, "Goodbye");

        assertEquals(1, connects.size());
        assertTrue(waiting.isEmpty());
    }
}
