package io.gleap;


/**
 * Called if Gleap is not initialised but used.
 */
public class GleapSessionNotInitialisedException extends Exception {
    public GleapSessionNotInitialisedException() {
        super("Gleap: Gleap Session not initialized.");
    }
}
