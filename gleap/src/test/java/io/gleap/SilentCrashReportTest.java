package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Silent crash reports (sendSilentCrashReport): what they exclude and when they are sent.
 */
public class SilentCrashReportTest {
    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        sdk.server.respond("/uploads/sdk", 200, "{\"fileUrl\":\"https://files.example.com/s.png\"}");
        sdk.server.respond("/uploads/sdksteps", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/uploads/attachments", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/bugs/v2", 201, "{}");
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    @Test
    public void withoutExcludeDataTheScreenshotAndReplayAreLeftOut() throws Exception {
        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", Gleap.SEVERITY.HIGH, "CRASH", null);

        JSONObject exclude = GleapConfig.getInstance().getCrashStripModel();
        assertTrue(exclude.getBoolean("screenshot"));
        assertTrue(exclude.getBoolean("replay"));
        assertEquals(2, exclude.length());
    }

    @Test
    public void theAppsExcludeDataIsUsed() throws Exception {
        JSONObject exclude = new JSONObject().put("consoleLog", true);
        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", Gleap.SEVERITY.HIGH, "CRASH", exclude);

        assertTrue(GleapConfig.getInstance().getCrashStripModel().getBoolean("consoleLog"));
    }

    @Test
    public void withoutAScreenshotNoReportIsSent() {
        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", Gleap.SEVERITY.HIGH);

        assertTrue(sdk.server.requestsTo("/bugs/v2").isEmpty());
    }
}
