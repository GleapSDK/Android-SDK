package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The JWT provider's rules: one call at a time, 10 s timeout, 30 s pause after a failure.
 */
public class GleapUserJwtProviderGateTest {
    static final class ManualScheduler implements GleapUserJwtProviderGate.Scheduler, GleapUserJwtProviderGate.Clock {
        long now = 1000000;
        final List<Runnable> posted = new ArrayList<>();
        final List<Runnable> delayed = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();

        @Override
        public void post(Runnable runnable) {
            posted.add(runnable);
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            delayed.add(runnable);
            delays.add(delayMs);
        }

        @Override
        public long elapsedMillis() {
            return now;
        }

        void runPosted() {
            while (!posted.isEmpty()) {
                posted.remove(0).run();
            }
        }

        /** Lets the timeouts pass. */
        void runDelayed() {
            List<Runnable> due = new ArrayList<>(delayed);
            delayed.clear();
            for (Runnable runnable : due) {
                runnable.run();
            }
        }
    }

    static final class Results implements GleapUserJwtProviderGate.TokenCallback {
        final List<String> tokens = new ArrayList<>();

        @Override
        public void onResult(String token) {
            tokens.add(token);
        }
    }

    private ManualScheduler scheduler;
    private GleapUserJwtProviderGate gate;
    private int calls;
    private GleapUserJwtProvider.Callback pending;
    private final String token = GleapUserJwtTest.jwt("{\"userId\":\"user-1\"}");

    @Before
    public void setUp() {
        scheduler = new ManualScheduler();
        gate = new GleapUserJwtProviderGate(scheduler, scheduler);
        gate.setProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                calls++;
                pending = callback;
            }
        });
    }

    @Test
    public void withoutAProviderTheResultIsNull() {
        GleapUserJwtProviderGate empty = new GleapUserJwtProviderGate(scheduler, scheduler);
        Results results = new Results();
        empty.requestToken(results);
        assertEquals(1, results.tokens.size());
        assertNull(results.tokens.get(0));
    }

    @Test
    public void oneCallAtATime() {
        Results first = new Results();
        Results second = new Results();
        gate.requestToken(first);
        gate.requestToken(second);
        scheduler.runPosted();
        assertEquals(1, calls);
        assertTrue(gate.isInFlight());

        pending.onToken(token);

        assertEquals(token, first.tokens.get(0));
        assertEquals(token, second.tokens.get(0));
        assertFalse(gate.isInFlight());
        // A late timeout changes nothing.
        scheduler.runDelayed();
        assertEquals(1, first.tokens.size());
    }

    @Test
    public void aTimeoutAfter10SecondsFailsAndPausesFor30Seconds() {
        Results first = new Results();
        gate.requestToken(first);
        scheduler.runPosted();
        assertEquals(Long.valueOf(GleapUserJwtProviderGate.TIMEOUT_MS), scheduler.delays.get(0));
        assertEquals(10000L, GleapUserJwtProviderGate.TIMEOUT_MS);

        scheduler.runDelayed();
        assertNull(first.tokens.get(0));
        // A late answer is dropped.
        pending.onToken(token);
        assertEquals(1, first.tokens.size());

        scheduler.now += 29000;
        Results second = new Results();
        gate.requestToken(second);
        scheduler.runPosted();
        assertNull(second.tokens.get(0));
        assertEquals(1, calls);

        scheduler.now += 1000;
        Results third = new Results();
        gate.requestToken(third);
        scheduler.runPosted();
        assertEquals(2, calls);
        pending.onToken(token);
        assertEquals(token, third.tokens.get(0));
    }

    @Test
    public void aResultThatIsNoJwtWithAUserIdFails() {
        Results first = new Results();
        gate.requestToken(first);
        scheduler.runPosted();
        pending.onToken(GleapUserJwtTest.jwt("{\"email\":\"ada@example.com\"}"));
        assertNull(first.tokens.get(0));

        Results second = new Results();
        gate.requestToken(second);
        assertNull(second.tokens.get(0));
        assertEquals(1, calls);
    }

    @Test
    public void aThrowingProviderFails() {
        gate.setProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                throw new IllegalStateException("offline");
            }
        });
        Results results = new Results();
        gate.requestToken(results);
        scheduler.runPosted();
        assertNull(results.tokens.get(0));
    }

    @Test
    public void aNewProviderIsAskedRightAwayAfterAFailure() {
        Results first = new Results();
        gate.requestToken(first);
        scheduler.runPosted();
        pending.onToken(null);

        gate.setProvider(new GleapUserJwtProvider() {
            @Override
            public void fetchToken(Callback callback) {
                callback.onToken(token);
            }
        });
        Results second = new Results();
        gate.requestToken(second);
        scheduler.runPosted();
        assertEquals(token, second.tokens.get(0));
    }
}
