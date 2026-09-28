package io.gleap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Links from the widget, banners and modals: their pages (and every frame in them) can ask the
 * SDK to open a link, so the schemes that reach app data, run script or build intents are
 * never opened.
 */
public class GleapExternalLinksTest {
    @Test
    public void webLinksMailPhoneSmartLinksAndAppLinksMayBeOpened() {
        String[] allowed = {
                "https://help.example.com/articles/1",
                "http://example.com",
                "HTTPS://EXAMPLE.COM/",
                "mailto:support@example.com",
                "tel:+43123456",
                "gleap://article/123",
                "myapp://orders/42",
                "vnd.myapp+orders://open",
                "/relative/path",
        };
        for (String url : allowed) {
            assertTrue(url, GleapExternalLinks.mayOpen(url));
        }
    }

    @Test
    public void intentFileContentJavascriptAndDataLinksAreNeverOpened() {
        String[] blocked = {
                "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end",
                "INTENT:#Intent;action=android.intent.action.VIEW;end",
                "file:///data/data/com.example/shared_prefs/prefs.xml",
                "content://com.example.provider/secret",
                "javascript:alert(1)",
                " JavaScript:alert(1)",
                "java\tscript:alert(1)",
                "\nfile:///sdcard/Download",
                "data:text/html,<script>alert(1)</script>",
        };
        for (String url : blocked) {
            assertFalse(url, GleapExternalLinks.mayOpen(url));
        }
        assertFalse(GleapExternalLinks.mayOpen(null));
    }
}
