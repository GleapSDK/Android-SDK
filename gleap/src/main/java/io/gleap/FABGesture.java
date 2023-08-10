package io.gleap;


import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import gleap.io.gleap.R;

class FABGesture extends GleapDetector {

    public FABGesture(Application application) {
        super(application);
        run();
    }

    @Override
    public void initialize() {
        //attachFAB(null);
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void pause() {

    }

    @Override
    public void unregister() {

    }

    private void start() {
       //
    }

    private void run() {

    }
}
