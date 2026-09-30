package io.gleap;

import android.graphics.Bitmap;
import android.view.View;
import android.view.Window;

import java.util.concurrent.Callable;

/**
 * Not used by the SDK anymore. Kept because it is part of the public API.
 */
public class PixelCopyTask implements Callable<String> {
    private final View view;
    private final Window window;
    private final int timer;

    public PixelCopyTask(View view, Window window, int timer) {
        this.view = view;
        this.window = window;
        this.timer = timer;
    }

    @Override
    public String call() {
        return "";
    }

    /**
     * Receives a captured screenshot.
     */
    protected interface ImageTaken {
        void invoke(Bitmap bitmap);
    }
}
