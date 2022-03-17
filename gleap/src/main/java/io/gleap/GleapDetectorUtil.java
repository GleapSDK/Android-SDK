package io.gleap;

import android.app.Activity;
import android.app.Application;

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
        if(GleapConfig.getInstance().getPriorizedGestureDetectors().size() > 0) {
            activationMethods = (GleapActivationMethod[]) GleapConfig.getInstance().getPriorizedGestureDetectors().toArray();
        }
        for (GleapActivationMethod activationMethod : activationMethods) {
            if(activationMethod != null) {
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
        for (GleapDetector activationMethod :  GleapConfig.getInstance().getGestureDetectors()) {
            activationMethod.unregister();
        }
    }

    public static boolean isIsRunning() {
        return isRunning;
    }
}
