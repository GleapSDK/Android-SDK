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

    public void addScreenshot(Bitmap bitmap, String screenName) {
        try {
            if (screenshots.size() == numberOfScreenshots) {
                screenshots.removeFirst();
            }

            screenshots.push(new ScreenshotReplay(bitmap, screenName, new Date()));
        }catch (Exception ex) {
      ex.printStackTrace();
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
