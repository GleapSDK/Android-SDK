package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One network log entry.
 * <p>
 * Entries are sent in this shape (ISO dates in UTC):
 * <pre>
 * { "date": request start, "type": "GET", "url": "...", "duration": ms, "success": true,
 *   "request":  { "headers": { ... }, "payload": "..." },
 *   "response": { "status": 200, "statusText": "OK", "headers": { ... }, "responseText": "..." } }
 * </pre>
 * {@code success} is true when an HTTP response arrived (any status). Failed requests have
 * {@code "response": { "errorText": "..." }} instead.
 */
public class Networklog {
    /** Maximum length of a request payload or response text. */
    static final int BODY_CAP = 150000;
    static final String TRUNCATED_PREFIX = "\n… [truncated, ";
    static final String BINARY_BODY_OMITTED = "[binary body omitted]";
    static final String STREAMING_BODY_OMITTED = "[streaming body omitted]";
    static final String BODY_NOT_CAPTURED = "[body not captured]";
    static final String BODY_PENDING = "[body pending]";

    private final String url;
    private final String method;
    private final long startMillis;
    private final int duration;
    private final boolean success;
    private final JSONObject request;
    private final JSONObject response;
    // Renders the response text of a body that is still being read by the app (interceptor).
    private BodyText pendingResponseText;

    interface BodyText {
        /** The response text so far: {@link #BODY_PENDING} until the app has read the body. */
        String get();
    }

    /**
     * Creates a network log entry, e.g. for {@link Gleap#attachNetworkLogs(Networklog[])}.
     *
     * @param url         the requested url
     * @param requestType the request method
     * @param status      the HTTP status of the response, 0 when no response arrived
     * @param duration    duration of the request in milliseconds
     * @param request     request details, recommended: {@code headers} (object) and {@code payload} (string)
     * @param response    response details, recommended: {@code headers} (object), {@code statusText} and
     *                    {@code responseText} (string); {@code errorText} when the request failed
     */
    public Networklog(String url, RequestType requestType, int status, int duration, JSONObject request, JSONObject response) {
        this.url = url;
        this.method = requestType != null ? requestType.name() : RequestType.GET.name();
        this.duration = duration;
        this.startMillis = System.currentTimeMillis() - Math.max(0, duration);
        this.request = copyOf(request);
        JSONObject responseCopy = copyOf(response);
        if (responseCopy == null) {
            responseCopy = new JSONObject();
        }
        if (status > 0) {
            try {
                responseCopy.put("status", status);
            } catch (Exception ignore) {
            }
        }
        this.response = responseCopy;
        this.success = status > 0 && !responseCopy.has("errorText");
    }

    /**
     * Entry recorded by the SDK. Takes ownership of the request and response objects.
     */
    Networklog(String method, String url, long startMillis, int duration, boolean success, JSONObject request, JSONObject response) {
        this.url = url;
        this.method = method != null ? method.toUpperCase(Locale.ROOT) : RequestType.GET.name();
        this.startMillis = startMillis;
        this.duration = duration;
        this.success = success;
        this.request = request;
        this.response = response;
    }

    String getUrl() {
        return url;
    }

    long getStartMillis() {
        return startMillis;
    }

    /**
     * The response body is still being read by the app. The given text is rendered when the log is sent.
     */
    synchronized void setPendingResponseText(BodyText responseText) {
        this.pendingResponseText = responseText;
    }

    /**
     * The entry in the network log shape, without redaction. Always a fresh copy.
     */
    synchronized JSONObject toRawJSON() {
        JSONObject object = new JSONObject();
        try {
            object.put("date", DateUtil.dateToString(new Date(startMillis)));
            object.put("type", method);
            if (url != null) {
                object.put("url", url);
            }
            if (duration >= 0) {
                object.put("duration", duration);
            }
            object.put("success", success);
            if (request != null) {
                object.put("request", GleapNetworkLogSanitizer.deepCopy(request));
            }
            if (response != null) {
                JSONObject responseCopy = (JSONObject) GleapNetworkLogSanitizer.deepCopy(response);
                if (pendingResponseText != null) {
                    String text = BODY_PENDING;
                    try {
                        text = pendingResponseText.get();
                    } catch (Throwable ignore) {
                    }
                    responseCopy.put("responseText", text);
                }
                object.put("response", responseCopy);
            }
        } catch (Exception ignore) {
        }
        return object;
    }

    /**
     * The entry as it is sent: with the network log blacklist and the props to ignore applied.
     *
     * @return the entry, or null when its url is blacklisted
     */
    public JSONObject toJSON() {
        try {
            return GleapNetworkLogSanitizer.fromConfig().sanitizeEntry(toRawJSON());
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * Concatenates JSON arrays. Null arrays are skipped.
     */
    public static JSONArray mergeMultiJsonArray(JSONArray... arrays) {
        JSONArray outArray = new JSONArray();
        if (arrays == null) {
            return outArray;
        }
        for (JSONArray array : arrays) {
            if (array == null) {
                continue;
            }
            for (int i = 0; i < array.length(); i++) {
                Object value = array.opt(i);
                outArray.put(value != null ? value : JSONObject.NULL);
            }
        }
        return outArray;
    }

    /**
     * Keeps the head of a body longer than {@link #BODY_CAP} and appends the truncation marker.
     * Bodies that already end with the marker are returned unchanged.
     */
    static String capBody(String body) {
        if (body == null || body.length() <= BODY_CAP) {
            return body;
        }
        int markerStart = body.lastIndexOf(TRUNCATED_PREFIX);
        if (markerStart >= 0 && markerStart <= BODY_CAP && body.endsWith(" bytes]")) {
            return body;
        }
        int cut = BODY_CAP;
        if (Character.isHighSurrogate(body.charAt(cut - 1))) {
            cut--;
        }
        return body.substring(0, cut) + TRUNCATED_PREFIX + utf8Length(body) + " bytes]";
    }

    /**
     * The error text of a failed request: exception class and message.
     */
    static String describeError(Throwable error) {
        String name = error.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = error.getClass().getName();
        }
        String message = error.getMessage();
        return message != null && !message.isEmpty() ? name + ": " + message : name;
    }

    /**
     * Joins the values of a repeated header with ", "; credential headers are masked.
     */
    static String headerValue(String name, List<String> values) {
        if (GleapNetworkLogSanitizer.isCredentialHeader(name)) {
            return GleapNetworkLogSanitizer.REDACTED;
        }
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(value);
        }
        return joined.toString();
    }

    private static long utf8Length(String text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private static JSONObject copyOf(JSONObject object) {
        if (object == null) {
            return null;
        }
        try {
            return (JSONObject) GleapNetworkLogSanitizer.deepCopy(object);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
