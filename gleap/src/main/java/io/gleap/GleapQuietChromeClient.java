package io.gleap;

import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * Chrome client of the banner and modal WebViews: their pages show no JavaScript dialogs and
 * their console output stays out of logcat.
 * <p>
 * A suppressed dialog is answered as dismissed (alert() returns, confirm() returns false,
 * prompt() returns null): the WebView waits for an answer, so without one the page stopped
 * responding.
 */
class GleapQuietChromeClient extends WebChromeClient {
    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        result.cancel();
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        result.cancel();
        return true;
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        result.cancel();
        return true;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        return true;
    }
}
