package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.InitializedCallback;

/**
 * The config is loaded once per process: callbacks set later, and a second initialize, get the
 * loaded one (like iOS), never before it was loaded.
 */
public class ConfigCallbacksReplayTest {
    private SdkTestEnvironment sdk;
    private final List<String> events = new ArrayList<>();
    private final List<JSONObject> configs = new ArrayList<>();

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.server.respond("/config/", 200, "{\"flowConfig\":{\"color\":\"#123456\"}}");
    }

    @After
    public void tearDown() {
        GleapInitializer.setInitializedForTesting(false);
        sdk.tearDown();
    }

    private void register(final String name) {
        GleapCallbacks.getInstance().setConfigLoadedCallback(new ConfigLoadedCallback() {
            @Override
            public void configLoaded(JSONObject flowConfig) {
                configs.add(flowConfig);
                events.add(name + " configLoaded " + flowConfig.optString("color"));
            }
        });
        GleapCallbacks.getInstance().setInitializedCallback(new InitializedCallback() {
            @Override
            public void initialized() {
                events.add(name + " initialized");
            }
        });
    }

    private void loadConfig() {
        new ConfigLoader(new OnHttpResponseListener() {
            @Override
            public void onTaskComplete(JSONObject response) {
            }
        }).doInBackground();
        // Drop the overlay work the first load posts.
        sdk.mainThreadTasks.clear();
    }

    @Test
    public void callbacksSetAfterTheLoadGetItOnceOnTheMainThread() {
        register("early");
        assertTrue("nothing is replayed before the first load", sdk.mainThreadTasks.isEmpty());
        loadConfig();
        assertEquals("[early configLoaded #123456, early initialized]", events.toString());

        events.clear();
        register("late");
        assertTrue("never called inside the setter", events.isEmpty());
        sdk.runMainThreadTasks();
        assertEquals("[late configLoaded #123456, late initialized]", events.toString());
        assertSame(configs.get(0), configs.get(1));

        events.clear();
        sdk.runMainThreadTasks();
        GleapCallbacks.getInstance().setConfigLoadedCallback(null);
        GleapCallbacks.getInstance().setInitializedCallback(null);
        sdk.runMainThreadTasks();
        assertTrue(events.isEmpty());
    }

    @Test
    public void initializeAgainWithTheSameKeyReplaysOnce() {
        GleapInitializer.setInitializedForTesting(true);
        register("app");
        GleapInitializer.initialize(SdkTestEnvironment.SDK_KEY, null);
        assertTrue("nothing is replayed before the first load", sdk.mainThreadTasks.isEmpty());
        loadConfig();

        events.clear();
        GleapInitializer.initialize(SdkTestEnvironment.SDK_KEY, null);
        // Registering right after joins the pending replay instead of adding one.
        register("reloaded");
        sdk.runMainThreadTasks();
        assertEquals("[reloaded configLoaded #123456, reloaded initialized]", events.toString());

        events.clear();
        GleapInitializer.initialize("other-key", null);
        sdk.runMainThreadTasks();
        assertTrue(events.isEmpty());
    }
}
