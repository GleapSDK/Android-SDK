package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.gleap.callbacks.GetActivityCallback;
import io.gleap.callbacks.InitializationDoneCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;

/**
 * The session id and hash the SDK authenticates with, the identified user and the push group
 * that follows the session.
 */
public class GleapSessionControllerTest {
    private final List<String> pushEvents = new ArrayList<>();
    private Activity currentActivity;
    private InMemoryKeyValueStore store;
    private GleapSessionController controller;

    @Before
    public void setUp() {
        GleapConfig.resetForTesting();
        GleapCallbacks.resetForTesting();
        GleapMainThread.setTestExecutor(Runnable::run);
        currentActivity = new Activity();
        GleapCallbacks.getInstance().setGetActivityCallback(new GetActivityCallback() {
            @Override
            public Activity getActivity() {
                return currentActivity;
            }
        });
        GleapCallbacks.getInstance().setRegisterPushMessageGroupCallback(new RegisterPushMessageGroupCallback() {
            @Override
            public void invoke(String pushMessageGroup) {
                pushEvents.add("register " + pushMessageGroup);
            }
        });
        GleapCallbacks.getInstance().setUnRegisterPushMessageGroupCallback(new UnRegisterPushMessageGroupCallback() {
            @Override
            public void invoke(String pushMessageGroup) {
                pushEvents.add("unregister " + pushMessageGroup);
            }
        });
        store = new InMemoryKeyValueStore();
        controller = new GleapSessionController(store);
        GleapSessionController.setInstanceForTesting(controller);
    }

    @After
    public void tearDown() {
        GleapMainThread.setTestExecutor(null);
        GleapSessionController.setInstanceForTesting(null);
        GleapCallbacks.resetForTesting();
        GleapConfig.resetForTesting();
    }

    private static JSONObject session(String id, String hash) throws Exception {
        return new JSONObject().put("gleapId", id).put("gleapHash", hash);
    }

    @Test
    public void restoresAStoredSessionOnlyWithBothIdAndHash() {
        store.putString("session_id", "id-1");
        store.putString("session_hash", "hash-1");
        GleapSession restored = new GleapSessionController(store).getUserSession();
        assertEquals("id-1", restored.getId());
        assertEquals("hash-1", restored.getHash());

        store.remove("session_hash");
        assertNull(new GleapSessionController(store).getUserSession());
    }

    @Test
    public void aSessionResultIsKeptAndPersisted() throws Exception {
        controller.processSessionActionResult(session("id-1", "hash-1")
                .put("userId", "user-1").put("name", "Ada").put("email", "ada@example.com"), false, false);

        assertEquals("id-1", controller.getUserSession().getId());
        assertEquals("hash-1", controller.getUserSession().getHash());
        assertTrue(controller.isSessionLoaded());
        assertEquals("id-1", store.getString("session_id", ""));
        assertEquals("hash-1", store.getString("session_hash", ""));
        assertEquals("user-1", controller.getGleapUserSession().getUserId());
        assertEquals("ada@example.com", store.getString("email", ""));
        assertEquals(Arrays.asList("register gleapuser-hash-1"), pushEvents);
    }

    @Test
    public void aNewHashLeavesThePreviousPushGroup() throws Exception {
        controller.processSessionActionResult(session("id-1", "hash-1"), false, false);
        controller.processSessionActionResult(session("id-1", "hash-1"), false, false);
        controller.processSessionActionResult(session("id-2", "hash-2"), false, false);

        assertEquals(Arrays.asList("register gleapuser-hash-1", "unregister gleapuser-hash-1",
                "register gleapuser-hash-2"), pushEvents);
        assertEquals("hash-2", store.getString("session_hash", ""));
    }

    @Test
    public void withoutAnActivityOnScreenThePushGroupStillFollowsTheSession() throws Exception {
        currentActivity = null;
        GleapCallbacks.getInstance().setInitializationDoneCallback(new InitializationDoneCallback() {
            @Override
            public void invoke() {
                pushEvents.add("initializationDone");
            }
        });

        controller.processSessionActionResult(session("id-1", "hash-1"), false, true);
        controller.processSessionActionResult(session("id-2", "hash-2"), false, false);
        controller.clearUserSession();

        assertEquals(Arrays.asList("register gleapuser-hash-1", "initializationDone",
                "unregister gleapuser-hash-1", "register gleapuser-hash-2", "unregister gleapuser-hash-2"), pushEvents);
    }

    @Test
    public void resultsWithoutIdAndHashChangeNothing() throws Exception {
        store.putString("session_id", "id-0");
        store.putString("session_hash", "hash-0");
        controller = new GleapSessionController(store);

        controller.processSessionActionResult(null, false, false);
        controller.processSessionActionResult(new JSONObject(), false, false);
        controller.processSessionActionResult(new JSONObject().put("gleapId", "id-9"), false, false);
        controller.processSessionActionResult(new JSONObject().put("gleapHash", "hash-9"), false, false);
        controller.processSessionActionResult(session("", ""), false, false);
        controller.processSessionActionResult(new JSONObject().put("gleapId", JSONObject.NULL).put("gleapHash", JSONObject.NULL), false, false);

        assertEquals("id-0", controller.getUserSession().getId());
        assertEquals("hash-0", controller.getUserSession().getHash());
        assertEquals("id-0", store.getString("session_id", ""));
        assertEquals("hash-0", store.getString("session_hash", ""));
        assertTrue(pushEvents.isEmpty());
    }

    @Test
    public void clearUserSessionDeletesTheSessionAndTheUser() throws Exception {
        controller.processSessionActionResult(session("id-1", "hash-1").put("userId", "user-1"), false, false);
        controller.setPendingUpdateAction(new GleapSessionProperties("user-1"));

        controller.clearUserSession();

        assertTrue(store.values.isEmpty());
        assertNull(controller.getUserSession());
        assertNull(controller.getGleapUserSession());
        assertFalse(controller.isSessionLoaded());
        assertNull(controller.getPendingUpdateAction());
        assertEquals("unregister gleapuser-hash-1", pushEvents.get(pushEvents.size() - 1));
    }

    @Test
    public void theIdentifiedUserSurvivesARestart() throws Exception {
        GleapSessionProperties user = new GleapSessionProperties("user-1", "Ada", "ada@example.com", "user-hash");
        user.setPhone("+43 1");
        user.setPlan("pro");
        user.setCompanyId("c-1");
        user.setCompanyName("Acme");
        user.setAvatar("https://example.com/a.png");
        user.setValue(2.5);
        user.setSla(3);
        user.setCustomData(new JSONObject().put("tier", "gold"));
        controller.setGleapUserSession(user);

        GleapSessionProperties restored = new GleapSessionController(store).getGleapUserSession();
        assertEquals("user-1", restored.getUserId());
        assertEquals("Ada", restored.getName());
        assertEquals("ada@example.com", restored.getEmail());
        assertEquals("user-hash", restored.getHash());
        assertEquals("+43 1", restored.getPhone());
        assertEquals("pro", restored.getPlan());
        assertEquals("c-1", restored.getCompanyId());
        assertEquals("Acme", restored.getCompanyName());
        assertEquals("https://example.com/a.png", restored.getAvatar());
        assertEquals(2.5, restored.getValue(), 0);
        assertEquals(3, restored.getSla(), 0);
        assertEquals("gold", restored.getCustomData().getString("tier"));
    }
}
