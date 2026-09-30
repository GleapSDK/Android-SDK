package io.gleap;

import android.graphics.Bitmap;

import java.util.Date;
import java.util.LinkedList;

class Replay {
    private LinkedList<ScreenshotReplay> screenshots;
    private final int interval;
    private int numberOfScreenshots;

    /**
     * The timespan of the replay is calculated with numberOfScreenshots * tick. (result in ms)
     *
     * @param numberOfScreenshots number of screenshots, after end reached, the old ones are overridden.
     * @param interval            value in ms
     */
    public Replay(int numberOfScreenshots, int interval) {
        screenshots = new LinkedList<>();
        this.numberOfScreenshots = numberOfScreenshots;
        this.interval = interval;
    }

    /**
     * Adds the newest frame; when the replay is full the oldest one is dropped. The frames stay
     * in the order they were taken, which is the order the dashboard plays them in.
     */
    public void addScreenshot(Bitmap bitmap, String screenName) {
        try {
            if (screenshots.size() >= numberOfScreenshots) {
                screenshots.getFirst().getScreenshot().recycle();
                screenshots.removeFirst();
            }

            screenshots.addLast(new ScreenshotReplay(bitmap, screenName, new Date()));
        } catch (Exception ex) {
            GleapLog.w("Could not add a replay frame", ex);
        }
    }


    public void reset() {
        screenshots = new LinkedList<>();
    }

    public ScreenshotReplay[] getScreenshots() {
        return this.screenshots.toArray(new ScreenshotReplay[0]);
    }

    public int getInterval() {
        return this.interval;
    }
}
