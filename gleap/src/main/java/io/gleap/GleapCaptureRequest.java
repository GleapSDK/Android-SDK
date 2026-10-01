package io.gleap;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * A screenshot or recording the widget asked for (capture-start): the request, its options and
 * the labels for the SDK's own UI (localized by the widget, English when missing).
 */
final class GleapCaptureRequest {
    static final String KIND_SCREENSHOT = "screenshot";
    static final String KIND_RECORDING = "recording";

    // Labels longer than this are cut: they are shown in the SDK's small bar.
    private static final int MAX_LABEL_LENGTH = 160;

    private static final Map<String, String> ENGLISH = new HashMap<>();

    static {
        ENGLISH.put("barScreenshotHint", "Go to the screen you want to show, then tap Capture.");
        ENGLISH.put("barCapture", "Capture");
        ENGLISH.put("barCancel", "Cancel");
        ENGLISH.put("barRecordHint", "Go to where the issue happens, then start recording.");
        ENGLISH.put("barStart", "Start");
        ENGLISH.put("barStop", "Stop");
        ENGLISH.put("barRecording", "Recording");
        ENGLISH.put("barDragHint", "Drag to move");
        ENGLISH.put("microphone", "Microphone");
        ENGLISH.put("previewTitle", "Send this recording?");
        ENGLISH.put("previewSend", "Send");
        ENGLISH.put("previewRetake", "Retake");
        ENGLISH.put("previewPlay", "Play");
        ENGLISH.put("previewPause", "Pause");
        ENGLISH.put("uploading", "Uploading…");
        ENGLISH.put("recordingInterrupted", "Recording stopped because the page changed.");
        ENGLISH.put("recordAgain", "Record again");
        ENGLISH.put("permissionDenied", "Screen capture was blocked. You can upload a file instead.");
        ENGLISH.put("notSupported", "Screen capture isn't available here. You can upload a file instead.");
        ENGLISH.put("failed", "That didn't work. Please try again or upload a file.");
    }

    final String id;
    final String kind;
    final String ticketShareToken;
    final boolean annotate;
    final boolean audio;
    final boolean attachLogs;
    final int maxDurationSec;
    // options.include as sent (null: the defaults).
    final JSONObject include;
    private final JSONObject labels;

    private GleapCaptureRequest(String id, String kind, String ticketShareToken, boolean annotate, boolean audio,
                                boolean attachLogs, int maxDurationSec, JSONObject include, JSONObject labels) {
        this.id = id;
        this.kind = kind;
        this.ticketShareToken = ticketShareToken;
        this.annotate = annotate;
        this.audio = audio;
        this.attachLogs = attachLogs;
        this.maxDurationSec = maxDurationSec;
        this.include = include;
        this.labels = labels;
    }

    /**
     * Reads a capture-start message's data; null when it has no valid request id or an unknown kind.
     */
    static GleapCaptureRequest fromStart(JSONObject data) {
        if (data == null) {
            return null;
        }
        String id = data.optString("requestId", null);
        if (!GleapCapture.isValidRequestId(id)) {
            return null;
        }
        String kind = data.optString("kind", "");
        if (!KIND_SCREENSHOT.equals(kind) && !KIND_RECORDING.equals(kind)) {
            // "any" is resolved by the widget before it starts a capture.
            return null;
        }
        JSONObject options = data.optJSONObject("options");
        if (options == null) {
            options = new JSONObject();
        }
        String token = data.optString("ticketShareToken", "");
        return new GleapCaptureRequest(
                id,
                kind,
                token != null ? token.trim() : "",
                options.optBoolean("annotate", true),
                options.optBoolean("audio", false),
                options.optBoolean("attachLogs", true),
                GleapCaptureGeometry.clampDuration(options.optInt("maxDurationSec", GleapCapture.DEFAULT_RECORDING_SEC)),
                options.optJSONObject("include"),
                data.optJSONObject("labels"));
    }

    boolean isRecording() {
        return KIND_RECORDING.equals(kind);
    }

    /**
     * The widget's label for {@code key}, else the English default.
     */
    String label(String key) {
        if (labels != null) {
            Object value = labels.opt(key);
            if (value instanceof String) {
                String text = ((String) value).trim();
                if (!text.isEmpty()) {
                    return text.length() > MAX_LABEL_LENGTH ? text.substring(0, MAX_LABEL_LENGTH) : text;
                }
            }
        }
        String english = ENGLISH.get(key);
        return english != null ? english : "";
    }
}
