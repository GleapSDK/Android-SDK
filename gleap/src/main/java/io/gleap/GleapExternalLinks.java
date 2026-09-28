package io.gleap;

import android.app.Activity;
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
                if (browserIntent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(browserIntent);
                }
                return true;
            }
        } catch (Error | Exception ignore) {
        }
        return false;
    }
}
