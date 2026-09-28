package io.gleap;

import android.graphics.Bitmap;

import java.util.Date;

class ScreenshotReplay {
    private final Bitmap screenshot;
    private final String screenName;
    private final Date date;

    public ScreenshotReplay(Bitmap screenshot, String screenName, Date date) {
        this.screenshot = screenshot;
        this.screenName = screenName;
        this.date = date;
    }

    public Bitmap getScreenshot() {
        return screenshot;
    }

    public Date getDate() {
        return date;
    }

    public String getScreenName() {
        return screenName;
    }

}
