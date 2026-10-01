package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Who may start a capture and what the SDK announces: capture commands are only taken from the
 * messenger's own origin, request ids never leave the id format, and the capabilities follow the
 * app's switches.
 */
public class GleapCaptureTest {
    private static final String WIDGET = "https://messenger-app.gleap.io/appnew";

    @Test
    public void onlyTheMessengerOriginMayStartACapture() {
        assertTrue(GleapCapture.sameOrigin("https://messenger-app.gleap.io/appnew?lang=en&gleapId=1", WIDGET));
        assertTrue(GleapCapture.sameOrigin("https://MESSENGER-APP.gleap.io:443/other", WIDGET));

        assertFalse(GleapCapture.sameOrigin("http://messenger-app.gleap.io/appnew", WIDGET));
        assertFalse(GleapCapture.sameOrigin("https://messenger-app.gleap.io.evil.com/appnew", WIDGET));
        assertFalse(GleapCapture.sameOrigin("https://evil.com/?u=https://messenger-app.gleap.io/appnew", WIDGET));
        assertFalse(GleapCapture.sameOrigin("https://messenger-app.gleap.io@evil.com/appnew", WIDGET));
        assertFalse(GleapCapture.sameOrigin("https://messenger-app.gleap.io:8443/appnew", WIDGET));
        assertFalse(GleapCapture.sameOrigin("javascript:alert(1)", WIDGET));
        assertFalse(GleapCapture.sameOrigin("file:///android_asset/appnew.html", WIDGET));
        assertFalse(GleapCapture.sameOrigin("about:blank", WIDGET));
        assertFalse(GleapCapture.sameOrigin(null, WIDGET));
        assertFalse(GleapCapture.sameOrigin(WIDGET, null));

        // A custom frame url (setFrameUrl), e.g. a local server.
        assertTrue(GleapCapture.sameOrigin("http://10.0.2.2:8787/widget/appnew?x=1", "http://10.0.2.2:8787/widget/appnew"));
        assertFalse(GleapCapture.sameOrigin("http://10.0.2.2:8788/widget/appnew", "http://10.0.2.2:8787/widget/appnew"));
    }

    @Test
    public void requestIdsStayInsideTheirPath() {
        assertTrue(GleapCapture.isValidRequestId("65f0c0ffee0123456789abcd"));
        assertFalse(GleapCapture.isValidRequestId("../../bugs/v2"));
        assertFalse(GleapCapture.isValidRequestId("id/claim"));
        assertFalse(GleapCapture.isValidRequestId("id?x=1"));
        assertFalse(GleapCapture.isValidRequestId(""));
        assertFalse(GleapCapture.isValidRequestId(null));
        assertEquals("/v3/shared/capture-requests/abc/logs", GleapCaptureApi.requestPath("abc", "logs"));
        try {
            GleapCaptureApi.requestPath("a/b", "logs");
            throw new AssertionError("An invalid id was accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void theCapabilitiesFollowTheAppsSwitches() throws Exception {
        assertEquals(Arrays.asList("capture.screenshot", "capture.recording", "capture.logs"),
                GleapCapture.caps(true, true, true));
        // Below API 26: no recordings.
        assertEquals(Arrays.asList("capture.screenshot", "capture.logs"), GleapCapture.caps(true, false, true));
        assertEquals(Collections.singletonList("capture.logs"), GleapCapture.caps(false, true, true));
        assertEquals(Collections.emptyList(), GleapCapture.caps(false, true, false));
        assertEquals("capture.screenshot,capture.logs",
                GleapCapture.capsQueryValue(GleapCapture.caps(true, false, true)));

        JSONObject enabled = GleapCapture.capabilities(true, true, "FLUTTER", "19.0.0");
        assertEquals(1, enabled.getInt("version"));
        assertEquals("android", enabled.getString("platform"));
        assertEquals("FLUTTER", enabled.getString("sdkType"));
        assertTrue(enabled.getBoolean("screenshot"));
        assertTrue(enabled.getBoolean("recording"));
        assertEquals("frames", enabled.getString("recordingMethod"));
        assertFalse(enabled.getBoolean("microphone"));

        // setCaptureEnabled(false): nothing is offered, the widget falls back to the upload.
        JSONObject disabled = GleapCapture.capabilities(false, true, "NATIVE", "19.0.0");
        assertFalse(disabled.getBoolean("screenshot"));
        assertFalse(disabled.getBoolean("recording"));
        assertFalse(disabled.has("recordingMethod"));

        JSONObject oldDevice = GleapCapture.capabilities(true, false, "NATIVE", "19.0.0");
        assertTrue(oldDevice.getBoolean("screenshot"));
        assertFalse(oldDevice.getBoolean("recording"));
    }

    @Test
    public void aCaptureStartIsReadWithItsLimitsAndLabels() throws Exception {
        JSONObject start = new JSONObject()
                .put("requestId", "65f0c0ffee0123456789abcd")
                .put("kind", "recording")
                .put("ticketShareToken", "share-1")
                .put("options", new JSONObject().put("maxDurationSec", 900).put("attachLogs", false))
                .put("labels", new JSONObject().put("barStop", "Stopp").put("barStart", "  "));

        GleapCaptureRequest request = GleapCaptureRequest.fromStart(start);

        assertEquals("recording", request.kind);
        assertTrue(request.isRecording());
        assertEquals("share-1", request.ticketShareToken);
        assertEquals(180, request.maxDurationSec);
        assertFalse(request.attachLogs);
        assertEquals("Stopp", request.label("barStop"));
        // Blank or missing labels fall back to English.
        assertEquals("Start recording", request.label("barStart"));
        assertEquals("Cancel", request.label("barCancel"));

        assertNull(GleapCaptureRequest.fromStart(new JSONObject(start.toString()).put("kind", "any")));
        assertNull(GleapCaptureRequest.fromStart(new JSONObject(start.toString()).put("requestId", "../x")));
        assertNull(GleapCaptureRequest.fromStart(null));
    }
}
