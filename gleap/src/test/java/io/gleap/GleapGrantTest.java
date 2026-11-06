package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * The grant a signed identify returns ({@code identity.grant}): sent as Gleap-Grant with the
 * session it was issued to, kept across app starts, handed to the widget, dropped on logout and
 * by an unsigned identify; {@code identity_mismatch} restarts as a fresh visitor and identifies
 * once more.
 */
public class GleapGrantTest {
    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        GleapUserJwtProviderGate.setInstanceForTesting(null);
    }

    @After
    public void tearDown() {
        GleapConfig.getInstance().setSessionDuration(0);
        sdk.tearDown();
    }

    private static long inSeconds(long seconds) {
        return System.currentTimeMillis() / 1000 + seconds;
    }

    private static String token() {
        return GleapUserJwtTest.jwt("{\"userId\":\"user-1\",\"exp\":" + inSeconds(3600) + "}");
    }

    private static String grant(String sid) {
        return GleapUserJwtTest.jwt("{\"sid\":\"" + sid + "\",\"uid\":\"user-1\",\"kind\":\"jwt\",\"exp\":" + inSeconds(3600) + "}");
    }

    private static String signedAnswer(String id, String grant) {
        return "{\"gleapId\":\"" + id + "\",\"gleapHash\":\"hash-" + id + "\",\"userId\":\"user-1\","
                + "\"identity\":{\"signed\":" + (grant != null) + ",\"grant\":" + (grant != null ? "\"" + grant + "\"" : "null") + "}}";
    }

    private void signIn(String grant) {
        sdk.server.respond("/sessions/identify", 200, signedAnswer("id-2", grant));
        Gleap.getInstance().setUserJwt(token());
        new GleapIdentifyService().doInBackground();
    }

    @Test
    public void theGrantRidesOnTheSessionsRequests() throws Exception {
        String issued = grant("id-2");
        signIn(issued);

        assertNull("the guest had no grant", sdk.server.last("/sessions/identify").getRequestProperty("Gleap-Grant"));
        assertEquals(issued, sdk.controller.getGrant("id-2"));
        assertNull("never with another session", sdk.controller.getGrant("id-1"));

        sdk.server.respond("/sessions/partialupdate", 200, "{\"gleapId\":\"id-2\",\"gleapHash\":\"hash-id-2\",\"userId\":\"user-1\"}");
        GleapSessionProperties update = new GleapSessionProperties();
        update.setName("Ada");
        sdk.controller.setPendingUpdateAction(update);
        new GleapUpdateSessionService().doInBackground();
        assertEquals(issued, sdk.server.last("/sessions/partialupdate").getRequestProperty("Gleap-Grant"));
        assertEquals("an answer without identity keeps it", issued, sdk.controller.getGrant("id-2"));

        assertEquals(issued, GleapWidgetMessages.sessionUpdate().getString("grant"));
    }

    @Test
    public void theGrantSurvivesAnAppStartAndLeavesWithClearIdentity() {
        String issued = grant("id-2");
        signIn(issued);

        GleapSessionController restarted = new GleapSessionController(sdk.store);
        assertEquals(issued, restarted.getGrant("id-2"));

        sdk.controller.clearUserSession();
        assertNull(sdk.controller.getGrant("id-2"));
        assertNull(new GleapSessionController(sdk.store).getGrant("id-2"));
    }

    @Test
    public void anUnsignedIdentifyDropsTheGrant() {
        signIn(grant("id-2"));

        sdk.server.clear("/sessions/identify").respond("/sessions/identify", 200, signedAnswer("id-2", null));
        // Another user, identified without a token or hash.
        GleapSessionProperties unsigned = new GleapSessionProperties("user-2");
        sdk.controller.setPendingIdentificationAction(unsigned);
        new GleapIdentifyService().doInBackground();

        assertNull(sdk.controller.getGrant("id-2"));
        assertEquals("", sdk.store.getString("grant", ""));
    }

    @Test
    public void identityMismatchRestartsAsAVisitorAndIdentifiesOnce() throws Exception {
        String token = token();
        sdk.server.respond("/sessions/identify", 400, "{\"name\":\"BadRequest\",\"details\":{\"value\":\"identity_mismatch\"}}");
        Gleap.getInstance().setUserJwt(token);
        new GleapIdentifyService().doInBackground();

        assertNull(sdk.controller.getUserSession());
        assertEquals("", sdk.store.getString("session_id", ""));

        sdk.server.clear("/sessions/identify").respond("/sessions/identify", 200, signedAnswer("id-2", grant("id-2")));
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-fresh\",\"gleapHash\":\"hash-fresh\"}");
        new GleapBaseSessionService().doInBackground();
        assertNull("a fresh visitor", sdk.server.last("/sessions").getRequestProperty("Gleap-Id"));
        new GleapIdentifyService().doInBackground();

        List<FakeGleapServer.Request> identifies = sdk.server.requestsTo("/sessions/identify");
        assertEquals(2, identifies.size());
        assertEquals("id-1", identifies.get(0).getRequestProperty("Gleap-Id"));
        assertEquals("id-fresh", identifies.get(1).getRequestProperty("Gleap-Id"));
        assertEquals(token, identifies.get(1).bodyJson().getString("userHash"));
        assertEquals("id-2", sdk.controller.getUserSession().getId());
        assertTrue(sdk.controller.getGrant("id-2") != null);
    }

    @Test
    public void sessionDurationIsSentWithTheIdentify() throws Exception {
        GleapConfig.getInstance().setSessionDuration(3_600_000L);
        signIn(grant("id-2"));

        JSONObject body = sdk.server.last("/sessions/identify").bodyJson();
        assertEquals(3_600_000L, body.getLong("sessionDuration"));
    }
}
