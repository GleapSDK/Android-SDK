package io.gleap;

import static android.graphics.Bitmap.Config.ARGB_8888;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import androidx.annotation.RequiresApi;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

public class PixelCopyTask implements Callable<String> {
    private View view;
    private Window window;
    private int timer;
    private final List<Bitmap> result = new LinkedList<>();

    public PixelCopyTask(View view, Window window, int timer){
        this.view = view;
        this.window = window;
        this.timer = timer;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public String call() {

    return "";
    }
    protected interface ImageTaken {
        void invoke(Bitmap bitmap);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void captureView(View view, Window window, ImageTaken imageTaken){

        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), ARGB_8888);
        int[] location = new int[2];
        view.getLocationInWindow(location);
        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PixelCopy.request(window,
                        new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight()),
                        bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
                            @Override
                            public void onPixelCopyFinished(int copyResult) {
                                System.out.println(copyResult);
                                imageTaken.invoke(bitmap);
                            }
                        },
                        new Handler()
                );
            }
        });
    }
}


