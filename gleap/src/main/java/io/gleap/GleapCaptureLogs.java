package io.gleap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Log bundles for capture requests: the server asks for the app's logs (kind "logs", pushed on
 * the WebSocket or returned with a ping), or a screenshot / recording asks for the logs around it
 * (attachLogs). The bundle carries what a ticket carries (POST /bugs/v2) with the same shapes,
 * limited to the parts the request includes, and is sent gzip compressed to
 * POST /v3/shared/capture-requests/{id}/logs.
 * <p>
 * Each request id is handled once per process; the work runs on the capture threads.
 */
final class GleapCaptureLogs {
    static final long FLUSH_TIMEOUT_MS = 500;
    static final String KIND_LOGS = "logs";
    // A request that fails on the server's side (or the network) is tried 3 times, 2 and then 8
    // minutes apart; then it is reported failed, so the server closes it.
    static final int MAX_ATTEMPTS = 3;
    static final long[] RETRY_DELAYS_MS = {2 * 60 * 1000L, 8 * 60 * 1000L};
    private static final int MAX_REMEMBERED_REQUESTS = 200;

    /**
     * What a bundle may contain (the request's options.include): everything but the replay by
     * default. The replay is never sent with capture logs (its frames are not masked).
     */
    static final class Include {
        final boolean consoleLog;
        final boolean networkLogs;
        final boolean customData;
        final boolean metaData;
        final boolean customEventLog;
        final boolean replays;

        Include(boolean consoleLog, boolean networkLogs, boolean customData, boolean metaData,
                boolean customEventLog, boolean replays) {
            this.consoleLog = consoleLog;
            this.networkLogs = networkLogs;
            this.customData = customData;
            this.metaData = metaData;
            this.customEventLog = customEventLog;
            this.replays = replays;
        }

        static Include from(JSONObject include) {
            if (include == null) {
                include = new JSONObject();
            }
            return new Include(
                    include.optBoolean("consoleLog", true),
                    include.optBoolean("networkLogs", true),
                    include.optBoolean("customData", true),
                    include.optBoolean("metaData", true),
                    include.optBoolean("customEventLog", true),
                    include.optBoolean("replays", false));
        }
    }

    /**
     * Where the bundle's data comes from: the collectors tickets use.
     */
    interface Sources {
        // Console logs are enabled in the remote config (as for tickets).
        boolean consoleLogsEnabled();

        JSONArray consoleLog();

        JSONArray networkLogs();

        JSONObject customData();

        // The env data; null while it is disabled.
        JSONObject metaData() throws JSONException;

        JSONArray customEventLog();
    }

    private static final Sources DEVICE = new Sources() {
        @Override
        public boolean consoleLogsEnabled() {
            return GleapConfig.getInstance().isEnableConsoleLogs();
        }

        @Override
        public JSONArray consoleLog() {
            return GleapBug.getInstance().getLogs();
        }

        @Override
        public JSONArray networkLogs() {
            return GleapBug.getInstance().getNetworklogs();
        }

        @Override
        public JSONObject customData() {
            JSONObject customData = GleapBug.getInstance().getCustomData();
            try {
                return (JSONObject) GleapNetworkLogSanitizer.deepCopy(customData);
            } catch (Exception e) {
                return customData;
            }
        }

        @Override
        public JSONObject metaData() throws JSONException {
            if (PhoneMeta.isEnvDataDisabled()) {
                return null;
            }
            PhoneMeta phoneMeta = GleapBug.getInstance().getPhoneMeta();
            return phoneMeta != null ? phoneMeta.getJSONObj() : null;
        }

        @Override
        public JSONArray customEventLog() {
            return GleapBug.getInstance().getCustomEventLog();
        }
    };

    // After DEVICE: static fields are initialized in the order they are declared.
    private static final GleapCaptureLogs instance = new GleapCaptureLogs();

    // Request ids started on this device (in progress or sent). Guarded by this.
    private final Set<String> started = new LinkedHashSet<>();
    private volatile Sources sources = DEVICE;

    private GleapCaptureLogs() {
    }

    static GleapCaptureLogs getInstance() {
        return instance;
    }

    // Tests only; null restores the device's collectors.
    void setSourcesForTesting(Sources testSources) {
        sources = testSources != null ? testSources : DEVICE;
    }

    // Tests only.
    synchronized void resetForTesting() {
        started.clear();
        sources = DEVICE;
    }

    /**
     * The capture requests of a ping answer ({@code cr}).
     */
    void onCaptureRequests(JSONArray requests) {
        if (requests == null) {
            return;
        }
        for (int i = 0; i < requests.length(); i++) {
            onCaptureRequest(requests.optJSONObject(i));
        }
    }

    /**
     * A capture request the server pushed: {@code {id, kind, options, ticketShareToken, expiresAt}}.
     * Only log requests come this way (screenshots and recordings start from the widget); each
     * one is handled once.
     */
    void onCaptureRequest(JSONObject request) {
        try {
            if (request == null || !KIND_LOGS.equals(request.optString("kind"))) {
                return;
            }
            final String id = request.optString("id", null);
            if (!GleapCapture.isValidRequestId(id) || !begin(id)) {
                return;
            }
            JSONObject options = request.optJSONObject("options");
            attempt(id, Include.from(options != null ? options.optJSONObject("include") : null), 1);
        } catch (Throwable error) {
            GleapErrors.report(error, "onCaptureRequest");
        }
    }

    /**
     * One attempt at a log request. The request stays started (a push or ping answer for it is
     * ignored) while it is retried: the logs are never collected and sent again for every ping.
     */
    private void attempt(final String id, final Include include, final int number) {
        GleapCaptureExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String retryReason;
                try {
                    retryReason = handleLogsRequest(id, include);
                } catch (Throwable error) {
                    GleapLog.w("Could not send the logs for a capture request", error);
                    retryReason = error.getClass().getSimpleName();
                }
                if (retryReason == null) {
                    return;
                }
                if (number >= MAX_ATTEMPTS) {
                    // The server closes the request: no further pushes.
                    try {
                        GleapCaptureApi.event(id, "failed", "The logs could not be sent (" + retryReason + ").");
                    } catch (Throwable ignore) {
                    }
                    return;
                }
                GleapMainThread.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        attempt(id, include, number + 1);
                    }
                }, RETRY_DELAYS_MS[number - 1]);
            }
        });
    }

    /**
     * Claims the request, collects the logs and sends them.
     *
     * @return null when nothing is left to do for this request on this device, else why it
     * should be tried again (server trouble)
     */
    String handleLogsRequest(String id, Include include) throws IOException, JSONException {
        if (!GleapCapture.isRemoteLogCollectionEnabled()) {
            GleapCaptureApi.event(id, "unsupported", "Remote log collection is disabled in the app.");
            return null;
        }

        GleapCaptureApi.Response claim = GleapCaptureApi.claim(id);
        if (!claim.ok()) {
            // Gone (410) or refused for good: nothing to send.
            return isRetryable(claim.status) ? "HTTP " + claim.status : null;
        }

        JSONObject bundle = collect(include, null, null);
        GleapCaptureApi.Response response = send(id, bundle);
        return !response.ok() && isRetryable(response.status) ? "HTTP " + response.status : null;
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    /**
     * Sends the logs around a screenshot or recording the customer made (its attachLogs option):
     * the widget claimed that request already. Does nothing while remote log collection is off.
     */
    void sendForCapture(final String requestId, JSONObject include, final Date windowStart, final Date windowEnd) {
        if (!GleapCapture.isRemoteLogCollectionEnabled() || !GleapCapture.isValidRequestId(requestId)) {
            return;
        }
        final Include parts = Include.from(include);
        GleapCaptureExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GleapCaptureApi.Response response = send(requestId, collect(parts, windowStart, windowEnd));
                    if (!response.ok()) {
                        GleapLog.w("The capture logs were not accepted (HTTP " + response.status + ")");
                    }
                } catch (Throwable error) {
                    GleapLog.w("Could not send the capture logs", error);
                }
            }
        });
    }

    private JSONObject collect(Include include, Date windowStart, Date windowEnd) throws JSONException {
        flushWrapperLogs();
        return buildBundle(include, sources, windowStart, windowEnd, new Date(), GleapCapture.deviceId());
    }

    /**
     * The bundle: the included parts (absent = not collected), the collection time and window,
     * and who sent it.
     */
    static JSONObject buildBundle(Include include, Sources sources, Date windowStart, Date windowEnd,
                                  Date capturedAt, String deviceId) throws JSONException {
        JSONObject bundle = new JSONObject();
        if (include.consoleLog && sources.consoleLogsEnabled()) {
            putIfPresent(bundle, "consoleLog", sources.consoleLog());
        }
        if (include.networkLogs) {
            putIfPresent(bundle, "networkLogs", sources.networkLogs());
        }
        if (include.customData) {
            putIfPresent(bundle, "customData", sources.customData());
        }
        if (include.metaData) {
            putIfPresent(bundle, "metaData", sources.metaData());
        }
        if (include.customEventLog) {
            putIfPresent(bundle, "customEventLog", sources.customEventLog());
        }
        // No replay (include.replays): its frames are screenshots taken without the capture
        // masks (password fields, masked views, secure windows).

        String captured = DateUtil.dateToString(capturedAt);
        bundle.put("capturedAt", captured);
        if (windowStart != null) {
            bundle.put("windowStart", DateUtil.dateToString(windowStart));
        } else {
            Date oldest = oldestEntry(bundle);
            bundle.put("windowStart", oldest != null && oldest.before(capturedAt) ? DateUtil.dateToString(oldest) : captured);
        }
        bundle.put("windowEnd", windowEnd != null ? DateUtil.dateToString(windowEnd) : captured);
        bundle.put("platform", GleapCapture.PLATFORM);
        bundle.put("sdkType", GleapCapture.sdkType());
        bundle.put("sdkVersion", GleapCapture.sdkVersion());
        if (deviceId != null) {
            bundle.put("deviceId", deviceId);
        }
        return bundle;
    }

    private static void putIfPresent(JSONObject bundle, String key, Object value) throws JSONException {
        if (value != null) {
            bundle.put(key, value);
        }
    }

    // The oldest dated entry of the logs and events, or null.
    private static Date oldestEntry(JSONObject bundle) {
        Date oldest = null;
        for (String key : new String[]{"consoleLog", "networkLogs", "customEventLog"}) {
            JSONArray entries = bundle.optJSONArray(key);
            if (entries == null) {
                continue;
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                Object date = entry != null ? entry.opt("date") : null;
                if (!(date instanceof String)) {
                    continue;
                }
                try {
                    Date parsed = DateUtil.stringToDate((String) date);
                    if (oldest == null || parsed.before(oldest)) {
                        oldest = parsed;
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return oldest;
    }

    /**
     * Sends the bundle gzip compressed, leaving out its biggest parts while it is above the
     * server's limit.
     */
    static GleapCaptureApi.Response send(String id, JSONObject bundle) throws IOException {
        byte[] body = gzip(bundle.toString());
        for (String key : new String[]{"consoleLog", "networkLogs", "customEventLog"}) {
            if (body.length <= GleapCaptureApi.MAX_LOGS_BYTES) {
                break;
            }
            bundle.remove(key);
            body = gzip(bundle.toString());
        }
        return GleapCaptureApi.postLogs(id, body);
    }

    static byte[] gzip(String json) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /**
     * Gives the wrapper SDK (setLogFlushHandler) up to 500 ms to hand over its buffered logs.
     * Never call it on the main thread: the handler runs there.
     */
    static void flushWrapperLogs() {
        final GleapLogFlushHandler handler = GleapCapture.getLogFlushHandler();
        if (handler == null) {
            return;
        }
        final CountDownLatch flushed = new CountDownLatch(1);
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                flushed.countDown();
            }
        };
        try {
            GleapMainThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        handler.onFlushRequested(done);
                    } catch (Throwable error) {
                        flushed.countDown();
                        GleapErrors.report(error, "logFlushHandler");
                    }
                }
            });
            flushed.await(FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignore) {
        }
    }

    private synchronized boolean begin(String id) {
        if (started.contains(id)) {
            return false;
        }
        started.add(id);
        while (started.size() > MAX_REMEMBERED_REQUESTS) {
            Iterator<String> oldest = started.iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }
}
