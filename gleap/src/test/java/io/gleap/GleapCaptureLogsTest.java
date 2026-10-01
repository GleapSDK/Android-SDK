package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.zip.GZIPInputStream;

/**
 * Log bundles for capture requests: only what the request includes and the app allows, sent
 * gzip compressed with the session, once per request, and never while remote log collection is
 * turned off.
 */
public class GleapCaptureLogsTest {
    private static final String ID = "65f0c0ffee0123456789abcd";
    private static final String PATH = "/v3/shared/capture-requests/" + ID;

    private SdkTestEnvironment sdk;
    private FakeSources sources;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        sources = new FakeSources();
        GleapCaptureLogs.getInstance().resetForTesting();
        GleapCaptureLogs.getInstance().setSourcesForTesting(sources);
        GleapCaptureExecutor.setExecutorForTesting(new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
        GleapCapture.setRemoteLogCollectionEnabled(true);
        GleapCapture.setLogFlushHandler(null);
    }

    @After
    public void tearDown() {
        GleapCaptureExecutor.setExecutorForTesting(null);
        GleapCaptureLogs.getInstance().resetForTesting();
        GleapCapture.setRemoteLogCollectionEnabled(true);
        sdk.tearDown();
    }

    @Test
    public void aLogsRequestIsClaimedAndAnsweredWithAGzippedBundle() throws Exception {
        sdk.server.respond(PATH + "/claim", 200, "{\"claimedElsewhere\":false}");
        sdk.server.respond(PATH + "/logs", 200, "{}");

        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(null));

        FakeGleapServer.Request claim = sdk.server.last(PATH + "/claim");
        JSONObject claimBody = claim.bodyJson();
        assertEquals("android", claimBody.getString("platform"));
        assertEquals("NATIVE", claimBody.getString("sdkType"));
        assertEquals(gleap.io.gleap.BuildConfig.VERSION_NAME, claimBody.getString("sdkVersion"));

        FakeGleapServer.Request logs = sdk.server.last(PATH + "/logs");
        assertEquals("gzip", logs.headers.get("Content-Encoding"));
        assertEquals(SdkTestEnvironment.SDK_KEY, logs.headers.get("Api-Token"));
        assertEquals("id-1", logs.headers.get("Gleap-Id"));
        assertEquals("hash-1", logs.headers.get("Gleap-Hash"));
        JSONObject bundle = new JSONObject(gunzip(logs.body.toByteArray()));
        assertEquals("boom", bundle.getJSONArray("consoleLog").getJSONObject(0).getString("log"));
        assertEquals(1, bundle.getJSONArray("networkLogs").length());
        assertEquals("gold", bundle.getJSONObject("customData").getString("plan"));
        assertEquals("Pixel", bundle.getJSONObject("metaData").getString("deviceModel"));
        assertEquals("checkout", bundle.getJSONArray("customEventLog").getJSONObject(0).getString("name"));
        // The replay is only sent when the request asks for it.
        assertFalse(bundle.has("replay"));
        assertEquals("android", bundle.getString("platform"));
        // The window starts with the oldest entry.
        assertEquals("2026-09-30T10:00:00.000Z", bundle.getString("windowStart"));
        assertTrue(bundle.has("capturedAt"));
        assertTrue(bundle.has("windowEnd"));
    }

    @Test
    public void onlyTheIncludedPartsAreSent() throws Exception {
        sdk.server.respond(PATH + "/claim", 200, "{}");
        sdk.server.respond(PATH + "/logs", 200, "{}");
        JSONObject include = new JSONObject()
                .put("consoleLog", false)
                .put("networkLogs", true)
                .put("customData", false)
                .put("metaData", false)
                .put("customEventLog", false)
                .put("replays", true);
        sources.replaysEnabled = true;

        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(include));

        JSONObject bundle = new JSONObject(gunzip(sdk.server.last(PATH + "/logs").body.toByteArray()));
        assertFalse(bundle.has("consoleLog"));
        assertFalse(bundle.has("customData"));
        assertFalse(bundle.has("metaData"));
        assertFalse(bundle.has("customEventLog"));
        assertTrue(bundle.has("networkLogs"));
        assertEquals(5000, bundle.getJSONObject("replay").getInt("interval"));
    }

    @Test
    public void theAppsSettingsApply() throws Exception {
        // Console logs off in the remote config, env data disabled, replays off.
        sources.consoleLogsEnabled = false;
        sources.metaData = null;
        sources.replaysEnabled = false;
        JSONObject bundle = GleapCaptureLogs.buildBundle(
                GleapCaptureLogs.Include.from(new JSONObject().put("replays", true)), sources, null, null,
                new Date(), "device-1");

        assertFalse(bundle.has("consoleLog"));
        assertFalse(bundle.has("metaData"));
        assertFalse(bundle.has("replay"));
        assertEquals("device-1", bundle.getString("deviceId"));
    }

    @Test
    public void eachRequestIsSentOnce() throws Exception {
        sdk.server.respond(PATH + "/claim", 200, "{}");
        sdk.server.respond(PATH + "/logs", 200, "{}");

        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(null));
        // The same request again, e.g. with a ping answer or after a reconnect.
        GleapCaptureLogs.getInstance().onCaptureRequests(new JSONArray().put(logsRequest(null)));

        assertEquals(1, sdk.server.requestsTo(PATH + "/claim").size());
        assertEquals(1, sdk.server.requestsTo(PATH + "/logs").size());
    }

    @Test
    public void aRequestThatFailedOnTheServerSideIsTriedAgainWhenItComesBack() throws Exception {
        sdk.server.respond(PATH + "/claim", 200, "{}");
        sdk.server.respond(PATH + "/logs", 503, "{}");
        sdk.server.respond(PATH + "/logs", 200, "{}");

        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(null));
        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(null));

        assertEquals(2, sdk.server.requestsTo(PATH + "/logs").size());
    }

    @Test
    public void withRemoteLogCollectionOffTheRequestIsAnsweredUnsupported() throws Exception {
        sdk.server.respond(PATH + "/event", 200, "{}");
        GleapCapture.setRemoteLogCollectionEnabled(false);

        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(null));

        assertTrue(sdk.server.requestsTo(PATH + "/claim").isEmpty());
        assertTrue(sdk.server.requestsTo(PATH + "/logs").isEmpty());
        JSONObject event = sdk.server.last(PATH + "/event").bodyJson();
        assertEquals("unsupported", event.getString("type"));

        // Captures do not bring logs either.
        GleapCaptureLogs.getInstance().sendForCapture(ID, null, null, new Date());
        assertTrue(sdk.server.requestsTo(PATH + "/logs").isEmpty());
    }

    @Test
    public void aClosedRequestGetsNoLogs() throws Exception {
        sdk.server.respond(PATH + "/claim", 410, "{\"status\":\"expired\"}");

        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(null));

        assertTrue(sdk.server.requestsTo(PATH + "/logs").isEmpty());
    }

    @Test
    public void onlyLogRequestsWithAValidIdAreHandled() throws Exception {
        sdk.server.respond("/v3/shared/capture-requests/", 200, "{}");

        GleapCaptureLogs.getInstance().onCaptureRequest(new JSONObject().put("id", ID).put("kind", "screenshot"));
        GleapCaptureLogs.getInstance().onCaptureRequest(new JSONObject().put("id", "../../bugs").put("kind", "logs"));
        GleapCaptureLogs.getInstance().onCaptureRequest(new JSONObject().put("kind", "logs"));
        GleapCaptureLogs.getInstance().onCaptureRequest(null);

        assertTrue(sdk.server.requests.isEmpty());
    }

    @Test
    public void theFlushHandlerRunsOnTheMainThreadBeforeTheLogsAreRead() throws Exception {
        sdk.server.respond(PATH + "/claim", 200, "{}");
        sdk.server.respond(PATH + "/logs", 200, "{}");
        final boolean[] flushed = {false};
        GleapCapture.setLogFlushHandler(new GleapLogFlushHandler() {
            @Override
            public void onFlushRequested(Runnable done) {
                flushed[0] = true;
                done.run();
            }
        });
        // The main thread runs the posted work right away here.
        GleapMainThread.setTestExecutor(new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });

        GleapCaptureLogs.getInstance().onCaptureRequest(logsRequest(null));

        assertTrue(flushed[0]);
        assertEquals(1, sdk.server.requestsTo(PATH + "/logs").size());
    }

    @Test
    public void gzipRoundTrip() throws Exception {
        String json = "{\"consoleLog\":[{\"log\":\"é ✓\"}]}";
        assertEquals(json, gunzip(GleapCaptureLogs.gzip(json)));
    }

    private static JSONObject logsRequest(JSONObject include) throws Exception {
        JSONObject options = new JSONObject();
        if (include != null) {
            options.put("include", include);
        }
        return new JSONObject()
                .put("id", ID)
                .put("kind", "logs")
                .put("options", options)
                .put("ticketShareToken", "share-1")
                .put("expiresAt", "2026-10-01T10:00:00.000Z");
    }

    private static String gunzip(byte[] data) throws Exception {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class FakeSources implements GleapCaptureLogs.Sources {
        boolean consoleLogsEnabled = true;
        boolean replaysEnabled = false;
        JSONObject metaData;

        FakeSources() {
            try {
                metaData = new JSONObject().put("deviceModel", "Pixel");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public boolean consoleLogsEnabled() {
            return consoleLogsEnabled;
        }

        @Override
        public JSONArray consoleLog() {
            try {
                return new JSONArray().put(new JSONObject()
                        .put("date", "2026-09-30T10:00:00.000Z").put("priority", "ERROR").put("log", "boom"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public JSONArray networkLogs() {
            try {
                return new JSONArray().put(new JSONObject()
                        .put("date", "2026-09-30T10:01:00.000Z").put("url", "https://api.example.com").put("success", false));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public JSONObject customData() {
            try {
                return new JSONObject().put("plan", "gold");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public JSONObject metaData() {
            return metaData;
        }

        @Override
        public JSONArray customEventLog() {
            try {
                return new JSONArray().put(new JSONObject()
                        .put("name", "checkout").put("date", "2026-09-30T10:02:00.000Z"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public boolean replaysEnabled() {
            return replaysEnabled;
        }

        @Override
        public JSONObject replay() {
            try {
                return new JSONObject().put("interval", 5000).put("frames", new JSONArray());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }
}
