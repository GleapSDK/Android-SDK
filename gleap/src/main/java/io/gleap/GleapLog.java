package io.gleap;

import android.util.Log;

/**
 * The SDK's own logcat output, all under the tag "Gleap".
 */
final class GleapLog {
    static final String TAG = "Gleap";

    private GleapLog() {
    }

    static void i(String message) {
        Log.i(TAG, message);
    }

    static void w(String message) {
        Log.w(TAG, message);
    }

    static void w(String message, Throwable error) {
        Log.w(TAG, message, error);
    }

    static void e(String message) {
        Log.e(TAG, message);
    }

    static void e(String message, Throwable error) {
        Log.e(TAG, message, error);
    }
}
