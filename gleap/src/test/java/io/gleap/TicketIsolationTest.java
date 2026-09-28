package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * What one ticket carries (its outbound, type, form data, excluded data, a crash report's
 * priority) never ends up in the next ticket or crash report.
 */
public class TicketIsolationTest {
    private static final OnHttpResponseListener NO_LISTENER = new OnHttpResponseListener() {
        @Override
        public void onTaskComplete(JSONObject response) {
        }
    };

    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        sdk.server.respond("/uploads/sdk", 200, "{\"fileUrl\":\"https://files.example.com/s.png\"}");
        sdk.server.respond("/uploads/sdksteps", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/uploads/attachments", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/bugs/v2", 201, "{}");
        GleapBug.getInstance().setCustomData("plan", "pro");
        GleapBug.getInstance().setScreenshot(SdkTestEnvironment.screenshot());
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    // What the widget sets before it sends a ticket (see GleapWidgetBridge.sendFeedback).
    private void submitFromWidget(String type, String outboundId, JSONObject formData, JSONObject excludeData) {
        GleapBug.getInstance().setType(type);
        if (outboundId != null) {
            GleapBug.getInstance().setOutboundId(outboundId);
            GleapBug.getInstance().setSpamToken("spam-1");
        }
        GleapBug.getInstance().setData(formData);
        if (excludeData != null) {
            GleapConfig.getInstance().setStripModel(excludeData);
        }
        HttpHelper.send(NO_LISTENER, null);
    }

    private JSONObject lastTicket() throws Exception {
        List<FakeGleapServer.Request> tickets = sdk.server.requestsTo("/bugs/v2");
        return tickets.get(tickets.size() - 1).bodyJson();
    }

    private static int eventCount(String name) throws Exception {
        JSONArray log = GleapBug.getInstance().getCustomEventLog();
        int count = 0;
        for (int i = 0; i < log.length(); i++) {
            if (name.equals(log.getJSONObject(i).getString("name"))) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void aCrashReportAfterASurveyIsNotPostedAsTheSurveyAnswer() throws Exception {
        submitFromWidget("SURVEY", "survey-1", new JSONObject().put("rating", 5),
                new JSONObject().put("customData", true));
        assertEquals("survey-1", lastTicket().getString("outbound"));

        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", Gleap.SEVERITY.HIGH);

        JSONObject crash = lastTicket();
        assertEquals("bugreporting", crash.getString("outbound"));
        assertFalse(crash.has("spamToken"));
        assertEquals("CRASH", crash.getString("type"));
        assertEquals("{\"description\":\"Checkout failed\"}", crash.getJSONObject("formData").toString());
        assertEquals("pro", crash.getJSONObject("customData").getString("plan"));
        assertEquals(1, eventCount("outbound-survey-1-submitted"));
    }

    @Test
    public void aWidgetTicketAfterACrashReportHasItsOwnPriorityAndData() throws Exception {
        SilentBugReportUtil.createSilentBugReport(null, "Checkout failed", Gleap.SEVERITY.HIGH);

        submitFromWidget("BUG", null, new JSONObject().put("description", "The button is broken"), null);

        JSONObject ticket = lastTicket();
        assertEquals("MEDIUM", ticket.getString("priority"));
        assertEquals("false", ticket.getString("isSilent"));
        assertEquals("BUG", ticket.getString("type"));
        assertEquals("The button is broken", ticket.getJSONObject("formData").getString("description"));
        // The crash report's exclusions (screenshot and replay) are not applied.
        assertTrue(ticket.has("screenshotUrl"));
        assertTrue(ticket.has("replay"));
    }

    @Test
    public void anExclusionOnlyAppliesToTheTicketItCameWith() throws Exception {
        submitFromWidget("BUG", null, new JSONObject(), new JSONObject().put("customData", true));
        assertFalse(lastTicket().has("customData"));

        submitFromWidget("BUG", null, new JSONObject(), null);
        assertTrue(lastTicket().has("customData"));
    }

    @Test
    public void aTicketWithoutAnOutboundIsABugReportEvenAfterASurvey() throws Exception {
        submitFromWidget("SURVEY", "survey-1", new JSONObject().put("rating", 5), null);

        submitFromWidget("BUG", null, new JSONObject().put("description", "The button is broken"), null);

        JSONObject ticket = lastTicket();
        assertEquals("bugreporting", ticket.getString("outbound"));
        assertFalse(ticket.has("spamToken"));
        assertEquals(1, eventCount("outbound-survey-1-submitted"));
    }
}
