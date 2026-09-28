package io.gleap;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Navigations inside the SDK's WebViews (widget, banner, modal): pages of the own host stay in
 * the WebView, everything else opens outside the SDK.
 * <p>
 * Links from web content never open the schemes in {@link #BLOCKED_SCHEMES}: the SDK's
 * bridges are reachable from every frame of those pages, including third-party ones.
 */
final class GleapExternalLinks {
    // Schemes that reach the app's own data (file:, content:), run script (javascript:,
    // data:) or describe arbitrary intents (intent:). http(s), mailto, tel, gleap: and other
    // app schemes stay allowed: help-center links may deep-link into the app.
    private static final Set<String> BLOCKED_SCHEMES =
            new HashSet<>(Arrays.asList("intent", "file", "content", "javascript", "data"));

    private GleapExternalLinks() {
    }

    /**
     * Whether a link from web content (the widget, a banner, a modal) may be opened.
     */
    static boolean mayOpen(String url) {
        if (url == null) {
            return false;
        }
        String scheme = scheme(url);
        return scheme == null || !BLOCKED_SCHEMES.contains(scheme);
    }

    /**
     * The url's scheme in lower case, or null when it has none. Whitespace and control
     * characters around it are ignored, as browsers do.
     */
    static String scheme(String url) {
        String trimmed = url.replaceAll("[\\t\\n\\r]", "");
        int start = 0;
        while (start < trimmed.length() && trimmed.charAt(start) <= ' ') {
            start++;
        }
        int colon = trimmed.indexOf(':', start);
        if (colon <= start) {
            return null;
        }
        // RFC 3986: ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
        for (int i = start; i < colon; i++) {
            char c = trimmed.charAt(i);
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            boolean other = (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.';
            if (!letter && !(i > start && other)) {
                return null;
            }
        }
        return trimmed.substring(start, colon).toLowerCase(Locale.ROOT);
    }

    /**
     * For {@code WebViewClient.shouldOverrideUrlLoading}: opens urls that are not under
     * {@code ownUrl} with the app that handles them.
     *
     * @return whether the WebView must not load the url itself
     */
    static boolean openOutside(Activity activity, String url, String ownUrl) {
        try {
            if (!mayOpen(url)) {
                // Neither opened nor loaded in the WebView.
                GleapLog.w("Blocked a link with a disallowed scheme");
                return true;
            }
            if (!url.contains(ownUrl)) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                // No resolveActivity check: since Android 11 it finds nothing for most links
                // unless the app declares <queries>, so the links were silently dropped.
                try {
                    activity.startActivity(browserIntent);
                } catch (ActivityNotFoundException e) {
                    GleapLog.w("No app can open the link");
                }
                return true;
            }
        } catch (Error | Exception ignore) {
        }
        return false;
    }
}
