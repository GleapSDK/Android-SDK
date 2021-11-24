package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.BitmapCompat;

import java.util.Date;

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
        handler = new Handler();
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

    private final Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            if(Gleap.getInstance() != null && UserSessionController.getInstance().isSessionLoaded()) {
                try {
                    Activity activity = ActivityUtil.getCurrentActivity();
                    if (activity != null) {
                        Bitmap bitmap = null;

                        bitmap = ScreenshotUtil.takeScreenshot();

                        if (bitmap != null) {
                            String screenName = "MainActivity";
                            ViewGroup viewGroup = (ViewGroup) ((ViewGroup) activity
                                    .findViewById(android.R.id.content)).getChildAt(0);
                            if (viewGroup != null) {
                                viewGroup.setOnTouchListener(new View.OnTouchListener() {
                                    @Override
                                    public boolean onTouch(View v, MotionEvent event) {
                                        if (replay != null) {
                                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                                replay.addInteractionToCurrentReplay(new Interaction(event.getX(), event.getY(), new Date(), INTERACTIONTYPE.TD));
                                            }
                                            if (event.getAction() == MotionEvent.ACTION_UP) {
                                                replay.addInteractionToCurrentReplay(new Interaction(event.getX(), event.getY(), new Date(), INTERACTIONTYPE.TU));
                                            }
                                        }
                                        return true;
                                    }
                                });
                            }
                            screenName = activity.getClass().getSimpleName();
                            replay.addScreenshot(bitmap, screenName);

                        }


                    }
                } catch (GleapSessionNotInitialisedException gleapSessionNotInitialisedException) {
                    gleapSessionNotInitialisedException.printStackTrace();
                }
            }
            handler.postDelayed(this, replay.getInterval());
        }
    };
}
