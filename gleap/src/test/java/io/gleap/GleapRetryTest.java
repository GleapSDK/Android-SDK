package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Retries of the config and session requests, and what is kept when they fail.
 */
public class GleapRetryTest {
    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    private static final OnHttpResponseListener NO_LISTENER = new OnHttpResponseListener() {
        @Override
        public void onTaskComplete(JSONObject response) {
        }
    };

    @Test
    public void theSessionRequestIsRetriedWithBackoff() {
        sdk.server.fail("/sessions", new IOException("offline"))
                .fail("/sessions", new IOException("offline"))
                .respond("/sessions", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\"}");

        new GleapBaseSessionService().doInBackground();

        assertEquals(3, sdk.server.requestsTo("/sessions").size());
        assertEquals(Arrays.asList(1000L, 2000L), sdk.sleeps);
        assertEquals("id-1", sdk.controller.getUserSession().getId());
    }

    @Test
    public void aFailedSessionStartReportsFailureAndKeepsTheStoredSession() {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.fail("/sessions", new IOException("offline"));
        final boolean[] loaded = {true};
        GleapBaseSessionService service = new GleapBaseSessionService(new GleapBaseSessionService.SessionLoadedCallback() {
            @Override
            public void invoke(boolean success) {
                loaded[0] = success;
            }
        });

        service.doInBackground();
        service.onPostExecute(200);

        assertEquals(3, sdk.server.requestsTo("/sessions").size());
        assertFalse(loaded[0]);
        assertTrue(sdk.controller.isSessionLoaded());
        assertEquals("id-1", sdk.controller.getUserSession().getId());
        assertEquals("hash-1", sdk.store.getString("session_hash", ""));
    }

    @Test
    public void anErrorStatusFromTheSessionStartDoesNotTouchTheStoredSession() {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/sessions", 503, "{\"status\":\"overloaded\"}");

        new GleapBaseSessionService().doInBackground();

        assertEquals("id-1", sdk.controller.getUserSession().getId());
        assertEquals("hash-1", sdk.store.getString("session_hash", ""));
    }

    @Test
    public void answersWithoutASessionDoNotTouchTheStoredSession() {
        for (String body : new String[]{"{}", "{\"status\":\"overloaded\"}",
                "{\"gleapId\":\"\",\"gleapHash\":\"\"}", "{\"gleapId\":null,\"gleapHash\":null}"}) {
            sdk.storeSession("id-1", "hash-1");
            sdk.server.clear("/sessions").respond("/sessions", 200, body);

            new GleapBaseSessionService().doInBackground();

            assertEquals(body, "id-1", sdk.controller.getUserSession().getId());
            assertEquals(body, "hash-1", sdk.store.getString("session_hash", ""));
        }
    }

    @Test
    public void aFailedContactUpdateDoesNotTouchTheStoredSession() {
        for (int status : new int[]{400, 500, 503}) {
            sdk.storeSession("id-1", "hash-1");
            GleapSessionProperties update = new GleapSessionProperties();
            update.setPlan("pro");
            sdk.controller.setPendingUpdateAction(update);
            sdk.server.clear("/sessions/partialupdate").respond("/sessions/partialupdate", status, "{\"status\":\"overloaded\"}");

            new GleapUpdateSessionService().doInBackground();

            assertEquals("HTTP " + status, "id-1", sdk.controller.getUserSession().getId());
            assertEquals("HTTP " + status, "hash-1", sdk.store.getString("session_hash", ""));
        }
    }

    @Test
    public void configConnectionFailuresAreRetried() {
        sdk.server.fail("/config/", new IOException("offline"));

        new ConfigLoader(NO_LISTENER).doInBackground();

        assertEquals(3, sdk.server.requestsTo("/config/").size());
        assertEquals(Arrays.asList(1000L, 2000L), sdk.sleeps);
        assertNull(GleapConfig.getInstance().getPlainConfig());
    }

    @Test
    public void aConfigServerErrorIsNotRetried() {
        sdk.server.respond("/config/", 500, "");

        new ConfigLoader(NO_LISTENER).doInBackground();

        assertEquals(1, sdk.server.requestsTo("/config/").size());
        assertNull(GleapConfig.getInstance().getPlainConfig());
    }

    /**
     * Runs an identify for user-1 on the stored session id-1 of user-0 against the answers
     * queued by {@code answers}.
     */
    private GleapSessionProperties identify(Runnable answers) {
        sdk.server.requests.clear();
        sdk.server.clear("/sessions/identify");
        answers.run();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setGleapUserSession(new GleapSessionProperties("user-0"));
        GleapSessionProperties identify = new GleapSessionProperties("user-1");
        sdk.controller.setPendingIdentificationAction(identify);

        new GleapIdentifyService().doInBackground();
        return identify;
    }

    @Test
    public void anIdentifyThatDoesNotGetThroughKeepsTheSessionAndStaysPending() {
        Object[][] failures = {
                {"offline", new IOException("offline")},
                {"timeout", new java.net.SocketTimeoutException("Read timed out")},
                {"overloaded", 503, "{\"status\":\"overloaded\"}"},
                {"server error", 500, ""},
                {"rate limited", 429, "Too Many Requests"},
                {"timeout status", 408, "{\"error\":\"timeout\"}"},
                {"4xx without a JSON body", 403, "<html>Blocked by proxy</html>"},
        };
        for (final Object[] failure : failures) {
            String name = (String) failure[0];
            GleapSessionProperties pending = identify(new Runnable() {
                @Override
                public void run() {
                    if (failure[1] instanceof IOException) {
                        sdk.server.fail("/sessions/identify", (IOException) failure[1]);
                    } else {
                        sdk.server.respond("/sessions/identify", (Integer) failure[1], (String) failure[2]);
                    }
                }
            });

            assertEquals(name, 3, sdk.server.requestsTo("/sessions/identify").size());
            assertEquals(name, "id-1", sdk.controller.getUserSession().getId());
            assertEquals(name, "hash-1", sdk.store.getString("session_hash", ""));
            assertEquals(name, "user-0", sdk.store.getString("userId", ""));
            assertEquals(name, "user-0", sdk.controller.getGleapUserSession().getUserId());
            assertEquals(name, pending, sdk.controller.getPendingIdentificationAction());
        }
    }

    @Test
    public void aPendingIdentifyGoesThroughOnItsNextRun() {
        identify(new Runnable() {
            @Override
            public void run() {
                sdk.server.fail("/sessions/identify", new IOException("offline"));
            }
        });
        sdk.server.clear("/sessions/identify")
                .respond("/sessions/identify", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-2\",\"userId\":\"user-1\"}");

        new GleapIdentifyService().doInBackground();

        assertEquals("user-1", sdk.controller.getGleapUserSession().getUserId());
        assertEquals("hash-2", sdk.store.getString("session_hash", ""));
        assertNull(sdk.controller.getPendingIdentificationAction());
    }

    @Test
    public void anIdentifyTheApiRejectsClearsTheSession() {
        identify(new Runnable() {
            @Override
            public void run() {
                sdk.server.respond("/sessions/identify", 401,
                        "{\"error\":{\"statusCode\":401,\"title\":\"Unauthorized\",\"message\":\"Not Authorized\"}}");
            }
        });

        assertEquals(1, sdk.server.requestsTo("/sessions/identify").size());
        assertNull(sdk.controller.getUserSession());
        assertNull(sdk.controller.getGleapUserSession());
        assertTrue(sdk.store.values.isEmpty());
        assertNull(sdk.controller.getPendingIdentificationAction());
        assertTrue(sdk.controller.isSessionLoaded());
    }
}
