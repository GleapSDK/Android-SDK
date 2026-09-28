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

    @Test
    public void aFailedIdentifyClearsTheStoredSession() {
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setPendingIdentificationAction(new GleapSessionProperties("user-1"));
        sdk.server.respond("/sessions/identify", 500, "");

        new GleapIdentifyService().doInBackground();

        assertEquals(3, sdk.server.requestsTo("/sessions/identify").size());
        assertNull(sdk.controller.getUserSession());
        assertTrue(sdk.store.values.isEmpty());
    }
}
