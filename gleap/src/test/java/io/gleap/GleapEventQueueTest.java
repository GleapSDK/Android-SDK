package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Events tracked by the app wait in a queue until a ping delivers them.
 */
public class GleapEventQueueTest {
    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    private static JSONObject event(int index) throws Exception {
        return new JSONObject().put("name", "event-" + index);
    }

    @Test
    public void theQueueKeepsTheNewestEvents() throws Exception {
        GleapEventQueue queue = new GleapEventQueue();
        for (int i = 0; i < GleapEventQueue.MAX_EVENTS + 5; i++) {
            queue.add(event(i));
        }

        JSONArray events = queue.toJSONArray();
        assertEquals(GleapEventQueue.MAX_EVENTS, events.length());
        assertEquals("event-5", events.getJSONObject(0).getString("name"));
        assertEquals("event-" + (GleapEventQueue.MAX_EVENTS + 4), events.getJSONObject(events.length() - 1).getString("name"));
    }

    @Test
    public void trackedEventsAreSentAndThenRemoved() throws Exception {
        sdk.server.respond("/sessions/ping", 200, "");
        Gleap.getInstance().trackEvent("signup");
        Gleap.getInstance().trackEvent("checkout", new JSONObject().put("total", 12));

        GleapEventService.getInstance().sendQueuedEvents();

        JSONArray sent = sdk.server.last("/sessions/ping").bodyJson().getJSONArray("events");
        assertEquals(2, sent.length());
        assertEquals("signup", sent.getJSONObject(0).getString("name"));
        assertEquals(12, sent.getJSONObject(1).getJSONObject("data").getInt("total"));
        assertTrue(GleapEventService.getInstance().getEventQueue().isEmpty());
    }

    @Test
    public void eventsTrackedDuringAPingWaitForTheNextOne() throws Exception {
        sdk.server.respond("/sessions/ping", 200, "")
                .whileAnswering("/sessions/ping", new Runnable() {
                    @Override
                    public void run() {
                        Gleap.getInstance().trackEvent("checkout");
                    }
                });
        Gleap.getInstance().trackEvent("signup");

        GleapEventService.getInstance().sendQueuedEvents();

        JSONArray queued = GleapEventService.getInstance().getEventQueue().toJSONArray();
        assertEquals(1, queued.length());
        assertEquals("checkout", queued.getJSONObject(0).getString("name"));
    }

    @Test
    public void theSessionStartIsTrackedOncePerSessionNotPerReconnect() throws Exception {
        sdk.controller.processSessionActionResult(new JSONObject().put("gleapId", "id-1").put("gleapHash", "hash-1"), true, true);
        // The WebSocket connects, drops and connects again.
        GleapEventService.getInstance().start();
        GleapEventService.getInstance().start();

        JSONArray queued = GleapEventService.getInstance().getEventQueue().toJSONArray();
        int sessionStarts = 0;
        for (int i = 0; i < queued.length(); i++) {
            if ("sessionStarted".equals(queued.getJSONObject(i).getString("name"))) {
                sessionStarts++;
            }
        }
        assertEquals(1, sessionStarts);
    }

    @Test
    public void aFailedPingKeepsTheEventsForTheNextOne() throws Exception {
        sdk.server.respond("/sessions/ping", 503, "{\"status\":\"overloaded\"}");
        Gleap.getInstance().trackEvent("signup");

        GleapEventService.getInstance().sendQueuedEvents();

        assertEquals(1, GleapEventService.getInstance().getEventQueue().size());
    }

    @Test
    public void clearIdentityDeletesTheSessionAndTheQueuedEvents() throws Exception {
        Gleap.getInstance().trackEvent("signup");

        Gleap.getInstance().clearIdentity();

        assertTrue(GleapEventService.getInstance().getEventQueue().isEmpty());
        assertNull(sdk.controller.getUserSession());
        assertTrue(sdk.store.values.isEmpty());
    }
}
