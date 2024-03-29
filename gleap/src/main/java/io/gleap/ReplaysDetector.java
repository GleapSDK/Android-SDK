package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutionException;

class ReplaysDetector extends GleapDetector {
    private Replay replay;
    private Handler handler;

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
        handler.post(runnableCode);
    }

    @Override
    public void pause() {
        handler.removeCallbacks(runnableCode);
    }

    @Override
    public void unregister() {
        handler.removeCallbacks(runnableCode);
    }

    private final Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            try {
                if (Gleap.getInstance() != null && GleapSessionController.getInstance().isSessionLoaded()) {
                    try {
                        Activity activity = ActivityUtil.getCurrentActivity();

                        if (activity != null) {
                            String screenName = activity.getClass().getSimpleName();
                            if (!screenName.equals("GleapMainActivity")) {
                                ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                                    @Override
                                    public void getImage(Bitmap bitmap) {
                                        if (bitmap != null) {
                                            replay.addScreenshot(bitmap, screenName);
                                            handler.postDelayed(runnableCode, replay.getInterval());
                                        }
                                    }
                                });
                            }
                        }
                    } catch (GleapSessionNotInitialisedException | ExecutionException gleapSessionNotInitialisedException) {
                        gleapSessionNotInitialisedException.printStackTrace();
                    } catch (InterruptedException e) {
                    }
                }
            }catch (Exception ex) {}
        }
    };
}
