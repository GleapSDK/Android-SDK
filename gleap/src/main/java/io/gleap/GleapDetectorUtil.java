package io.gleap;

import android.app.Activity;
import android.app.Application;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

class GleapDetectorUtil {
    // True while the widget is open (or opening): the activation methods are paused meanwhile.
    private static volatile boolean widgetOpen = false;

    public static void resumeAllDetectors() {
        widgetOpen = false;
        if (GleapCaptureCoordinator.isSessionActive()) {
            // The widget closed for a capture: the activation methods wait until it is done.
            return;
        }
        for (GleapDetector detector : GleapConfig.getInstance().getGestureDetectors()) {
            detector.resume();
        }
    }

    /**
     * Pauses the activation methods (and replays) while a capture runs, without marking the
     * widget open.
     */
    static void pauseDetectorsForCapture() {
        for (GleapDetector detector : GleapConfig.getInstance().getGestureDetectors()) {
            detector.pause();
        }
    }

    /**
     * Resumes them after a capture, unless the widget is open.
     */
    static void resumeDetectorsAfterCapture() {
        if (widgetOpen) {
            return;
        }
        for (GleapDetector detector : GleapConfig.getInstance().getGestureDetectors()) {
            detector.resume();
        }
    }

    public static void stopAllDetectors() {
        widgetOpen = true;

        for (GleapDetector detector : GleapConfig.getInstance().getGestureDetectors()) {
            detector.pause();
        }
    }

    public static List<GleapDetector> initDetectors(Application application, GleapActivationMethod[] activationMethods) {
        List<GleapDetector> detectorList = new LinkedList<>();

        List<GleapActivationMethod> methods;
        if (GleapConfig.getInstance().getPrioritizedActivationMethods().size() > 0) {
            methods = new LinkedList<>(GleapConfig.getInstance().getPrioritizedActivationMethods());
        } else if (activationMethods != null) {
            methods = Arrays.asList(activationMethods);
        } else {
            methods = Collections.emptyList();
        }

        for (GleapActivationMethod activationMethod : methods) {
            if (activationMethod != null) {
                if (activationMethod == GleapActivationMethod.SHAKE) {
                    GleapDetector detector = new ShakeGestureDetector(application);
                    detector.initialize();
                    detectorList.add(detector);
                }
                if (activationMethod == GleapActivationMethod.SCREENSHOT) {
                    ScreenshotGestureDetector screenshotGestureDetector;
                    screenshotGestureDetector = new ScreenshotGestureDetector(application);
                    screenshotGestureDetector.initialize();
                    detectorList.add(screenshotGestureDetector);
                }
            }
        }
        return detectorList;
    }

    public static void clearAllDetectors() {
        for (GleapDetector activationMethod : GleapConfig.getInstance().getGestureDetectors()) {
            activationMethod.unregister();
        }
    }

    public static boolean isWidgetOpen() {
        return widgetOpen;
    }
}
