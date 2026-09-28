package io.gleap;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A ticket as it is submitted: a copy of the report data, taken on the thread that submits it,
 * so later changes by the app (custom data, events) cannot change or break a ticket that is
 * being sent. What belongs to one ticket only (its type, outbound, form data, excluded data,
 * a crash report's severity) never stays behind for the next one.
 */
final class FeedbackSubmission {
    // The priority of the tickets from the widget.
    static final String WIDGET_SEVERITY = "MEDIUM";

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
     * Takes the ticket the widget submits: copies the report data and clears what the widget set
     * for this ticket only (type, outbound, spam token, form data, excluded data).
     */
    static FeedbackSubmission takeWidgetTicket() {
        GleapBug bug = GleapBug.getInstance();
        GleapConfig config = GleapConfig.getInstance();
        String[] tags = bug.getTags();
        FeedbackSubmission submission = new FeedbackSubmission(
                bug.getType(),
                bug.getOutboundId(),
                bug.getSpamToken(),
                copy(bug.getData()),
                copy(bug.getCustomData()),
                copy(bug.getCustomEventLog()),
                tags != null ? tags.clone() : null,
                WIDGET_SEVERITY,
                false,
                bug.getScreenshot(),
                copy(config.getStripModel()),
                new JSONObject());

        bug.setType("");
        bug.setOutboundId(null);
        bug.setSpamToken(null);
        bug.setData(null);
        config.setStripModel(new JSONObject());
        return submission;
    }

    /**
     * A silent crash report: the app's data (custom data, events, tags, ticket attributes) with
     * the report's own description, type, severity, screenshot and excluded data. The widget's
     * ticket data is not touched.
     */
    static FeedbackSubmission silentReport(String type, String description, String severity, Bitmap screenshot,
                                           JSONObject excludeData) {
        GleapBug bug = GleapBug.getInstance();
        JSONObject formData = new JSONObject();
        try {
            formData = bug.mergeJSONObjects(new JSONObject().put("description", description), bug.getTicketAttributes());
        } catch (Exception ignore) {
        }
        String[] tags = bug.getTags();
        return new FeedbackSubmission(
                type,
                null,
                null,
                copy(formData),
                copy(bug.getCustomData()),
                copy(bug.getCustomEventLog()),
                tags != null ? tags.clone() : null,
                severity,
                true,
                screenshot,
                new JSONObject(),
                copy(excludeData));
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
