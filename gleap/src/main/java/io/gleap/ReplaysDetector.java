package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.BitmapCompat;

import java.util.Date;
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
        //start
        handler.post(runnableCode);
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
            if (Gleap.getInstance() != null && UserSessionController.getInstance().isSessionLoaded()) {
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
            handler.postDelayed(this, replay.getInterval());
        }
    };
}
