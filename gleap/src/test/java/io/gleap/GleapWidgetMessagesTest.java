package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * What the SDK hands to the widget: the session it authenticates with and the hosts of the
 * region it talks to.
 */
public class GleapWidgetMessagesTest {
    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    @Test
    public void theSessionUpdateCarriesTheSessionAndTheRegionHosts() throws Exception {
        sdk.controller.setGleapUserSession(new GleapSessionProperties("user-1", "Ada", "ada@example.com"));
        GleapConfig.getInstance().setRegion(GleapRegion.US);

        JSONObject data = GleapWidgetMessages.sessionUpdate();

        JSONObject session = data.getJSONObject("sessionData");
        assertEquals("id-1", session.getString("gleapId"));
        assertEquals("hash-1", session.getString("gleapHash"));
        assertEquals("user-1", session.getString("userId"));
        assertEquals("ada@example.com", session.getString("email"));
        assertEquals("https://api.us.gleap.ai", data.getString("apiUrl"));
        assertEquals("sockets.us.gleap.ai", data.getString("realtimeHost"));
        assertEquals(SdkTestEnvironment.SDK_KEY, data.getString("sdkKey"));
    }

    @Test
    public void withoutARealtimeHostTheWidgetUsesItsDefault() throws Exception {
        JSONObject data = GleapWidgetMessages.sessionUpdate();

        assertEquals("https://api.eu.gleap.ai", data.getString("apiUrl"));
        assertFalse(data.has("realtimeHost"));
    }

    @Test
    public void theConfigUpdateCarriesTheRemoteConfigAndTheLanguage() throws Exception {
        GleapConfig.getInstance().initConfig(new JSONObject()
                .put("flowConfig", new JSONObject().put("color", "#123456"))
                .put("projectActions", new JSONObject().put("bug", new JSONObject())));
        GleapConfig.getInstance().setLanguage("de");

        JSONObject data = GleapWidgetMessages.configUpdate();

        assertEquals("#123456", data.getJSONObject("config").getString("color"));
        assertTrue(data.getJSONObject("actions").has("bug"));
        assertEquals("de", data.getString("overrideLanguage"));
        assertTrue(data.getBoolean("isApp"));
    }

    @Test
    public void theConfigUpdateCarriesTheColorSchemeBackground() throws Exception {
        GleapConfig.getInstance().initConfig(new JSONObject()
                .put("flowConfig", new JSONObject().put("colorScheme", "auto").put("backgroundColor", "#ffffff"))
                .put("projectActions", new JSONObject()));
        try {
            Gleap.getInstance().setColorScheme("dark", null, "#121212");

            JSONObject data = GleapWidgetMessages.configUpdate();

            assertEquals("#121212", data.getJSONObject("config").getString("backgroundColor"));
            // The cached server config is left as it is.
            assertEquals("#ffffff", GleapConfig.getInstance().getPlainConfig()
                    .getJSONObject("flowConfig").getString("backgroundColor"));
        } finally {
            GleapThemeHelper.resetForTesting();
        }

        assertEquals("#ffffff", GleapWidgetMessages.configUpdate().getJSONObject("config").getString("backgroundColor"));
    }

    @Test
    public void theFeedbackResultTellsTheWidgetWhetherTheTicketWasCreated() throws Exception {
        JSONObject sent = new JSONObject(GleapWidgetMessages.feedbackResult(new JSONObject()
                .put("status", 201).put("response", new JSONObject().put("shareToken", "share-1"))));
        assertEquals("feedback-sent", sent.getString("name"));
        assertEquals("share-1", sent.getJSONObject("data").getString("shareToken"));

        // Any 2xx is a created ticket.
        assertEquals("feedback-sent", new JSONObject(GleapWidgetMessages.feedbackResult(new JSONObject()
                .put("status", 200))).getString("name"));

        JSONObject failed = new JSONObject(GleapWidgetMessages.feedbackResult(new JSONObject().put("status", 500)));
        assertEquals("feedback-sending-failed", failed.getString("name"));
    }
}
