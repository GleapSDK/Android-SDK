package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Signed mode restored by the JWT provider alone: after an app restart the SDK holds the session
 * (and maybe an expired or rejected grant) but not the token setUserJwt was given, which lives in
 * memory only. When the session answer says Signed mode without a grant for an identified
 * session, the provider is asked once per app start and its token is sent like a token from
 * setUserJwt.
 */
public class SignedModeProviderRestoreTest {
    private SdkTestEnvironment sdk;
    private GleapUserJwtProviderGateTest.ManualScheduler scheduler;
    private int providerCalls;
    private GleapUserJwtProvider.Callback pendingCallback;
    // What the provider answers right away; null leaves the call pending (see pendingCallback).
    private String providerToken;
    private boolean providerAnswersNull;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        scheduler = new GleapUserJwtProviderGateTest.ManualScheduler();
        GleapUserJwtProviderGate.setInstanceForTesting(new GleapUserJwtProviderGate(scheduler, scheduler));
    }

    @After
    public void tearDown() {
        GleapUserJwtProviderGate.setInstanceForTesting(null);
        sdk.tearDown();
    }

    private static long inSeconds(long seconds) {
        return System.currentTimeMillis() / 1000 + seconds;
    }

    private static String token(String userId) {
        return GleapUserJwtTest.jwt("{\"userId\":\"" + userId + "\",\"exp\":" + inSeconds(3600) + "}");
    }

    private static String grant(String sid) {
        return GleapUserJwtTest.jwt("{\"sid\":\"" + sid + "\",\"uid\":\"user-1\",\"kind\":\"jwt\",\"exp\":" + inSeconds(3600) + "}");
    }

    private static String signedWithoutGrant(String id) {
        return "{\"gleapId\":\"" + id + "\",\"gleapHash\":\"hash-" + id + "\",\"userId\":\"user-1\","
                + "\"identity\":{\"signed\":false,\"grant\":null,\"mode\":\"signed\"}}";
    }

    private static String signedAnswer(String id, String grant) {
        return "{\"gleapId\":\"" + id + "\",\"gleapHash\":\"hash-" + id + "\",\"userId\":\"user-1\","
                + "\"identity\":{\"signed\":true,\"grant\":\"" + grant + "\"}}";
    }

    /**
     * The previous app start identified user-1 with setUserJwt: the user is stored, the token is not.
     */
    private void restartAsIdentifiedUser() {
        sdk.store.putString("userId", "user-1");
        sdk.controller = new GleapSessionController(sdk.store);
        GleapSessionController.setInstanceForTesting(sdk.controller);
    }

    private void setProvider() {
        Gleap.getInstance().setUserJwtProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                providerCalls++;
                if (providerAnswersNull) {
                    callback.onToken(null);
                } else if (providerToken != null) {
                    callback.onToken(providerToken);
                } else {
                    pendingCallback = callback;
                }
            }
        });
    }

    /**
     * A session load: the session answer, the provider call it may post, and the identify it may queue.
     */
    private void loadSession() {
        new GleapBaseSessionService().doInBackground();
        scheduler.runPosted();
        new GleapIdentifyService().doInBackground();
    }

    @Test
    public void theProviderIsAskedOnceAndTheIdentifyCarriesItsToken() throws Exception {
        restartAsIdentifiedUser();
        providerToken = token("user-1");
        setProvider();
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));
        sdk.server.respond("/sessions/identify", 200, signedAnswer("id-1", grant("id-1")));

        loadSession();

        assertEquals(1, providerCalls);
        List<FakeGleapServer.Request> identifies = sdk.server.requestsTo("/sessions/identify");
        assertEquals(1, identifies.size());
        assertEquals(providerToken, identifies.get(0).bodyJson().getString("userHash"));
        assertNotNull(sdk.controller.getGrant("id-1"));
    }

    @Test
    public void repeatedUnsignedAnswersNeverLoop() {
        restartAsIdentifiedUser();
        providerToken = token("user-1");
        setProvider();
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));
        // The server keeps answering without a grant.
        sdk.server.respond("/sessions/identify", 200, signedWithoutGrant("id-1"));

        loadSession();
        loadSession();
        loadSession();

        assertEquals(1, providerCalls);
        assertEquals(1, sdk.server.requestsTo("/sessions/identify").size());
    }

    @Test
    public void aNullTokenSendsNothingAndIsNotAskedAgain() {
        restartAsIdentifiedUser();
        providerAnswersNull = true;
        setProvider();
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));

        loadSession();
        scheduler.now += GleapUserJwtProviderGate.PAUSE_AFTER_FAILURE_MS + 1;
        loadSession();

        assertEquals(1, providerCalls);
        assertEquals(0, sdk.server.requestsTo("/sessions/identify").size());
        assertNull(sdk.controller.getPendingIdentificationAction());
    }

    @Test
    public void withoutAProviderNothingIsSent() {
        restartAsIdentifiedUser();
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));

        loadSession();

        assertEquals(0, sdk.server.requestsTo("/sessions/identify").size());
    }

    @Test
    public void aProviderSetAfterTheSessionAnswerIsAsked() throws Exception {
        restartAsIdentifiedUser();
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));
        sdk.server.respond("/sessions/identify", 200, signedAnswer("id-1", grant("id-1")));
        loadSession();
        assertEquals(0, sdk.server.requestsTo("/sessions/identify").size());

        providerToken = token("user-1");
        setProvider();
        scheduler.runPosted();
        new GleapIdentifyService().doInBackground();

        assertEquals(1, providerCalls);
        assertEquals(providerToken, sdk.server.last("/sessions/identify").bodyJson().getString("userHash"));
    }

    @Test
    public void aClassicAnswerAsksNothing() {
        restartAsIdentifiedUser();
        providerToken = token("user-1");
        setProvider();
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-id-1\",\"userId\":\"user-1\"}");

        loadSession();

        assertEquals(0, providerCalls);
        assertEquals(0, sdk.server.requestsTo("/sessions/identify").size());
    }

    @Test
    public void anAnonymousSessionAsksNothing() {
        providerToken = token("user-1");
        setProvider();
        sdk.server.respond("/sessions", 200,
                "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-id-1\",\"identity\":{\"signed\":false,\"grant\":null,\"mode\":\"signed\"}}");

        loadSession();

        assertEquals(0, providerCalls);
    }

    @Test
    public void aTokenFromTheAppIsSentInsteadOfAskingTheProvider() throws Exception {
        restartAsIdentifiedUser();
        providerToken = token("user-1");
        setProvider();
        String appToken = token("user-1");
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));
        sdk.server.respond("/sessions/identify", 200, signedAnswer("id-1", grant("id-1")));

        Gleap.getInstance().setUserJwt(appToken);
        loadSession();

        assertEquals(0, providerCalls);
        assertEquals(1, sdk.server.requestsTo("/sessions/identify").size());
        assertEquals(appToken, sdk.server.last("/sessions/identify").bodyJson().getString("userHash"));
    }

    @Test
    public void aLegacyHashKeptFromTheLastStartIsSentInsteadOfAskingTheProvider() throws Exception {
        sdk.store.putString("hash", "legacy-hash");
        restartAsIdentifiedUser();
        providerToken = token("user-1");
        setProvider();
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));
        sdk.server.respond("/sessions/identify", 200, signedAnswer("id-1", grant("id-1")));

        loadSession();

        assertEquals(0, providerCalls);
        assertEquals("legacy-hash", sdk.server.last("/sessions/identify").bodyJson().getString("userHash"));
    }

    @Test
    public void aLogoutWhileTheProviderRunsDropsItsToken() {
        restartAsIdentifiedUser();
        setProvider();
        sdk.server.respond("/sessions", 200, signedWithoutGrant("id-1"));

        loadSession();
        assertEquals(1, providerCalls);
        assertNotNull(pendingCallback);

        sdk.controller.clearUserSession();
        pendingCallback.onToken(token("user-1"));
        new GleapIdentifyService().doInBackground();

        assertNull(sdk.controller.getPendingIdentificationAction());
        assertEquals(0, sdk.server.requestsTo("/sessions/identify").size());
    }
}
