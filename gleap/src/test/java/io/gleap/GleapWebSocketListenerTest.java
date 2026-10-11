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
 * The realtime socket: one socket for a session start followed by an identify, an unchanged
 * channel keeps the open socket, the backoff after closes and failures (it starts over only once
 * a socket received a message or stayed open 30 s), no reconnect after the SDK closed it itself,
 * and the background close.
 */
public class GleapWebSocketListenerTest {
    private static class FakeSocket implements WebSocket {
        final List<Integer> closes = new ArrayList<>();

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
            closes.add(code);
            return true;
        }

        @Override
        public void cancel() {
        }
    }

    private SdkTestEnvironment sdk;
    private final List<String> connects = new ArrayList<>();
    private final List<FakeSocket> sockets = new ArrayList<>();
    private final List<Long> waits = new ArrayList<>();
    private final List<Runnable> waiting = new ArrayList<>();
    private long now = 1000;
    private GleapWebSocketListener listener;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        listener = new GleapWebSocketListener() {
            @Override
            WebSocket openWebSocket(String url) {
                connects.add(url);
                FakeSocket socket = new FakeSocket();
                sockets.add(socket);
                return socket;
            }

            @Override
            void schedule(Runnable runnable, long delayMs) {
                waits.add(delayMs);
                waiting.add(runnable);
            }

            @Override
            long now() {
                return now;
            }

            @Override
            double random() {
                return 0.5;
            }
        };
        GleapEventService.setWebSocketFactoryForTesting(new GleapEventService.WebSocketFactory() {
            @Override
            public GleapWebSocketListener create() {
                return listener;
            }
        });
        GleapEventService.getInstance().startWebSocketListener();
    }

    @After
    public void tearDown() {
        GleapWebSocketListener.resetAppStateForTesting();
        sdk.tearDown();
    }

    // Lets every pending wait pass, in order.
    private void runWaiting() {
        while (!waiting.isEmpty()) {
            waiting.remove(0).run();
        }
    }

    private FakeSocket socket() {
        return sockets.get(sockets.size() - 1);
    }

    private long lastWait() {
        return waits.get(waits.size() - 1);
    }

    @Test
    public void aSessionStartAndAnIdentifyOpenOneSocket() {
        assertEquals("waits for the debounce", 0, connects.size());
        assertEquals(GleapWebSocketListener.CONNECT_DEBOUNCE_MS, lastWait());

        sdk.controller.mergeUserSession("id-2", "hash-2");
        GleapEventService.getInstance().startWebSocketListener();
        runWaiting();

        assertEquals(1, connects.size());
        assertTrue(connects.get(0).contains("gleapId=id-2&gleapHash=hash-2"));
    }

    @Test
    public void anUnchangedChannelKeepsTheOpenSocket() {
        runWaiting();
        listener.onOpen(socket(), null);

        // An identify answering with the same session, a network handover.
        GleapEventService.getInstance().startWebSocketListener();
        runWaiting();
        assertEquals(1, connects.size());
        assertTrue(socket().closes.isEmpty());

        // A new session replaces it with a normal close.
        FakeSocket first = socket();
        sdk.controller.mergeUserSession("id-2", "hash-2");
        GleapEventService.getInstance().startWebSocketListener();
        runWaiting();
        assertEquals(2, connects.size());
        assertEquals("[1000]", first.closes.toString());
    }

    @Test
    public void aCleanCloseByTheServerReconnectsWithTheBackoff() {
        runWaiting();
        FakeSocket first = socket();
        listener.onClosing(first, 1001, "going away");
        assertEquals("onClosed reconnects, not onClosing", 1, waits.size());
        listener.onClosed(first, 1001, "going away");
        assertEquals(5000, lastWait());
        runWaiting();
        assertEquals(2, connects.size());
        assertEquals(connects.get(0), connects.get(1));

        // Closed again before it opened: the wait doubles.
        listener.onClosed(socket(), 1001, "going away");
        assertEquals(10000, lastWait());

        // A socket that opens and drops right away keeps backing off.
        runWaiting();
        listener.onOpen(socket(), null);
        now += 1000;
        listener.onFailure(socket(), new Exception("reset"), null);
        assertEquals(20000, lastWait());

        // One that received a message starts over.
        runWaiting();
        listener.onOpen(socket(), null);
        listener.onMessage(socket(), "{\"name\":\"other\"}");
        listener.onClosed(socket(), 1000, null);
        assertEquals(5000, lastWait());

        // So does one that stayed open 30 s.
        runWaiting();
        listener.onClosed(socket(), 1000, null);
        assertEquals(10000, lastWait());
        runWaiting();
        listener.onOpen(socket(), null);
        now += 30000;
        listener.onClosed(socket(), 1000, null);
        assertEquals(5000, lastWait());
    }

    @Test
    public void aCloseByTheSdkDoesNotReconnect() {
        runWaiting();
        FakeSocket first = socket();
        // Closed by the server; the reconnect waits.
        listener.onClosed(first, 1001, "going away");
        assertEquals(1, waiting.size());

        // Logout / stop meanwhile: neither the waiting reconnect nor the SDK's own close connects.
        GleapEventService.getInstance().stop();
        runWaiting();
        listener.onClosing(first, 1000, "Goodbye");
        listener.onClosed(first, 1000, "Goodbye");

        assertEquals(1, connects.size());
        assertTrue(waiting.isEmpty());
    }

    @Test
    public void aReplacedSocketClosingDoesNotReconnect() {
        runWaiting();
        FakeSocket first = socket();
        sdk.controller.mergeUserSession("id-2", "hash-2");
        GleapEventService.getInstance().startWebSocketListener();
        runWaiting();

        // The replaced socket's close arrives late.
        listener.onClosed(first, 1000, "reconnect");
        listener.onFailure(first, new Exception("closed"), null);
        assertTrue(waiting.isEmpty());
        assertEquals(2, connects.size());
    }

    @Test
    public void theSocketClosesAfterTenSecondsInTheBackgroundAndReturnsInTheForeground() {
        runWaiting();
        FakeSocket first = socket();
        listener.onOpen(first, null);

        // Back within the grace: the socket stays.
        GleapWebSocketListener.setAppInBackground(true);
        assertEquals(GleapWebSocketListener.BACKGROUND_GRACE_MS, lastWait());
        GleapWebSocketListener.setAppInBackground(false);
        runWaiting();
        assertTrue(first.closes.isEmpty());
        assertEquals(1, connects.size());

        // Stays in the background: closed with 1001, no reconnect, not even for an identify.
        GleapWebSocketListener.setAppInBackground(true);
        runWaiting();
        assertEquals("[1001]", first.closes.toString());
        listener.onClosed(first, 1001, "background");
        GleapEventService.getInstance().startWebSocketListener();
        runWaiting();
        assertEquals(1, connects.size());

        // Back in the foreground: connects after the debounce.
        GleapWebSocketListener.setAppInBackground(false);
        assertEquals(GleapWebSocketListener.CONNECT_DEBOUNCE_MS, lastWait());
        runWaiting();
        assertEquals(2, connects.size());
    }

    @Test
    public void aProcessStartedInTheBackgroundOpensNoSocketUntilTheForeground() {
        GleapWebSocketListener.setAppInBackground(true);
        runWaiting();
        assertEquals(0, connects.size());

        GleapWebSocketListener.setAppInBackground(false);
        runWaiting();
        assertEquals(1, connects.size());
    }

    @Test
    public void noReconnectWhileOfflineAndAFreshStartWhenTheNetworkReturns() {
        runWaiting();
        listener.onFailure(socket(), new Exception("offline"), null);
        GleapWebSocketListener.setOnline(false);
        runWaiting();
        listener.onFailure(socket(), new Exception("offline"), null);
        assertEquals("the reconnect waits while offline", 1, connects.size());

        GleapWebSocketListener.setOnline(true);
        assertEquals(GleapWebSocketListener.CONNECT_DEBOUNCE_MS, lastWait());
        runWaiting();
        assertEquals(2, connects.size());

        // The backoff started over.
        listener.onFailure(socket(), new Exception("reset"), null);
        assertEquals(5000, lastWait());
    }
}
