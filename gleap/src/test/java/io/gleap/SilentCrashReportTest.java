package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        GleapBug.getInstance().setCustomData("plan", "pro");
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    @Test
    public void byDefaultTheReportIsSentRightAwayWithoutScreenshotAndReplay() throws Exception {
        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", Gleap.SEVERITY.HIGH, "CRASH", null);

        JSONObject body = sdk.server.last("/bugs/v2").bodyJson();
        assertEquals("CRASH", body.getString("type"));
        assertEquals("true", body.getString("isSilent"));
        assertEquals("HIGH", body.getString("priority"));
        assertEquals("Checkout failed", body.getJSONObject("formData").getString("description"));
        assertFalse(body.has("screenshotUrl"));
        assertFalse(body.has("replay"));
        assertTrue(sdk.server.requestsTo("/uploads/sdk").isEmpty());
        assertTrue(sdk.server.requestsTo("/uploads/sdksteps").isEmpty());
        assertEquals("pro", body.getJSONObject("customData").getString("plan"));
        assertTrue(body.has("networkLogs"));
    }

    @Test
    public void theAppsExcludeDataIsUsed() throws Exception {
        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", Gleap.SEVERITY.HIGH,
                new JSONObject().put("screenshot", true).put("customData", true));

        JSONObject body = sdk.server.last("/bugs/v2").bodyJson();
        assertFalse(body.has("customData"));
        assertTrue(body.has("networkLogs"));
    }

    @Test
    public void aScreenshotThatCannotBeTakenDoesNotStopTheReport() throws Exception {
        // The screenshot is not excluded, but the test activity has no window to capture.
        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", null, new JSONObject().put("consoleLog", true));

        JSONObject body = sdk.server.last("/bugs/v2").bodyJson();
        assertEquals("LOW", body.getString("priority"));
        assertFalse(body.has("screenshotUrl"));
        assertTrue(sdk.server.requestsTo("/uploads/sdk").isEmpty());
    }
}
