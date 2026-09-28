package io.gleap;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

/**
 * Navigations inside the SDK's WebViews (widget, banner, modal): pages of the own host stay in
 * the WebView, everything else opens outside the SDK.
 */
final class GleapExternalLinks {
    private GleapExternalLinks() {
    }

    /**
     * For {@code WebViewClient.shouldOverrideUrlLoading}: opens urls that are not under
     * {@code ownUrl} with the app that handles them.
     *
     * @return whether the WebView must not load the url itself
     */
    static boolean openOutside(Activity activity, String url, String ownUrl) {
        try {
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
