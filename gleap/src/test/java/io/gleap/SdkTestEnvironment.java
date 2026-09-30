package io.gleap;

import android.app.Activity;
import android.graphics.Bitmap;

import java.lang.reflect.Constructor;

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
    // The ping loop: its ticks wait here, its clock and jitter are set by the tests.
    final ManualPingScheduler pingScheduler = new ManualPingScheduler();
    final FakePingClock pingClock = new FakePingClock();
    // 0.5 is no jitter: the backoff delays are exactly 3, 6, 12... s.
    double pingRandom = 0.5;
    // Pings run right away on the test thread, unless held: then they wait here.
    boolean holdPings;
    final List<Runnable> heldPings = new ArrayList<>();

    SdkTestEnvironment() {
        GleapConfig.resetForTesting();
        GleapCallbacks.resetForTesting();
        GleapBug.resetForTesting();
        GleapEventService.resetForTesting();
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
        GleapEventService.setPingSchedulerForTesting(pingScheduler);
        GleapEventService.setPingClockForTesting(pingClock);
        GleapEventService.setPingRandomForTesting(new GleapEventService.PingRandom() {
            @Override
            public double next() {
                return pingRandom;
            }
        });
        GleapEventService.setPingExecutorForTesting(new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable ping) {
                if (holdPings) {
                    heldPings.add(ping);
                } else {
                    ping.run();
                }
            }
        });
        controller = new GleapSessionController(store);
        GleapSessionController.setInstanceForTesting(controller);
        // Tickets are sent right away, on the test thread.
        HttpHelper.setSenderForTesting(new HttpHelper.Sender() {
            @Override
            public void send(OnHttpResponseListener listener, android.content.Context context,
                             FeedbackSubmission submission) {
                HttpHelper task = new HttpHelper(listener, context, submission);
                task.onPreExecute();
                task.onPostExecute(task.doInBackground(GleapBug.getInstance()));
            }
        });
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
     * A screenshot for the tests. Bitmap has no public constructor; the stubbed one draws
     * nothing, which is all the uploads need here.
     */
    static Bitmap screenshot() {
        try {
            Constructor<Bitmap> constructor = Bitmap.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Runs the main-thread work posted so far (and what it posts in turn).
     */
    void runMainThreadTasks() {
        while (!mainThreadTasks.isEmpty()) {
            mainThreadTasks.remove(0).run();
        }
    }

    /**
     * Lets the ping loop's pending delay pass and runs its tick (a ping, when one is due).
     */
    void runNextPingTick() {
        if (pingScheduler.pending == null) {
            throw new AssertionError("The ping loop has no tick scheduled");
        }
        pingClock.advance(pingScheduler.pendingDelay);
        pingScheduler.runPending();
    }

    /**
     * Runs the ping loop's main-thread ticks (without a WebSocket; see GleapEventService.start).
     */
    static final class ManualPingScheduler implements GleapEventService.PingScheduler {
        Runnable pending;
        long pendingDelay = -1;

        @Override
        public void schedule(Runnable tick, long delayMs) {
            pending = tick;
            pendingDelay = delayMs;
        }

        @Override
        public void cancel() {
            pending = null;
            pendingDelay = -1;
        }

        void runPending() {
            Runnable tick = pending;
            pending = null;
            pendingDelay = -1;
            if (tick != null) {
                tick.run();
            }
        }
    }

    static final class FakePingClock implements GleapEventService.PingClock {
        long elapsed = 1000000;
        long wall = 1760000000000L;

        void advance(long millis) {
            elapsed += millis;
            wall += millis;
        }

        @Override
        public long elapsedRealtime() {
            return elapsed;
        }

        @Override
        public long currentTimeMillis() {
            return wall;
        }
    }

    void tearDown() {
        GleapMainThread.setTestExecutor(null);
        GleapEventService.setPingSchedulerForTesting(null);
        GleapEventService.setPingClockForTesting(null);
        GleapEventService.setPingRandomForTesting(null);
        GleapEventService.setPingExecutorForTesting(null);
        GleapHttp.setConnectionFactoryForTesting(null);
        GleapRetry.setSleeperForTesting(null);
        GleapEventService.setWebSocketFactoryForTesting(null);
        HttpHelper.setSenderForTesting(null);
        GleapSessionController.setInstanceForTesting(null);
        GleapCallbacks.resetForTesting();
        GleapConfig.resetForTesting();
        GleapBug.resetForTesting();
        GleapEventService.resetForTesting();
    }
}
