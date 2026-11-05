package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Customer identity on the wire: setUserJwt sends the token as user hash and nothing else, a new
 * token is sent again, token refusals keep the session, an expired token goes to the JWT provider
 * and is retried once, and the auth tokens follow the signed session.
 */
public class CustomerIdentityTest {
    private static final String IDENTIFY_ANSWER = "{\"gleapId\":\"id-2\",\"gleapHash\":\"hash-2\",\"userId\":\"user-1\"}";

    private SdkTestEnvironment sdk;
    private GleapUserJwtProviderGateTest.ManualScheduler scheduler;
    private final List<Runnable> authTokenRequests = new ArrayList<>();

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        scheduler = new GleapUserJwtProviderGateTest.ManualScheduler();
        GleapUserJwtProviderGate.setInstanceForTesting(new GleapUserJwtProviderGate(scheduler, scheduler));
        GleapAuthTokens.resetForTesting();
        GleapAuthTokens.setExecutorForTesting(new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable command) {
                authTokenRequests.add(command);
            }
        });
    }

    @After
    public void tearDown() {
        GleapUserJwtProviderGate.setInstanceForTesting(null);
        GleapAuthTokens.resetForTesting();
        GleapAuthTokens.setExecutorForTesting(null);
        sdk.tearDown();
    }

    private void runAuthTokenRequests() {
        while (!authTokenRequests.isEmpty()) {
            authTokenRequests.remove(0).run();
        }
    }

    private static Set<String> keys(JSONObject object) {
        Set<String> keys = new TreeSet<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static String token(long expSeconds) {
        return GleapUserJwtTest.jwt("{\"userId\":\"user-1\",\"name\":\"Ada\",\"exp\":" + expSeconds + "}");
    }

    private static long inSeconds(long seconds) {
        return System.currentTimeMillis() / 1000 + seconds;
    }

    @Test
    public void setUserJwtSendsTheTokenAsUserHashAndNoContactFields() throws Exception {
        String token = token(inSeconds(3600));
        sdk.server.respond("/sessions/identify", 200, IDENTIFY_ANSWER);

        Gleap.getInstance().setUserJwt(token);
        new GleapIdentifyService().doInBackground();

        JSONObject body = sdk.server.last("/sessions/identify").bodyJson();
        assertEquals("user-1", body.getString("userId"));
        assertEquals(token, body.getString("userHash"));
        assertEquals(new TreeSet<>(java.util.Arrays.asList("userId", "userHash", "lang", "platform", "deviceType")), keys(body));
        assertEquals("id-2", sdk.controller.getUserSession().getId());
        // Memory only.
        assertFalse(sdk.store.values.toString().contains(token));
    }

    @Test
    public void anInvalidTokenIsRefusedWithoutARequest() {
        Gleap.getInstance().setUserJwt("not-a-jwt");
        Gleap.getInstance().setUserJwt(GleapUserJwtTest.jwt("{\"email\":\"ada@example.com\"}"));
        new GleapIdentifyService().doInBackground();

        assertTrue(sdk.server.requestsTo("/sessions/identify").isEmpty());
    }

    @Test
    public void theSameTokenIsSentOnceAndANewTokenAgain() throws Exception {
        String first = token(inSeconds(3600));
        String second = token(inSeconds(7200));
        sdk.server.respond("/sessions/identify", 200, IDENTIFY_ANSWER);

        Gleap.getInstance().setUserJwt(first);
        new GleapIdentifyService().doInBackground();
        Gleap.getInstance().setUserJwt(first);
        new GleapIdentifyService().doInBackground();
        assertEquals(1, sdk.server.requestsTo("/sessions/identify").size());

        Gleap.getInstance().setUserJwt(second);
        new GleapIdentifyService().doInBackground();
        assertEquals(2, sdk.server.requestsTo("/sessions/identify").size());
        assertEquals(second, sdk.server.last("/sessions/identify").bodyJson().getString("userHash"));
    }

    @Test
    public void anInvalidTokenKeepsTheSession() {
        sdk.server.respond("/sessions/identify", 401, "{\"name\":\"Unauthorized\",\"details\":{\"value\":\"token_invalid\"}}");

        Gleap.getInstance().setUserJwt(token(inSeconds(3600)));
        new GleapIdentifyService().doInBackground();

        assertEquals("id-1", sdk.controller.getUserSession().getId());
        assertEquals("id-1", sdk.store.getString("session_id", ""));
    }

    @Test
    public void signatureRequiredStillStartsOverAsAGuest() {
        sdk.server.respond("/sessions/identify", 401, "{\"name\":\"Unauthorized\",\"details\":{\"value\":\"signature_required\"}}");

        Gleap.getInstance().setUserJwt(token(inSeconds(3600)));
        new GleapIdentifyService().doInBackground();

        assertEquals(null, sdk.controller.getUserSession());
    }

    @Test
    public void anExpiredTokenAsksTheProviderAndRetriesOnce() throws Exception {
        final String expired = token(inSeconds(-60));
        final String fresh = token(inSeconds(3600));
        final int[] calls = {0};
        Gleap.getInstance().setUserJwtProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                calls[0]++;
                callback.onToken(fresh);
            }
        });
        sdk.server.respond("/sessions/identify", 401, "{\"details\":{\"value\":\"token_expired\"}}")
                .respond("/sessions/identify", 200, IDENTIFY_ANSWER);

        // An expiring token is renewed before it is sent; this one is sent as the app passed it.
        GleapSessionProperties identify = new GleapSessionProperties("user-1");
        identify.setHash(expired);
        identify.jwtOnly = true;
        identify.jwtFreshnessChecked = true;
        sdk.controller.setPendingIdentificationAction(identify);
        new GleapIdentifyService().doInBackground();
        assertEquals("id-1", sdk.controller.getUserSession().getId());

        scheduler.runPosted();
        new GleapIdentifyService().doInBackground();

        assertEquals(1, calls[0]);
        assertEquals(2, sdk.server.requestsTo("/sessions/identify").size());
        assertEquals(fresh, sdk.server.last("/sessions/identify").bodyJson().getString("userHash"));
        assertEquals("id-2", sdk.controller.getUserSession().getId());
    }

    @Test
    public void anExpiredRetryIsNotRetriedAgain() throws Exception {
        final String fresh = token(inSeconds(3600));
        final int[] calls = {0};
        Gleap.getInstance().setUserJwtProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                calls[0]++;
                callback.onToken(fresh);
            }
        });
        sdk.server.respond("/sessions/identify", 401, "{\"details\":{\"value\":\"token_expired\"}}");

        Gleap.getInstance().setUserJwt(token(inSeconds(-60)));
        new GleapIdentifyService().doInBackground();   // renews before sending
        scheduler.runPosted();
        new GleapIdentifyService().doInBackground();   // sends the fresh token: refused as expired
        scheduler.runPosted();
        new GleapIdentifyService().doInBackground();   // the one retry: refused again
        scheduler.runPosted();
        new GleapIdentifyService().doInBackground();

        assertEquals(2, calls[0]);
        assertEquals(2, sdk.server.requestsTo("/sessions/identify").size());
        assertEquals("id-1", sdk.controller.getUserSession().getId());
    }

    @Test
    public void aTokenAboutToExpireIsRenewedBeforeAFileSessionRefresh() throws Exception {
        final String fresh = token(inSeconds(3600));
        Gleap.getInstance().setUserJwtProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                callback.onToken(fresh);
            }
        });
        sdk.server.respond("/sessions/identify", 200, IDENTIFY_ANSWER);
        GleapSessionProperties identify = new GleapSessionProperties("user-1");
        identify.setHash(token(inSeconds(120)));
        identify.jwtOnly = true;
        sdk.controller.setPendingIdentificationAction(identify);
        sdk.controller.setPendingIdentificationAction(null);

        assertTrue(sdk.controller.requestFileAccessIdentify());
        new GleapIdentifyService().doInBackground();
        assertTrue(sdk.server.requestsTo("/sessions/identify").isEmpty());
        scheduler.runPosted();
        new GleapIdentifyService().doInBackground();

        assertEquals(fresh, sdk.server.last("/sessions/identify").bodyJson().getString("userHash"));
    }

    @Test
    public void setAuthTokensPostsTheTokensAndNullRemovesOne() throws Exception {
        sdk.server.respond("/sessions/auth-tokens", 200, "{\"names\":[\"backend\"]}");
        Map<String, String> tokens = new HashMap<>();
        tokens.put("backend", "secret-1");
        tokens.put("crm", null);
        tokens.put("bad name", "x");

        Gleap.getInstance().setAuthTokens(tokens);
        runAuthTokenRequests();

        FakeGleapServer.Request request = sdk.server.last("/sessions/auth-tokens");
        assertNotNull(request);
        assertEquals("id-1", request.getRequestProperty("Gleap-Id"));
        assertEquals("hash-1", request.getRequestProperty("Gleap-Hash"));
        assertEquals(SdkTestEnvironment.SDK_KEY, request.getRequestProperty("Api-Token"));
        JSONObject sent = request.bodyJson().getJSONObject("tokens");
        assertEquals("secret-1", sent.getString("backend"));
        assertTrue(sent.isNull("crm"));
        assertFalse(sent.has("bad name"));

        // Sent once per session.
        GleapAuthTokens.getInstance().sendIfNeeded();
        runAuthTokenRequests();
        assertEquals(1, sdk.server.requestsTo("/sessions/auth-tokens").size());
    }

    @Test
    public void authTokensAreResentAfterASignedIdentifyOnANewSession() throws Exception {
        sdk.server.respond("/sessions/auth-tokens", 401, "{\"details\":{\"value\":\"signature_required\"}}")
                .respond("/sessions/auth-tokens", 200, "{\"names\":[\"backend\"]}");
        sdk.server.respond("/sessions/identify", 200, IDENTIFY_ANSWER);
        Map<String, String> tokens = new HashMap<>();
        tokens.put("backend", "secret-1");

        Gleap.getInstance().setAuthTokens(tokens);
        runAuthTokenRequests();
        // The 401 keeps the session.
        assertEquals("id-1", sdk.controller.getUserSession().getId());

        Gleap.getInstance().setUserJwt(token(inSeconds(3600)));
        new GleapIdentifyService().doInBackground();
        runAuthTokenRequests();

        assertEquals(2, sdk.server.requestsTo("/sessions/auth-tokens").size());
        FakeGleapServer.Request resent = sdk.server.last("/sessions/auth-tokens");
        assertEquals("id-2", resent.getRequestProperty("Gleap-Id"));
        assertEquals("secret-1", resent.bodyJson().getJSONObject("tokens").getString("backend"));
    }

    @Test
    public void clearIdentityDropsTheTokensButKeepsTheProvider() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("backend", "secret-1");
        Gleap.getInstance().setAuthTokens(tokens);
        Gleap.getInstance().setUserJwtProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                callback.onToken(null);
            }
        });

        sdk.controller.clearUserSession();

        assertTrue(GleapAuthTokens.getInstance().tokensForTesting().isEmpty());
        assertTrue(GleapUserJwtProviderGate.getInstance().hasProvider());
    }
}
