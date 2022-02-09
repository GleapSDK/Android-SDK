package io.gleap;


/**
 * Called if Gleap is not initialised but used.
 */
public class GleapApplicationNull extends Exception {
    public GleapApplicationNull() {
        super("Application must be set.");
    }
}
