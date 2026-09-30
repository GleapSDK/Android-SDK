package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * clearIdentity (logout): nothing that was started for the previous user brings their session
 * or identity back.
 */
public class ClearIdentityTest {
    private SdkTestEnvironment sdk;

    private final Runnable logout = new Runnable() {
        @Override
        public void run() {
            Gleap.getInstance().clearIdentity();
        }
    };

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setGleapUserSession(new GleapSessionProperties("user-1"));
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    private void assertLoggedOut() {
        assertNull(sdk.controller.getUserSession());
        assertNull(sdk.controller.getGleapUserSession());
        assertTrue(sdk.store.values.toString(), sdk.store.values.isEmpty());
    }

    @Test
    public void aLogoutCancelsAPendingIdentify() throws Exception {
        sdk.controller.setPendingIdentificationAction(new GleapSessionProperties("user-2"));

        logout.run();
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-9\",\"gleapHash\":\"hash-9\"}");
        new GleapBaseSessionService().doInBackground();
        new GleapIdentifyService().doInBackground();

        assertNull(sdk.controller.getPendingIdentificationAction());
        assertTrue(sdk.server.requestsTo("/sessions/identify").isEmpty());
        assertEquals("id-9", sdk.controller.getUserSession().getId());
    }

    @Test
    public void anIdentifyAnsweredAfterTheLogoutIsDropped() {
        sdk.controller.setPendingIdentificationAction(new GleapSessionProperties("user-2"));
        sdk.server.respond("/sessions/identify", 200, "{\"gleapId\":\"id-2\",\"gleapHash\":\"hash-2\",\"userId\":\"user-2\"}")
                .whileAnswering("/sessions/identify", logout);

        new GleapIdentifyService().doInBackground();

        assertLoggedOut();
    }

    @Test
    public void anIdentifyThatFailedAcrossTheLogoutStaysCancelled() {
        sdk.controller.setPendingIdentificationAction(new GleapSessionProperties("user-2"));
        sdk.server.respond("/sessions/identify", 503, "{\"status\":\"overloaded\"}")
                .whileAnswering("/sessions/identify", logout);

        new GleapIdentifyService().doInBackground();

        assertNull(sdk.controller.getPendingIdentificationAction());
        assertLoggedOut();
    }

    @Test
    public void aRejectionAfterTheLogoutKeepsTheNextUsersIdentify() {
        final GleapSessionProperties nextUser = new GleapSessionProperties("user-3");
        sdk.controller.setPendingIdentificationAction(new GleapSessionProperties("user-2"));
        sdk.server.respond("/sessions/identify", 401, "{\"error\":{\"statusCode\":401}}")
                .whileAnswering("/sessions/identify", new Runnable() {
                    @Override
                    public void run() {
                        logout.run();
                        sdk.controller.setPendingIdentificationAction(nextUser);
                    }
                });

        new GleapIdentifyService().doInBackground();

        assertEquals(nextUser, sdk.controller.getPendingIdentificationAction());
    }

    @Test
    public void aContactUpdateAnsweredAfterTheLogoutIsDropped() {
        GleapSessionProperties update = new GleapSessionProperties();
        update.setPlan("pro");
        sdk.controller.setPendingUpdateAction(update);
        sdk.server.respond("/sessions/partialupdate", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\",\"userId\":\"user-1\",\"plan\":\"pro\"}")
                .whileAnswering("/sessions/partialupdate", logout);

        new GleapUpdateSessionService().doInBackground();

        assertLoggedOut();
    }

    @Test
    public void aSessionAnsweredAfterTheLogoutIsDropped() {
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\",\"userId\":\"user-1\"}")
                .whileAnswering("/sessions", logout);

        new GleapBaseSessionService().doInBackground();

        assertLoggedOut();
    }
}
