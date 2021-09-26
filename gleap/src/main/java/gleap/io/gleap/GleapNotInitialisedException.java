package gleap.io.gleap;


/**
 * Called if Gleap is not initialised but used.
 */
public class GleapNotInitialisedException extends Exception {
    public GleapNotInitialisedException(String s) {
        super(s);
    }
}
