package io.gleap.callbacks;

/**
 * Can be called from the webview
 */
public interface CustomActionCallback {
    void invoke(String message, String shareToken);
}

