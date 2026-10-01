package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.InitializationDoneCallback;
import io.gleap.callbacks.InitializedCallback;

/**
 * Where the SDK's requests go (region routing) and how they authenticate: the SDK key on
 * every request, the session id and hash once there is a session.
 */
public class GleapApiRequestsTest {
    private SdkTestEnvironment sdk;
    private final List<String> events = new ArrayList<>();

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    private static final OnHttpResponseListener NO_LISTENER = new OnHttpResponseListener() {
        @Override
        public void onTaskComplete(JSONObject response) {
        }
    };

    @Test
    public void theConfigIsLoadedForTheSdkKeyAndLanguage() throws Exception {
        GleapCallbacks.getInstance().setConfigLoadedCallback(new ConfigLoadedCallback() {
            @Override
            public void configLoaded(JSONObject flowConfig) {
                events.add("configLoaded " + flowConfig.optString("color"));
            }
        });
        GleapCallbacks.getInstance().setInitializedCallback(new InitializedCallback() {
            @Override
            public void initialized() {
                events.add("initialized");
            }
        });
        sdk.server.respond("/config/", 200, "{\"flowConfig\":{\"color\":\"#123456\"}}");

        JSONObject result = new ConfigLoader(NO_LISTENER).doInBackground();

        FakeGleapServer.Request request = sdk.server.last("/config/");
        assertEquals("https://api.eu.gleap.ai/config/sdk-key/?lang=en", request.getURL().toString());
        assertEquals("GET", request.getRequestMethod());
        assertEquals(200, result.getInt("status"));
        assertEquals("#123456", GleapConfig.getInstance().getColor());
        assertEquals("[configLoaded #123456, initialized]", events.toString());
    }

    @Test
    public void everyRequestFollowsTheRegionAndTheApiUrlAtRequestTime() throws Exception {
        sdk.server.respond("/config/", 200, "{\"flowConfig\":{}}");
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\"}");
        ConfigLoader loader = new ConfigLoader(NO_LISTENER);
        GleapBaseSessionService sessionService = new GleapBaseSessionService();

        Gleap.getInstance().setRegion("us");
        loader.doInBackground();
        sessionService.doInBackground();
        assertEquals("api.us.gleap.ai", sdk.server.last("/config/").getURL().getHost());
        assertEquals("https://api.us.gleap.ai/sessions", sdk.server.last("/sessions").getURL().toString());
        assertEquals("wss://ws.us.gleap.ai", sdk.webSocketConnects.get(0));

        Gleap.getInstance().setApiUrl("http://10.0.2.2:9000");
        new GleapBaseSessionService().doInBackground();
        assertEquals("http://10.0.2.2:9000/sessions", sdk.server.last("/sessions").getURL().toString());
    }

    @Test
    public void aNewSessionIsRequestedWithTheSdkKeyOnly() throws Exception {
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\"}");

        new GleapBaseSessionService().doInBackground();

        FakeGleapServer.Request request = sdk.server.last("/sessions");
        assertEquals("POST", request.getRequestMethod());
        assertEquals(SdkTestEnvironment.SDK_KEY, request.headers.get("Api-Token"));
        assertFalse(request.headers.containsKey("Gleap-Id"));
        assertFalse(request.headers.containsKey("Gleap-Hash"));
        JSONObject body = request.bodyJson();
        assertEquals("en", body.getString("lang"));
        assertEquals("android", body.getString("platform"));
        assertEquals("mobile", body.getString("deviceType"));
        assertEquals("id-1", sdk.controller.getUserSession().getId());
        assertEquals("hash-1", sdk.store.getString("session_hash", ""));
    }

    @Test
    public void aStoredSessionIsResumedWithItsIdAndHash() throws Exception {
        GleapCallbacks.getInstance().setInitializationDoneCallback(new InitializationDoneCallback() {
            @Override
            public void invoke() {
                events.add("initializationDone");
            }
        });
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-2\"}");

        GleapBaseSessionService service = new GleapBaseSessionService();
        service.doInBackground();

        FakeGleapServer.Request request = sdk.server.last("/sessions");
        assertEquals("id-1", request.headers.get("Gleap-Id"));
        assertEquals("hash-1", request.headers.get("Gleap-Hash"));
        assertEquals("hash-2", sdk.controller.getUserSession().getHash());
        assertEquals(1, sdk.webSocketConnects.size());
        assertEquals("[initializationDone]", events.toString());
    }

    @Test
    public void identifySendsTheUserAndTheUserHashForTheCurrentSession() throws Exception {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/sessions/identify", 200, "{\"gleapId\":\"id-9\",\"gleapHash\":\"hash-9\",\"userId\":\"user-1\"}");
        sdk.controller.setPendingIdentificationAction(new GleapSessionProperties("user-1", "Ada", "ada@example.com", "hmac-1"));

        new GleapIdentifyService().doInBackground();

        FakeGleapServer.Request request = sdk.server.last("/sessions/identify");
        assertEquals("https://api.eu.gleap.ai/sessions/identify", request.getURL().toString());
        assertEquals(SdkTestEnvironment.SDK_KEY, request.headers.get("Api-Token"));
        assertEquals("id-1", request.headers.get("Gleap-Id"));
        assertEquals("hash-1", request.headers.get("Gleap-Hash"));
        JSONObject body = request.bodyJson();
        assertEquals("user-1", body.getString("userId"));
        assertEquals("ada@example.com", body.getString("email"));
        assertEquals("hmac-1", body.getString("userHash"));
        assertEquals("android", body.getString("platform"));
        assertNull(sdk.controller.getPendingIdentificationAction());
        assertEquals("id-9", sdk.controller.getUserSession().getId());
        assertEquals("user-1", sdk.controller.getGleapUserSession().getUserId());
    }

    @Test
    public void identifyWaitsForTheSession() throws Exception {
        GleapSessionProperties pending = new GleapSessionProperties("user-1");
        sdk.controller.setPendingIdentificationAction(pending);

        new GleapIdentifyService().doInBackground();

        assertTrue(sdk.server.requests.isEmpty());
        assertEquals(pending, sdk.controller.getPendingIdentificationAction());
    }

    @Test
    public void contactUpdatesSendTheDataForTheCurrentSession() throws Exception {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/sessions/partialupdate", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\",\"plan\":\"pro\"}");
        GleapSessionProperties update = new GleapSessionProperties();
        update.setPlan("pro");
        sdk.controller.setPendingUpdateAction(update);

        new GleapUpdateSessionService().doInBackground();

        FakeGleapServer.Request request = sdk.server.last("/sessions/partialupdate");
        assertEquals("id-1", request.headers.get("Gleap-Id"));
        assertEquals("hash-1", request.headers.get("Gleap-Hash"));
        JSONObject body = request.bodyJson();
        assertEquals("pro", body.getJSONObject("data").getString("plan"));
        assertEquals("android", body.getJSONObject("data").getString("platform"));
        assertEquals("android", body.getString("type"));
        assertEquals(gleap.io.gleap.BuildConfig.VERSION_NAME, body.getString("sdkVersion"));
        assertEquals("pro", sdk.controller.getGleapUserSession().getPlan());
    }

    @Test
    public void bugReportsAndTheirUploadsCarryTheSdkKeyAndTheSession() throws Exception {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/uploads/sdk", 200, "{\"fileUrl\":\"https://files.example.com/screenshot.png\"}");
        sdk.server.respond("/uploads/sdksteps", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/uploads/attachments", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/bugs/v2", 201, "{\"shareToken\":\"share-1\"}");
        GleapBug.getInstance().setScreenshot(SdkTestEnvironment.screenshot());

        JSONObject result = new HttpHelper(NO_LISTENER, null).doInBackground(GleapBug.getInstance());

        assertEquals(201, result.getInt("status"));
        assertEquals("share-1", result.getJSONObject("response").getString("shareToken"));
        for (String path : new String[]{"/uploads/sdk", "/uploads/attachments", "/bugs/v2"}) {
            FakeGleapServer.Request request = sdk.server.last(path);
            assertNotNull(path, request);
            assertEquals(path, "api.eu.gleap.ai", request.getURL().getHost());
            assertEquals(path, SdkTestEnvironment.SDK_KEY, request.headers.get("api-token"));
            assertEquals(path, "id-1", request.headers.get("gleap-id"));
            assertEquals(path, "hash-1", request.headers.get("gleap-hash"));
        }
        assertEquals("https://files.example.com/screenshot.png",
                sdk.server.last("/bugs/v2").bodyJson().getString("screenshotUrl"));
    }

    @Test
    public void aTicketLeavesNoScreenshotFilesBehind() throws Exception {
        final File cacheDir = java.nio.file.Files.createTempDirectory("gleap-cache").toFile();
        Context context = new ContextWrapper(null) {
            @Override
            public File getCacheDir() {
                return cacheDir;
            }
        };
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/uploads/sdk", 200, "{\"fileUrl\":\"https://files.example.com/screenshot.png\"}");
        sdk.server.respond("/uploads/sdksteps", 200, "{\"fileUrls\":[\"https://files.example.com/frame.png\"]}");
        sdk.server.respond("/uploads/attachments", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/bugs/v2", 201, "{}");
        GleapBug.getInstance().setScreenshot(SdkTestEnvironment.screenshot());
        GleapBug.getInstance().getReplay().addScreenshot(SdkTestEnvironment.screenshot(), "CheckoutActivity");

        JSONObject result = new HttpHelper(NO_LISTENER, context).doInBackground(GleapBug.getInstance());

        assertEquals(201, result.getInt("status"));
        assertTrue(sdk.server.last("/uploads/sdk").bodyText().contains(".png"));
        assertTrue(sdk.server.last("/uploads/sdksteps").bodyText().contains(".png"));
        assertEquals(0, cacheDir.listFiles().length);
        cacheDir.delete();
    }

    @Test
    public void eventsAreSentWithTheSession() throws Exception {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/sessions/ping", 200, "");

        GleapEventService.PingResponse response = GleapEventService.postEvents(sdk.controller.getUserSession(),
                new JSONArray().put(new JSONObject().put("name", "signup")));

        assertEquals(200, response.status);
        FakeGleapServer.Request request = sdk.server.last("/sessions/ping");
        assertEquals("https://api.eu.gleap.ai/sessions/ping", request.getURL().toString());
        assertEquals(SdkTestEnvironment.SDK_KEY, request.headers.get("api-token"));
        assertEquals("id-1", request.headers.get("gleap-id"));
        assertEquals("hash-1", request.headers.get("gleap-hash"));
        JSONObject body = request.bodyJson();
        assertEquals("signup", body.getJSONArray("events").getJSONObject(0).getString("name"));
        // No WebSocket is connected here: the server answers the ping with what it would push.
        assertFalse(body.getBoolean("ws"));
    }

    @Test
    public void everyRequestTimesOutAndTicketsAndUploadsGetLongerToAnswer() throws Exception {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/config/", 200, "{\"flowConfig\":{}}");
        sdk.server.respond("/sessions", 200, "{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\"}");
        sdk.server.respond("/sessions/ping", 200, "");
        sdk.server.respond("/uploads/sdk", 200, "{\"fileUrl\":\"https://files.example.com/screenshot.png\"}");
        sdk.server.respond("/uploads/sdksteps", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/uploads/attachments", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/bugs/v2", 201, "{}");
        GleapBug.getInstance().setScreenshot(SdkTestEnvironment.screenshot());

        new ConfigLoader(NO_LISTENER).doInBackground();
        new GleapBaseSessionService().doInBackground();
        GleapEventService.postEvents(sdk.controller.getUserSession(), new JSONArray());
        new HttpHelper(NO_LISTENER, null).doInBackground(GleapBug.getInstance());

        List<String> paths = new ArrayList<>();
        for (FakeGleapServer.Request request : sdk.server.requests) {
            String path = request.getURL().getPath();
            paths.add(path);
            boolean carriesFiles = path.startsWith("/uploads/") || path.equals("/bugs/v2");
            assertEquals(path, 15000, request.getConnectTimeout());
            assertEquals(path, carriesFiles ? 60000 : 30000, request.getReadTimeout());
        }
        assertTrue(paths.toString(), paths.containsAll(java.util.Arrays.asList(
                "/config/sdk-key/", "/sessions", "/sessions/ping", "/uploads/sdk", "/bugs/v2")));
    }

    @Test
    public void aFailedPingReportsItsStatusAndRetryAfter() throws Exception {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respondWithHeader("/sessions/ping", 503, "{\"status\":\"overloaded\"}", "Retry-After", "2");

        GleapEventService.PingResponse response = GleapEventService.postEvents(sdk.controller.getUserSession(), new JSONArray());

        assertEquals(503, response.status);
        assertEquals("2", response.retryAfter);
        assertFalse(response.isDelivered());
    }

    @Test(expected = java.io.IOException.class)
    public void aPingWithoutAConnectionThrows() throws Exception {
        sdk.storeSession("id-1", "hash-1");
        sdk.server.fail("/sessions/ping", new java.net.ConnectException("offline"));

        GleapEventService.postEvents(sdk.controller.getUserSession(), new JSONArray());
    }
}
