package io.gleap;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

import io.gleap.callbacks.GetActivityCallback;

/**
 * Runs the SDK's core code under JUnit: a fake API server, recorded retry delays, queued main-thread
 * work, no real WebSocket and an in-memory session store.
 */
class SdkTestEnvironment {
    static final String SDK_KEY = "sdk-key";

    final FakeGleapServer server = new FakeGleapServer();
    final List<Long> sleeps = new ArrayList<>();
    final List<String> webSocketConnects = new ArrayList<>();
    // Work the SDK posted to the main thread; run it with runMainThreadTasks().
    final List<Runnable> mainThreadTasks = new ArrayList<>();
    final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    Activity currentActivity = new Activity();
    GleapSessionController controller;

    SdkTestEnvironment() {
        GleapConfig.resetForTesting();
        GleapCallbacks.resetForTesting();
        GleapConfig.getInstance().setSdkKey(SDK_KEY);
        GleapConfig.getInstance().setLanguage("en");
        GleapConfig.getInstance().setEnableConsoleLogsFromCode(false);
        GleapMainThread.setTestExecutor(new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable command) {
                mainThreadTasks.add(command);
            }
        });
        GleapHttp.setConnectionFactoryForTesting(server);
        GleapRetry.setSleeperForTesting(new GleapRetry.Sleeper() {
            @Override
            public void sleep(long millis) {
                sleeps.add(millis);
            }
        });
        GleapEventService.setWebSocketFactoryForTesting(new GleapEventService.WebSocketFactory() {
            @Override
            public GleapWebSocketListener create() {
                return new GleapWebSocketListener() {
                    @Override
                    public boolean connect() {
                        webSocketConnects.add(GleapConfig.getInstance().getWsApiUrl());
                        return true;
                    }

                    @Override
                    public void destroy() {
                    }
                };
            }
        });
        GleapCallbacks.getInstance().setGetActivityCallback(new GetActivityCallback() {
            @Override
            public Activity getActivity() {
                return currentActivity;
            }
        });
        controller = new GleapSessionController(store);
        GleapSessionController.setInstanceForTesting(controller);
    }

    /**
     * Stores a session, as a previous app start would have.
     */
    void storeSession(String id, String hash) {
        store.putString("session_id", id);
        store.putString("session_hash", hash);
        controller = new GleapSessionController(store);
        GleapSessionController.setInstanceForTesting(controller);
    }

    /**
     * Runs the main-thread work posted so far (and what it posts in turn).
     */
    void runMainThreadTasks() {
        while (!mainThreadTasks.isEmpty()) {
            mainThreadTasks.remove(0).run();
        }
    }

    void tearDown() {
        GleapMainThread.setTestExecutor(null);
        GleapHttp.setConnectionFactoryForTesting(null);
        GleapRetry.setSleeperForTesting(null);
        GleapEventService.setWebSocketFactoryForTesting(null);
        GleapSessionController.setInstanceForTesting(null);
        GleapCallbacks.resetForTesting();
        GleapConfig.resetForTesting();
        GleapBug.resetForTesting();
    }
}
