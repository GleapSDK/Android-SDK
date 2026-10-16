package io.gleap.callbacks;

/**
 * The space an inline banner takes at the top of the screen changed.
 */
public interface BannerHeightChangedCallback {
    /**
     * @param height Height in dp from the top of the activity's content, including the status bar
     *               the banner covers in edge-to-edge apps. 0 when no inline banner is shown;
     *               floating banners report 0 as they float above the app by design.
     */
    void invoke(int height);
}
