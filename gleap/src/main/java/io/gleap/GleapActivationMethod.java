package io.gleap;

/**
 * Possible activation methods for {@link Gleap#setActivationMethods}. Only {@link #SHAKE} and
 * {@link #SCREENSHOT} open the widget; pass {@link #NONE} alone to turn both off. An empty array
 * does not turn them off for good: the dashboard settings apply again when the config loads.
 */
public enum GleapActivationMethod {
    /** No activation method: on its own it turns shake and screenshot off. */
    NONE,
    /** Shaking the device opens the widget. */
    SHAKE,
    /** Not supported on Android: it does nothing. */
    THREE_FINGER_DOUBLE_TAB,
    /** Taking a screenshot opens the widget. */
    SCREENSHOT,
    /**
     * Does nothing here: the feedback button follows the dashboard settings, and
     * {@link Gleap#showFeedbackButton(boolean)} shows or hides it from code.
     */
    FAB
}
