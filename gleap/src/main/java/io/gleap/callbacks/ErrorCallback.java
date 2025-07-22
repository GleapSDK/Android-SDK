package io.gleap.callbacks;

/**
 * Callback interface for handling errors and exceptions in the Gleap SDK.
 * This allows customers to receive and process errors that occur within the SDK.
 */
public interface ErrorCallback {
    /**
     * Called when an error or exception occurs in the Gleap SDK.
     * 
     * @param error The error or exception that occurred
     * @param context Optional context information about where the error occurred
     */
    void onError(Throwable error, String context);
} 