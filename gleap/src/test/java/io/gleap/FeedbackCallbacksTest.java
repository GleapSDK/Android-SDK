package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.FeedbackWillBeSentCallback;
import io.gleap.callbacks.OutboundSentCallback;

/**
 * The app hears about a report once: sent when the API created it, failed otherwise.
 */
public class FeedbackCallbacksTest {
    private SdkTestEnvironment sdk;
    private final List<String> events = new ArrayList<>();
    private final OnHttpResponseListener listener = new OnHttpResponseListener() {
        @Override
        public void onTaskComplete(JSONObject response) {
            events.add("listener " + response.optInt("status"));
        }
    };

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.server.respond("/uploads/sdk", 200, "{\"fileUrl\":\"https://files.example.com/s.png\"}");
        sdk.server.respond("/uploads/sdksteps", 200, "{\"fileUrls\":[]}");
        sdk.server.respond("/uploads/attachments", 200, "{\"fileUrls\":[]}");
        GleapCallbacks.getInstance().setFeedbackWillBeSentCallback(new FeedbackWillBeSentCallback() {
            @Override
            public void invoke(String message) {
                events.add("willBeSent " + message);
            }
        });
        GleapCallbacks.getInstance().setFeedbackSentCallback(new FeedbackSentCallback() {
            @Override
            public void invoke(JSONObject formData) {
                events.add("sent " + (formData != null ? formData.optString("description") : null));
            }
        });
        GleapCallbacks.getInstance().setOutboundSentCallback(new OutboundSentCallback() {
            @Override
            public void invoke(JSONObject data) {
                events.add("outboundSent " + (data != null ? data.optString("outboundId") : null));
            }
        });
        GleapCallbacks.getInstance().setFeedbackSendingFailedCallback(new FeedbackSendingFailedCallback() {
            @Override
            public void invoke(String message) {
                events.add("failed " + message);
            }
        });
        GleapBug.getInstance().setData(new JSONObject());
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    private void send(String description) throws Exception {
        GleapBug.getInstance().setData(new JSONObject().put("description", description));
        GleapBug.getInstance().setOutboundId("survey-1");
        HttpHelper task = new HttpHelper(listener, null);
        task.onPreExecute();
        JSONObject result = task.doInBackground(GleapBug.getInstance());
        task.onPostExecute(result);
    }

    private static boolean hasEvent(String name) throws Exception {
        JSONArray log = GleapBug.getInstance().getCustomEventLog();
        for (int i = 0; i < log.length(); i++) {
            if (name.equals(log.getJSONObject(i).getString("name"))) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void aCreatedReportIsReportedAsSent() throws Exception {
        sdk.server.respond("/bugs/v2", 201, "{\"shareToken\":\"share-1\"}");

        send("broken");

        assertEquals(
                "[willBeSent {\"description\":\"broken\"}, sent broken, outboundSent survey-1, listener 201]",
                events.toString());
        assertTrue(hasEvent("outbound-survey-1-submitted"));
    }

    @Test
    public void anErrorStatusIsReportedAsFailedOnly() throws Exception {
        sdk.server.respond("/bugs/v2", 500, "");

        send("broken");

        assertEquals("[willBeSent {\"description\":\"broken\"}, failed The feedback could not be sent (HTTP 500)., listener 500]",
                events.toString());
        assertTrue(!hasEvent("outbound-survey-1-submitted"));
    }

    @Test
    public void aReportThatCouldNotBeUploadedIsReportedAsFailed() throws Exception {
        sdk.server.clear("/uploads/sdk").fail("/uploads/sdk", new IOException("offline"));

        send("broken");

        assertEquals("[willBeSent {\"description\":\"broken\"}, failed The feedback could not be sent., listener 0]",
                events.toString());
    }
}
