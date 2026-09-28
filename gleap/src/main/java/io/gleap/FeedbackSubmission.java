package io.gleap;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A ticket as it is submitted: a copy of the report data, taken on the thread that submits it,
 * so later changes by the app (custom data, events) cannot change or break a ticket that is
 * being sent.
 */
final class FeedbackSubmission {
    final String type;
    // As set by the widget; null or empty for a plain bug report.
    final String outboundId;
    final String spamToken;
    // The form data merged with the ticket attributes.
    final JSONObject formData;
    final JSONObject customData;
    final JSONArray customEventLog;
    final String[] tags;
    final String severity;
    final boolean silent;
    final Bitmap screenshot;
    // Data the widget action excludes (excludeData), e.g. {"consoleLog": true}.
    final JSONObject excludeData;
    // Data a silent crash report excludes.
    final JSONObject crashExcludeData;

    FeedbackSubmission(String type, String outboundId, String spamToken, JSONObject formData,
                       JSONObject customData, JSONArray customEventLog, String[] tags, String severity,
                       boolean silent, Bitmap screenshot, JSONObject excludeData, JSONObject crashExcludeData) {
        this.type = type;
        this.outboundId = outboundId;
        this.spamToken = spamToken;
        this.formData = formData;
        this.customData = customData;
        this.customEventLog = customEventLog;
        this.tags = tags;
        this.severity = severity;
        this.silent = silent;
        this.screenshot = screenshot;
        this.excludeData = excludeData;
        this.crashExcludeData = crashExcludeData;
    }

    /**
     * Copies the current report data.
     */
    static FeedbackSubmission capture() {
        GleapBug bug = GleapBug.getInstance();
        GleapConfig config = GleapConfig.getInstance();
        String[] tags = bug.getTags();
        return new FeedbackSubmission(
                bug.getType(),
                bug.getOutboundId(),
                bug.getSpamToken(),
                copy(bug.getData()),
                copy(bug.getCustomData()),
                copy(bug.getCustomEventLog()),
                tags != null ? tags.clone() : null,
                bug.getSeverity(),
                bug.isSilent(),
                bug.getScreenshot(),
                copy(config.getStripModel()),
                copy(config.getCrashStripModel()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T copy(T json) {
        if (json == null) {
            return null;
        }
        try {
            return (T) GleapNetworkLogSanitizer.deepCopy(json);
        } catch (Exception e) {
            return json;
        }
    }
}
