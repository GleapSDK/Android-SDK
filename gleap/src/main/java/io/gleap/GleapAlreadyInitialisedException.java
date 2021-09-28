package io.gleap;


/**
 * Called if Gleap is not initialised but used.
 */
public class GleapAlreadyInitialisedException extends Exception {
    public GleapAlreadyInitialisedException(String s) {
        super(s);
    }
}
