package io.gleap;

import android.app.Application;

import java.util.LinkedList;
import java.util.List;

/**
 * Starts the SDK: {@link Gleap#initialize(String, Application)} loads the remote config and the
 * session; once the config is there the activation methods, the screenshot taker and the
 * lifecycle tracking are set up.
 */
final class GleapInitializer {
    private static Application application;
    private static boolean initialized = false;

    private GleapInitializer() {
    }

    static Application getApplication() {
        return application;
    }

    static void initialize(String sdkKey, Application app) {
        if (sdkKey == null || sdkKey.trim().isEmpty()) {
            GleapErrors.report(new IllegalArgumentException("Gleap SDK key is missing or empty."), "initialize");
            return;
        }

        if (initialized) {
            return;
        }

        try {
            application = app;
            GleapActivityTracker.register(app);
            GleapConfig.getInstance().setSdkKey(sdkKey.trim());
            // Follow the app's dark / light mode for the widget color scheme.
            GleapThemeHelper.getInstance().start(app);
            initialized = true;
            GleapSessionController.initialize(app);
            new Gleap.GleapListener();
            GleapConnectivityManager.getInstance().register(app.getApplicationContext());
        } catch (Error | Exception error) {
            GleapErrors.report(error, "initialize");
        }
    }

    /**
     * Loads the remote config (reported to {@code configListener}) and the session.
     */
    static void startLoading(OnHttpResponseListener configListener) {
        try {
            new ConfigLoader(configListener).executeOnExecutor(GleapExecutor.SERIAL, GleapBug.getInstance());

            GleapBaseSessionService sessionLoader = new GleapBaseSessionService();
            sessionLoader.executeOnExecutor(GleapExecutor.SERIAL);
        } catch (Error | Exception error) {
            GleapErrors.report(error, "GleapListener constructor");
        }
    }

    /**
     * The remote config was loaded (or failed to load): sets up the SDK with the activation
     * methods it enables.
     */
    static void onConfigLoaded() {
        try {
            GleapConfig config = GleapConfig.getInstance();

            List<GleapActivationMethod> activationMethods = new LinkedList<>();
            if (config.isActivationMethodShake()) {
                activationMethods.add(GleapActivationMethod.SHAKE);
            }

            if (config.isActivationMethodScreenshotGesture()) {
                activationMethods.add(GleapActivationMethod.SCREENSHOT);
            }

            if (config.isActivationMethodFeedbackButton()) {
                activationMethods.add(GleapActivationMethod.FAB);
            }

            Gleap.getInstance();
            initGleap(GleapConfig.getInstance().getSdkKey(),
                    activationMethods.toArray(new GleapActivationMethod[0]), application);
        } catch (Error | Exception error) {
            GleapErrors.report(error, "GleapListener onTaskComplete");
        }
    }

    private static void initGleap(String sdkKey, GleapActivationMethod[] activationMethods, Application app) {
        try {
            application = app;
            GleapWidgetLauncher.screenshotTaker = new ScreenshotTaker();
            GleapConfig.getInstance().setSdkKey(sdkKey);

            GleapBug.getInstance().setPhoneMeta(new PhoneMeta(app.getApplicationContext()));

            // Start the activation methods.
            List<GleapDetector> detectorList = GleapDetectorUtil.initDetectors(app, activationMethods);

            if (GleapConfig.getInstance().isEnableReplays()) {
                ReplaysDetector replaysDetector = new ReplaysDetector(app);
                replaysDetector.initialize();
                detectorList.add(replaysDetector);
            }

            GleapActivityManager.getInstance().start(app);

            GleapConfig.getInstance().setGestureDetectors(detectorList);
            GleapDetectorUtil.resumeAllDetectors();
        } catch (Exception error) {
            GleapErrors.report(error, "initGleap");
        }
    }
}
