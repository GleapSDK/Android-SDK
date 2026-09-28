package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The pings (POST /sessions/ping) that deliver the queued events: only with a session, one at a
 * time, in batches, backing off while the server does not take them.
 */
public class GleapEventPingTest {
    private static final String PING = "/sessions/ping";

    private SdkTestEnvironment sdk;
    private GleapEventService service;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        service = GleapEventService.getInstance();
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    @Test
    public void noPingWithoutASession() throws Exception {
        service.start();
        // The API rejected an identify: the session is gone, but the SDK counts as loaded.
        sdk.controller.clearRejectedIdentity(sdk.controller.currentGeneration());
        track("signup");
        track("checkout");

        for (int i = 0; i < 20; i++) {
            sdk.runNextPingTick();
        }

        assertTrue(pings().isEmpty());
        assertEquals(Arrays.asList("signup", "checkout"), queuedNames());
        assertEquals(GleapEventService.PING_INTERVAL_MS, sdk.pingScheduler.pendingDelay);
        assertEquals(0, service.getBackoff().failures());
    }

    @Test
    public void onlyOnePingIsInFlight() throws Exception {
        sdk.server.respond(PING, 200, "{}");
        sdk.holdPings = true;
        track("signup");
        service.start();
        sdk.runNextPingTick();
        assertEquals(1, sdk.heldPings.size());

        // The ping waits behind other requests; meanwhile the WebSocket reconnects and the loop
        // keeps ticking.
        service.start();
        for (int i = 0; i < 10; i++) {
            sdk.runNextPingTick();
            track("more-" + i);
        }
        assertEquals(1, sdk.heldPings.size());

        sdk.holdPings = false;
        sdk.heldPings.remove(0).run();
        assertEquals(1, pings().size());
        assertEquals(11, sentNames(pings().get(0)).size());

        // Once it is done, the next one goes out.
        track("after");
        sdk.runNextPingTick();
        assertEquals(2, pings().size());
        assertEquals(Arrays.asList("after"), sentNames(pings().get(1)));
        assertTrue(service.getEventQueue().isEmpty());
    }

    @Test
    public void failedPingsBackOffFrom3To60SecondsWithUpTo20PercentJitter() throws Exception {
        sdk.server.respond(PING, 429, "{\"error\":\"Too many requests\"}");
        track("signup");
        service.start();
        sdk.runNextPingTick();

        long[] expected = {3000, 6000, 12000, 24000, 48000, 60000, 60000};
        for (int i = 0; i < expected.length; i++) {
            assertEquals("after failure " + (i + 1), expected[i], sdk.pingScheduler.pendingDelay);
            sdk.runNextPingTick();
        }
        // The loop applies the jitter: -20 % at the lowest random value.
        sdk.pingRandom = 0;
        sdk.runNextPingTick();
        assertEquals(48000, sdk.pingScheduler.pendingDelay);

        assertEquals(expected.length + 2, pings().size());
        // Nothing was lost, and the body did not grow.
        for (FakeGleapServer.Request ping : pings()) {
            assertEquals(Arrays.asList("signup"), sentNames(ping));
        }

        // Jitter bounds: +-20 %, never above 60 s.
        assertEquals(2400, GleapPingBackoff.delay(1, 0));
        assertEquals(3600, GleapPingBackoff.delay(1, 0.999999999));
        assertEquals(38400, GleapPingBackoff.delay(5, 0));
        assertEquals(57600, GleapPingBackoff.delay(5, 0.999999999));
        assertEquals(48000, GleapPingBackoff.delay(6, 0));
        assertEquals(60000, GleapPingBackoff.delay(6, 0.999999999));
        assertEquals(60000, GleapPingBackoff.delay(1000, 0.5));
    }

    @Test
    public void retryAfterInSecondsIsHonoured() throws Exception {
        sdk.server.respondWithHeader(PING, 429, "{}", "Retry-After", "30");
        // Shorter than the backoff: the backoff applies.
        sdk.server.respondWithHeader(PING, 429, "{}", "Retry-After", "1");
        // Longer than 5 minutes: capped.
        sdk.server.respondWithHeader(PING, 503, "{}", "Retry-After", "3600");
        sdk.server.respondWithHeader(PING, 503, "{}", "Retry-After", "not a number");
        track("signup");
        service.start();
        sdk.runNextPingTick();

        assertEquals(30000, sdk.pingScheduler.pendingDelay);
        sdk.runNextPingTick();
        assertEquals(6000, sdk.pingScheduler.pendingDelay);
        sdk.runNextPingTick();
        assertEquals(GleapPingBackoff.MAX_RETRY_AFTER_MS, sdk.pingScheduler.pendingDelay);
        sdk.runNextPingTick();
        assertEquals(24000, sdk.pingScheduler.pendingDelay);
        assertEquals(4, pings().size());
    }

    @Test
    public void retryAfterAsAnHttpDateIsHonoured() throws Exception {
        sdk.server.respondWithHeader(PING, 429, "{}", "Retry-After", httpDate(sdk.pingClock.wall + 45000));
        // A date in the past: the backoff applies.
        sdk.server.respondWithHeader(PING, 429, "{}", "Retry-After", httpDate(sdk.pingClock.wall - 60000));
        track("signup");

        assertEquals(45000, service.sendQueuedEvents());
        assertEquals(6000, service.sendQueuedEvents());
    }

    @Test
    public void aDeliveredPingResetsTheBackoff() throws Exception {
        sdk.server.respond(PING, 429, "{}");
        sdk.server.respond(PING, 429, "{}");
        sdk.server.respond(PING, 204, "");
        sdk.server.respond(PING, 503, "{}");
        track("signup");
        service.start();
        sdk.runNextPingTick();
        assertEquals(3000, sdk.pingScheduler.pendingDelay);
        sdk.runNextPingTick();
        assertEquals(6000, sdk.pingScheduler.pendingDelay);
        sdk.runNextPingTick();

        // Delivered: back to the 3 s interval, the backoff starts over.
        assertTrue(service.getEventQueue().isEmpty());
        assertEquals(0, service.getBackoff().failures());
        assertEquals(GleapEventService.PING_INTERVAL_MS, sdk.pingScheduler.pendingDelay);

        track("checkout");
        sdk.runNextPingTick();
        assertEquals(3000, sdk.pingScheduler.pendingDelay);
        assertEquals(1, service.getBackoff().failures());
    }

    @Test
    public void every2xxAnswerDeliversTheEvents() throws Exception {
        for (int status : new int[]{201, 202, 204, 299}) {
            sdk.server.clear(PING).respond(PING, status, status == 204 ? "" : "{}");
            track("event-" + status);

            service.sendQueuedEvents();

            assertTrue("HTTP " + status, service.getEventQueue().isEmpty());
        }
        assertEquals(4, pings().size());
    }

    @Test
    public void aPingCarriesAtMost100EventsAndTheRestFollowsRightAway() throws Exception {
        sdk.server.respond(PING, 200, "{}").whileAnswering(PING, new Runnable() {
            @Override
            public void run() {
                track("late");
            }
        });
        for (int i = 0; i < 250; i++) {
            service.addEvent(event("event-" + i));
        }
        service.start();

        sdk.runNextPingTick();
        sdk.server.whileAnswering(PING, null);
        // Tracked while the first ping was in flight: still queued, behind the rest.
        List<String> rest = names("event-", 100, 250);
        rest.add("late");
        assertEquals(rest, queuedNames());
        assertEquals(0, sdk.pingScheduler.pendingDelay);
        sdk.runNextPingTick();
        assertEquals(0, sdk.pingScheduler.pendingDelay);
        sdk.runNextPingTick();
        assertEquals(3000, sdk.pingScheduler.pendingDelay);

        List<FakeGleapServer.Request> pings = pings();
        assertEquals(3, pings.size());
        assertEquals(names("event-", 0, 100), sentNames(pings.get(0)));
        assertEquals(names("event-", 100, 200), sentNames(pings.get(1)));
        assertEquals(rest.subList(100, 151), sentNames(pings.get(2)));
        assertTrue(service.getEventQueue().isEmpty());
    }

    @Test
    public void aPingCarriesAtMostAbout256KbOfEvents() throws Exception {
        sdk.server.respond(PING, 200, "{}");
        String blob = repeat('x', 40 * 1024);
        for (int i = 0; i < 20; i++) {
            service.addEvent(event("big-" + i).put("data", new JSONObject().put("blob", blob)));
        }
        service.start();

        while (!service.getEventQueue().isEmpty()) {
            sdk.runNextPingTick();
        }

        List<String> sent = new ArrayList<>();
        for (FakeGleapServer.Request ping : pings()) {
            JSONArray events = ping.bodyJson().getJSONArray("events");
            assertTrue(events.toString().length() <= GleapEventService.MAX_PING_BYTES);
            assertTrue(events.length() <= 6);
            sent.addAll(sentNames(ping));
        }
        assertEquals(4, pings().size());
        assertEquals(names("big-", 0, 20), sent);
    }

    @Test
    public void theQueueNeverGrowsPastTheLimitAndKeepsItsSessionStarts() throws Exception {
        for (int i = 0; i < GleapEventQueue.MAX_EVENTS; i++) {
            service.addEvent(event("event-" + i));
        }

        // A session start at the limit replaces the oldest events.
        service.sessionStarted();
        List<String> queued = queuedNames();
        assertEquals(GleapEventQueue.MAX_EVENTS, queued.size());
        assertEquals("event-2", queued.get(0));
        assertEquals("sessionStarted", queued.get(queued.size() - 2));
        assertEquals("pageView", queued.get(queued.size() - 1));

        // More session starts and events at the limit: it stays at the limit, keeping them.
        service.sessionStarted();
        service.sessionStarted();
        for (int i = 0; i < 10; i++) {
            service.addEvent(event("later-" + i));
        }
        queued = queuedNames();
        assertEquals(GleapEventQueue.MAX_EVENTS, queued.size());
        assertEquals(3, count(queued, "sessionStarted"));
        assertEquals("later-9", queued.get(queued.size() - 1));
    }

    // ---------------------------------------------------------------------------------------------

    private static void track(String name) {
        Gleap.getInstance().trackEvent(name);
    }

    private static JSONObject event(String name) throws Exception {
        return new JSONObject().put("name", name);
    }

    private List<FakeGleapServer.Request> pings() {
        return sdk.server.requestsTo(PING);
    }

    private static List<String> sentNames(FakeGleapServer.Request ping) throws Exception {
        JSONArray events = ping.bodyJson().getJSONArray("events");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < events.length(); i++) {
            names.add(events.getJSONObject(i).getString("name"));
        }
        return names;
    }

    private List<String> queuedNames() throws Exception {
        JSONArray events = service.getEventQueue().toJSONArray();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < events.length(); i++) {
            names.add(events.getJSONObject(i).getString("name"));
        }
        return names;
    }

    private static List<String> names(String prefix, int from, int to) {
        List<String> names = new ArrayList<>();
        for (int i = from; i < to; i++) {
            names.add(prefix + i);
        }
        return names;
    }

    private static int count(List<String> names, String name) {
        int count = 0;
        for (String each : names) {
            if (each.equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static String repeat(char c, int times) {
        char[] chars = new char[times];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String httpDate(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(millis));
    }
}
