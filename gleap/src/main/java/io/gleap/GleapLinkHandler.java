package io.gleap;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

/**
 * Links opened from the widget, banners, modals and the app: gleap: smart links open the
 * matching widget screen, everything else goes to the app's link handler or the browser.
 */
final class GleapLinkHandler {
    private GleapLinkHandler() {
    }

    static void handleLink(final String url) {
        if (url == null || url.length() == 0) {
            return;
        }

        GleapMainThread.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Smartlink, handle internally.
                if (url.contains("gleap:")) {
                    if (GleapDetectorUtil.isWidgetOpen()) {
                        // Try again later.
                        Gleap.getInstance().handleLink(url);
                    } else {
                        Gleap.getInstance().handleGleapLink(url);
                    }
                    return;
                }

                // Use custom link handler.
                if (GleapCallbacks.getInstance().getCustomLinkHandler() != null) {
                    GleapCallbacks.getInstance().getCustomLinkHandler().invoke(url);
                    return;
                }

                // If URL doesn't start with http or https, mailto or tel, close the widget.
                if (!url.startsWith("http") && !url.startsWith("https") && !url.startsWith("mailto")
                        && !url.startsWith("tel")) {
                    Gleap.getInstance().close();
                }

                // Open externally.
                openUrlExternally(url);
            }
        }, 250);
    }

    /**
     * Opens the widget screen of a smart link, e.g. {@code gleap://article/<id>}.
     */
    static void handleGleapLink(String href) {
        try {
            Gleap gleap = Gleap.getInstance();
            String[] urlParts = href.split("/");
            String type = urlParts[2];

            switch (type) {
                case "article":
                    gleap.openHelpCenterArticle(urlParts[3], true);
                    break;
                case "collection":
                    gleap.openHelpCenterCollection(urlParts[3], true);
                    break;
                case "survey":
                    gleap.showSurvey(urlParts[3]);
                    break;
                case "bot":
                    gleap.startBot(urlParts[3], true);
                    break;
                case "news":
                    gleap.openNewsArticle(urlParts[3], true);
                    break;
                case "flow":
                    gleap.startFeedbackFlow(urlParts[3], true);
                    break;
                case "checklist":
                    gleap.startChecklist(urlParts[3], true);
                    break;
                case "tour":
                    GleapLog.w("Product tours are not supported on mobile.");
                    break;
                default:
                    GleapLog.w("Invalid type provided in href: " + href);
                    break;
            }
        } catch (Exception e) {
            GleapErrors.report(e, "handleGleapLink");
        }
    }

    private static void openUrlExternally(String url) {
        try {
            Activity local = ActivityUtil.getCurrentActivity();
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            local.startActivity(browserIntent);
        } catch (Exception e) {
            GleapErrors.report(e, "openUrlExternally");
        }
    }
}
