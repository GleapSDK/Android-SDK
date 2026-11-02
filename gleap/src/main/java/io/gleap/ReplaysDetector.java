package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

class ReplaysDetector extends GleapDetector {
    private Replay replay;
    private Handler handler;
    private volatile boolean active = false;

    /**
     * Abstract class for Detectors. All implemented detectors must extend
     * this class.
     *
     * @param application application for access app
     */
    public ReplaysDetector(Application application) {
        super(application);

    }

    @Override
    public void initialize() {
        replay = GleapBug.getInstance().getReplay();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void resume() {
        // One frame loop at a time: a resume while it already runs does not start a second one.
        handler.removeCallbacks(runnableCode);
        active = true;
        handler.post(runnableCode);
    }

    @Override
    public void pause() {
        active = false;
        handler.removeCallbacks(runnableCode);
    }

    @Override
    public void unregister() {
        active = false;
        handler.removeCallbacks(runnableCode);
    }

    private final Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            try {
                if (Gleap.getInstance() != null && GleapSessionController.getInstance().isSessionLoaded()) {
                    Activity activity = ActivityUtil.getCurrentActivity();

                    if (activity != null) {
                        String screenName = activity.getClass().getSimpleName();
                        if (!ActivityUtil.isGleapActivity(activity)) {
                            ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                                @Override
                                public void getImage(Bitmap bitmap) {
                                    // A frame that could not be taken is skipped; the capture goes on.
                                    if (bitmap != null) {
                                        replay.addScreenshot(bitmap, screenName);
                                    }
                                    // A frame still being taken when the detector was paused or
                                    // replaced (setActivationMethods) arrives late: it must not
                                    // restart the loop or run a second one beside the new one.
                                    if (active) {
                                        handler.removeCallbacks(runnableCode);
                                        handler.postDelayed(runnableCode, replay.getInterval());
                                    }
                                }
                            });
                        }
                    }
                }
            }catch (Exception ex) {}
        }
    };
}
