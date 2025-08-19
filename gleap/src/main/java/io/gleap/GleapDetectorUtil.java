package io.gleap;

import android.app.Activity;
import android.app.Application;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

class GleapDetectorUtil {
    private static boolean isRunning = false;

    public static void resumeAllDetectors() {
        isRunning = false;
        for (GleapDetector detector : GleapConfig.getInstance().getGestureDetectors()) {
            detector.resume();
        }
    }

    public static void stopAllDetectors() {
        isRunning = true;

        for (GleapDetector detector : GleapConfig.getInstance().getGestureDetectors()) {
            detector.pause();
        }
    }

    public static List<GleapDetector> initDetectors(Application application, GleapActivationMethod[] activationMethods) {
        List<GleapDetector> detectorList = new LinkedList<>();

        List<GleapActivationMethod> methods;
        if (GleapConfig.getInstance().getPriorizedGestureDetectors().size() > 0) {
            methods = new LinkedList<>(GleapConfig.getInstance().getPriorizedGestureDetectors());
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
                if (activationMethod == GleapActivationMethod.FAB) {
                    FABGesture fabGesture = new FABGesture(application);
                    fabGesture.initialize();
                    detectorList.add(fabGesture);
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

    public static boolean isIsRunning() {
        return isRunning;
    }

    public static GleapDetector getDetectorByClassName(String name) {
        for (int i = 0; i < GleapConfig.getInstance().getGestureDetectors().size(); i++) {
            GleapDetector detector = GleapConfig.getInstance().getGestureDetectors().get(i);
            if (detector.getClass().getSimpleName().equals(name)) {
                return detector;
            }
        }
        return null;
    }
}
