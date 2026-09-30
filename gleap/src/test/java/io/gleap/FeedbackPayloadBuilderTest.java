package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * What a ticket sends, and what it leaves out when the widget action or a silent crash report
 * excludes data.
 */
public class FeedbackPayloadBuilderTest {
    private static final class FakeUploads implements FeedbackPayloadBuilder.Uploads {
        int screenshots;
        int replays;
        int attachments;

        @Override
        public JSONObject uploadScreenshot(Bitmap screenshot) throws JSONException {
            screenshots++;
            return new JSONObject().put("fileUrl", "https://files.example.com/screenshot.png");
        }

        @Override
        public JSONObject uploadReplay() throws JSONException {
            replays++;
            return new JSONObject().put("interval", 5000).put("frames", new JSONArray());
        }

        @Override
        public JSONArray uploadAttachments() {
            attachments++;
            try {
                return new JSONArray().put(new JSONObject().put("url", "https://files.example.com/log.txt").put("name", "log.txt"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class FakeData implements FeedbackPayloadBuilder.ReportData {
        int consoleLogReads;

        @Override
        public JSONArray networkLogs() {
            return new JSONArray().put("GET https://api.example.com/items");
        }

        @Override
        public JSONObject metaData() throws JSONException {
            return new JSONObject().put("deviceName", "Pixel 9");
        }

        @Override
        public JSONArray consoleLogs() {
            consoleLogReads++;
            return new JSONArray().put("E MainActivity: boom");
        }
    }

    private final FakeUploads uploads = new FakeUploads();
    private final FakeData data = new FakeData();

    private static FeedbackSubmission report(String outboundId, boolean silent, JSONObject exclude, JSONObject crashExclude)
            throws JSONException {
        return report(outboundId, silent, SdkTestEnvironment.screenshot(), exclude, crashExclude);
    }

    private static FeedbackSubmission report(String outboundId, boolean silent, Bitmap screenshot, JSONObject exclude,
                                             JSONObject crashExclude) throws JSONException {
        return new FeedbackSubmission("BUG", outboundId, "spam-1",
                new JSONObject().put("description", "It crashed"),
                new JSONObject().put("plan", "pro"),
                new JSONArray().put(new JSONObject().put("name", "checkout")),
                new String[]{"android", "beta"}, "HIGH", silent, screenshot, exclude, crashExclude);
    }

    private JSONObject build(FeedbackSubmission report) throws Exception {
        return FeedbackPayloadBuilder.build(report, true, uploads, data);
    }

    @Test
    public void theBodyCarriesTheWholeTicket() throws Exception {
        JSONObject body = build(report("survey-1", false, new JSONObject(), new JSONObject()));

        assertEquals("survey-1", body.getString("outbound"));
        assertEquals("spam-1", body.getString("spamToken"));
        assertEquals("https://files.example.com/screenshot.png", body.getString("screenshotUrl"));
        assertEquals(5000, body.getJSONObject("replay").getInt("interval"));
        assertEquals("BUG", body.getString("type"));
        assertEquals("log.txt", body.getJSONArray("attachments").getJSONObject(0).getString("name"));
        assertEquals("It crashed", body.getJSONObject("formData").getString("description"));
        assertEquals(1, body.getJSONArray("networkLogs").length());
        assertEquals("checkout", body.getJSONArray("customEventLog").getJSONObject(0).getString("name"));
        assertEquals("false", body.getString("isSilent"));
        assertEquals("Pixel 9", body.getJSONObject("metaData").getString("deviceName"));
        assertEquals("pro", body.getJSONObject("customData").getString("plan"));
        assertEquals("HIGH", body.getString("priority"));
        assertEquals("beta", body.getJSONArray("tags").getString(1));
        assertEquals(1, body.getJSONArray("consoleLog").length());
    }

    @Test
    public void withoutAnOutboundTheTicketIsABugReport() throws Exception {
        assertEquals("bugreporting", build(report(null, false, new JSONObject(), new JSONObject())).getString("outbound"));
        assertEquals("bugreporting", build(report("", false, new JSONObject(), new JSONObject())).getString("outbound"));
    }

    @Test
    public void silentReportsAreMarked() throws Exception {
        assertEquals("true", build(report(null, true, new JSONObject(), new JSONObject())).getString("isSilent"));
    }

    @Test
    public void dataTheWidgetExcludesIsLeftOut() throws Exception {
        JSONObject exclude = new JSONObject()
                .put("consoleLog", true).put("networkLogs", true).put("customData", true)
                .put("metaData", true).put("customEventLog", true).put("formData", false);

        JSONObject body = build(report(null, false, exclude, new JSONObject()));

        assertFalse(body.has("consoleLog"));
        assertFalse(body.has("networkLogs"));
        assertFalse(body.has("customData"));
        assertFalse(body.has("metaData"));
        assertFalse(body.has("customEventLog"));
        assertTrue(body.has("formData"));
    }

    @Test
    public void anExcludedScreenshotIsNeitherUploadedNorSent() throws Exception {
        JSONObject body = build(report(null, false, new JSONObject().put("screenshot", true), new JSONObject()));

        assertEquals(0, uploads.screenshots);
        assertEquals(0, uploads.replays);
        assertFalse(body.has("screenshotUrl"));
        assertFalse(body.has("replay"));
    }

    @Test
    public void theCrashReportDecidesAboutTheScreenshot() throws Exception {
        build(report(null, true, new JSONObject().put("screenshot", true), new JSONObject().put("screenshot", false)));
        assertEquals(1, uploads.screenshots);

        JSONObject body = build(report(null, true, new JSONObject(), new JSONObject().put("screenshot", true)));
        assertEquals(1, uploads.screenshots);
        assertFalse(body.has("screenshotUrl"));
    }

    @Test
    public void aTicketWithoutAScreenshotIsSentWithoutOne() throws Exception {
        JSONObject body = build(report(null, false, null, new JSONObject(), new JSONObject()));

        assertEquals(0, uploads.screenshots);
        assertFalse(body.has("screenshotUrl"));
        assertEquals("It crashed", body.getJSONObject("formData").getString("description"));
    }

    @Test
    public void excludedAttachmentsAreNotUploaded() throws Exception {
        JSONObject body = build(report(null, false, new JSONObject().put("attachments", true), new JSONObject()));

        assertEquals(0, uploads.attachments);
        assertFalse(body.has("attachments"));
    }

    @Test
    public void consoleLogsAreOnlyReadWhenTheRemoteConfigEnablesThem() throws Exception {
        JSONObject body = FeedbackPayloadBuilder.build(report(null, false, new JSONObject(), new JSONObject()), false, uploads, data);

        assertFalse(body.has("consoleLog"));
        assertEquals(0, data.consoleLogReads);
    }

    @Test
    public void aSilentCrashReportLeavesOutEverythingItExcludes() throws Exception {
        JSONObject body = build(report(null, true, new JSONObject(), new JSONObject()
                .put("consoleLog", true).put("networkLogs", true).put("customData", true)
                .put("metaData", true).put("customEventLog", true).put("attachments", true)));

        assertFalse(body.has("consoleLog"));
        assertFalse(body.has("networkLogs"));
        assertFalse(body.has("customData"));
        assertFalse(body.has("metaData"));
        assertFalse(body.has("customEventLog"));
        assertFalse(body.has("attachments"));
        assertEquals(0, uploads.attachments);
        assertTrue(body.has("screenshotUrl"));
        assertTrue(body.has("formData"));
    }

    @Test
    public void theCrashReportDecidesWhenBothExcludeTheSameData() throws Exception {
        JSONObject body = build(report(null, true, new JSONObject().put("consoleLog", true),
                new JSONObject().put("consoleLog", false)));

        assertTrue(body.has("consoleLog"));
    }

    @Test
    public void anExcludedReplayIsNeitherUploadedNorSentUnderEitherName() throws Exception {
        for (String key : new String[]{"replay", "replays"}) {
            JSONObject body = build(report(null, true, new JSONObject(), new JSONObject().put(key, true)));

            assertFalse(key, body.has("replay"));
            assertTrue(key, body.has("screenshotUrl"));
        }
        assertEquals(0, uploads.replays);
    }

    @Test(expected = JSONException.class)
    public void anExclusionThatIsNotABooleanFailsTheTicket() throws Exception {
        build(report(null, false, new JSONObject().put("consoleLog", "yes"), new JSONObject()));
    }
}
