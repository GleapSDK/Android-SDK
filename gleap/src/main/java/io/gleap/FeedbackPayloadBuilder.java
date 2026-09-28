package io.gleap;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;

/**
 * Builds the body of POST /bugs/v2 from a {@link FeedbackSubmission}: the uploads (screenshot,
 * replay, attachments), the report data and the logs, without the data the report excludes.
 */
final class FeedbackPayloadBuilder {
    /**
     * Uploads files for a ticket and returns what the body refers to.
     */
    interface Uploads {
        /**
         * @return the upload response, {@code {"fileUrl": ...}}
         */
        JSONObject uploadScreenshot(Bitmap screenshot) throws IOException, JSONException;

        /**
         * @return {@code {"interval": ms, "frames": [...]}}
         */
        JSONObject uploadReplay() throws IOException, JSONException;

        /**
         * @return {@code [{"url", "name", "type"}]}
         */
        JSONArray uploadAttachments();
    }

    /**
     * Data read when the ticket is sent (background thread).
     */
    interface ReportData {
        JSONArray networkLogs();

        /**
         * @return the env data, or null when there is none
         */
        JSONObject metaData() throws JSONException;

        JSONArray consoleLogs();
    }

    private FeedbackPayloadBuilder() {
    }

    /**
     * @param consoleLogsEnabled console logs are sent (remote config)
     */
    static JSONObject build(FeedbackSubmission report, boolean consoleLogsEnabled, Uploads uploads, ReportData data)
            throws JSONException, IOException {
        JSONObject exclude = report.excludeData;
        JSONObject crashExclude = report.crashExcludeData;

        // The screenshot (and the replay with it) is left out when the widget action or a
        // silent crash report says so; the crash report decides when both do.
        boolean stripImages = false;
        if (exclude.has("screenshot")) {
            stripImages = exclude.getBoolean("screenshot");
        }
        if (crashExclude.has("screenshot")) {
            stripImages = crashExclude.getBoolean("screenshot");
        }

        JSONObject body = new JSONObject();

        String outboundId = report.outboundId;
        if (outboundId == null || outboundId.equalsIgnoreCase("")) {
            outboundId = "bugreporting";
        }
        body.put("outbound", outboundId);

        body.put("spamToken", report.spamToken);

        if (!stripImages) {
            // No screenshot when none could be taken: the ticket is sent without one.
            if (report.screenshot != null) {
                JSONObject screenshotUpload = uploads.uploadScreenshot(report.screenshot);
                body.put("screenshotUrl", screenshotUpload.get("fileUrl"));
            }
            body.put("replay", uploads.uploadReplay());
        }

        body.put("type", report.type);

        if (exclude.has("attachments") && !exclude.getBoolean("attachments") || !exclude.has("attachments")) {
            body.put("attachments", uploads.uploadAttachments());
        }

        body.put("formData", report.formData);
        body.put("networkLogs", data.networkLogs());
        body.put("customEventLog", report.customEventLog);
        body.put("isSilent", report.silent ? "true" : "false");

        JSONObject metaData = data.metaData();
        if (metaData != null) {
            body.put("metaData", metaData);
        }

        body.put("customData", report.customData);
        body.put("priority", report.severity);

        try {
            body.put("tags", new JSONArray(report.tags));
        } catch (Exception ex) {
        }

        if (consoleLogsEnabled) {
            body.put("consoleLog", data.consoleLogs());
        }

        for (Iterator<String> it = exclude.keys(); it.hasNext(); ) {
            String key = it.next();
            if (exclude.getBoolean(key)) {
                body.remove(key);
            }
        }

        return body;
    }
}
