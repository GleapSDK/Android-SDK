package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;

/**
 * Authenticated conversation files: the file session from a verified identify reaches the widget
 * but is never stored, stays with its session and user only, is fetched even for an unchanged
 * identify, is revoked on logout, and opens the conversation of an emailed file link.
 */
public class AuthenticatedFilesTest {
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";
    private static final String FILE_ID = "0123456789abcdef01234567";

    private SdkTestEnvironment sdk;
    private final String expiresAt = DateUtil.dateToString(new Date(System.currentTimeMillis() + 15 * 60 * 1000));

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        GleapConfig.getInstance().plainConfig = new JSONObject();
        GleapWidgetLauncher.screenshotTaker = new ScreenshotTaker() {
            @Override
            public void takeScreenshot() {
            }
        };
        GleapActionQueueHandler.getInstance().clearActionMessageQueue();
    }

    @After
    public void tearDown() {
        GleapWidgetLauncher.screenshotTaker = null;
        GleapActionQueueHandler.getInstance().clearActionMessageQueue();
        sdk.tearDown();
    }

    private String identifyAnswer(String gleapId, String userId) {
        return "{\"gleapId\":\"" + gleapId + "\",\"gleapHash\":\"hash-1\",\"userId\":\"" + userId + "\","
                + "\"authenticatedFilesRequired\":true,\"fileAccessToken\":\"" + TOKEN + "\","
                + "\"fileAccessExpiresAt\":\"" + expiresAt + "\"}";
    }

    private static String sessionAnswer(String gleapId, String userId) {
        return "{\"gleapId\":\"" + gleapId + "\",\"gleapHash\":\"hash-1\",\"userId\":\"" + userId + "\","
                + "\"authenticatedFilesRequired\":true}";
    }

    private void apply(String answer) throws Exception {
        sdk.controller.processSessionActionResult(new JSONObject(answer), false, false);
    }

    private static GleapSessionProperties verifiedUser() {
        return new GleapSessionProperties("user-1", null, null, "user-hash");
    }

    @Test
    public void theFileSessionReachesTheWidgetButIsNeverStored() throws Exception {
        sdk.server.respond("/sessions/identify", 201, identifyAnswer("id-1", "user-1"));
        Gleap.getInstance().identifyUser("user-1", verifiedUser());
        new GleapIdentifyService().doInBackground();

        JSONObject sessionData = GleapWidgetMessages.sessionUpdate().getJSONObject("sessionData");
        assertEquals(TOKEN, sessionData.getString("fileAccessToken"));
        assertEquals(expiresAt, sessionData.getString("fileAccessExpiresAt"));
        assertFalse(sdk.store.values.toString(), sdk.store.values.toString().contains(TOKEN));
    }

    @Test
    public void aSessionAnswerKeepsTheFileSessionOnlyForTheSameSessionAndUser() throws Exception {
        apply(identifyAnswer("id-1", "user-1"));
        apply(sessionAnswer("id-1", "user-1"));
        assertEquals(TOKEN, sdk.controller.getUserSession().getFileAccessToken());

        apply(sessionAnswer("id-1", "user-2"));
        assertNull(sdk.controller.getUserSession().getFileAccessToken());
        assertFalse(GleapWidgetMessages.sessionUpdate().getJSONObject("sessionData").has("fileAccessToken"));

        apply(identifyAnswer("id-1", "user-1"));
        apply(sessionAnswer("id-2", "user-1"));
        assertNull(sdk.controller.getUserSession().getFileAccessToken());
    }

    @Test
    public void anUnchangedIdentifyIsSentWhileTheFileSessionIsMissing() throws Exception {
        sdk.controller.setGleapUserSession(new GleapSessionProperties("user-1"));
        // Identified before the session start answered: the check uses the session it returns.
        Gleap.getInstance().identifyUser("user-1", verifiedUser());
        sdk.server.respond("/sessions", 201, sessionAnswer("id-1", "user-1"))
                .respond("/sessions/identify", 201, identifyAnswer("id-1", "user-1"));
        new GleapBaseSessionService().doInBackground();
        new GleapIdentifyService().doInBackground();

        List<FakeGleapServer.Request> identifies = sdk.server.requestsTo("/sessions/identify");
        assertEquals(1, identifies.size());
        assertEquals("user-hash", identifies.get(0).bodyJson().getString("userHash"));
        assertEquals(TOKEN, sdk.controller.getUserSession().getFileAccessToken());

        // With a valid file session an unchanged identify is skipped again.
        Gleap.getInstance().identifyUser("user-1", verifiedUser());
        new GleapIdentifyService().doInBackground();
        assertEquals(1, sdk.server.requestsTo("/sessions/identify").size());
    }

    @Test
    public void clearIdentityRevokesTheFileSession() throws Exception {
        apply(identifyAnswer("id-1", "user-1"));
        sdk.server.respond("/files/session/revoke", 204, "");

        Gleap.getInstance().clearIdentity();

        FakeGleapServer.Request revoke = sdk.server.last("/files/session/revoke");
        assertEquals("POST", revoke.getRequestMethod());
        assertEquals(TOKEN, revoke.headers.get("X-File-Session"));
        assertNull(revoke.headers.get("Api-Token"));
        assertNull(sdk.controller.getUserSession());
    }

    @Test
    public void anEmailedFileLinkOpensItsConversationOnceThereIsAFileSession() throws Exception {
        assertFalse(Gleap.getInstance().openProtectedFileFromUrl(null));
        assertFalse(Gleap.getInstance().openProtectedFileFromUrl("https://app.example.com/inbox"));
        assertFalse(Gleap.getInstance().openProtectedFileFromUrl("https://app.example.com/?gleapFile=0123456789ABCDEF01234567"));
        assertFalse(Gleap.getInstance().openProtectedFileFromUrl("https://app.example.com/?gleapFile=" + FILE_ID + "0"));
        assertFalse(Gleap.getInstance().openProtectedFileFromUrl("https://app.example.com/?gleapFile=..%2F" + FILE_ID));

        // No file session yet: the file waits.
        assertTrue(Gleap.getInstance().openProtectedFileFromUrl("https://app.example.com/inbox?tab=1&gleapFile=" + FILE_ID + "#top"));
        assertTrue(sdk.server.requestsTo("/files/").isEmpty());

        sdk.server.respond("/files/" + FILE_ID + "/location", 200, "{\"shareToken\":\"share-1\"}");
        apply(identifyAnswer("id-1", "user-1"));
        sdk.runMainThreadTasks();

        FakeGleapServer.Request location = sdk.server.last("/files/" + FILE_ID + "/location");
        assertEquals("GET", location.getRequestMethod());
        assertEquals(TOKEN, location.headers.get("X-File-Session"));
        assertNull(location.headers.get("Api-Token"));
        List<GleapAction> queue = GleapActionQueueHandler.getInstance().getActionQueue();
        assertEquals(1, queue.size());
        assertEquals("open-conversation", queue.get(0).getCommand());
        assertEquals("share-1", queue.get(0).getData().getString("shareToken"));

        // A file the user may not see opens nothing.
        GleapActionQueueHandler.getInstance().clearActionMessageQueue();
        String otherFile = "fedcba9876543210fedcba98";
        sdk.server.respond("/files/" + otherFile + "/location", 403, "");
        assertTrue(Gleap.getInstance().openProtectedFileFromUrl("https://app.example.com/?gleapFile=" + otherFile));
        sdk.runMainThreadTasks();
        assertEquals(1, sdk.server.requestsTo("/files/" + otherFile + "/location").size());
        assertTrue(GleapActionQueueHandler.getInstance().getActionQueue().isEmpty());
    }
}
