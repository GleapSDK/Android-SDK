package io.gleap;

import android.os.Bundle;

import androidx.test.runner.AndroidJUnitRunner;

/**
 * Installs the network guard before any test (or SDK) code runs, so no request can leave the
 * device during the e2e tests. The test APK's application is a plain {@link android.app.Application}:
 * nothing initializes Gleap with a real SDK key.
 */
public class E2ETestRunner extends AndroidJUnitRunner {
    @Override
    public void onCreate(Bundle arguments) {
        E2ENetworkGuard.install();
        super.onCreate(arguments);
    }
}
