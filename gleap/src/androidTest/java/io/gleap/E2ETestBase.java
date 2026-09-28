package io.gleap;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;

import java.util.List;

/**
 * Every e2e test: the SDK runs against the fake server, state from other tests is reset, and
 * afterwards no host but the device itself may have been contacted.
 */
public abstract class E2ETestBase {
    private int externalAttemptsBefore;

    @Before
    public void startE2EEnvironment() {
        E2EEnvironment.start();
        E2EEnvironment.resetBetweenTests();
        externalAttemptsBefore = E2ENetworkGuard.externalAttempts().size();
    }

    @After
    public void nothingLeftTheDevice() {
        List<String> attempts = E2ENetworkGuard.externalAttempts();
        List<String> during = attempts.subList(Math.min(externalAttemptsBefore, attempts.size()), attempts.size());
        assertTrue("Requests to hosts other than the fake server on the device: " + during, during.isEmpty());
    }
}
