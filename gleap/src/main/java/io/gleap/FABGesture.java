package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import gleap.io.gleap.R;

public class FABGesture extends GleapDetector {
    private boolean running = false;

    public FABGesture(Application application) {
        super(application);
        run();
    }

    @Override
    public void initialize() {

    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void pause() {
        this.running = false;
    }

    @Override
    public void unregister() {

    }

    private void start() {
        this.running = true;
    }

    private void run() {
        if (this.application != null) {
            this.application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
                    System.out.println("CREATED?");
                }

                @Override
                public void onActivityStarted(@NonNull Activity activity) {
                    System.out.println("STARTED?");
                    if (activity != null) {
                        System.out.println(activity.getLocalClassName());
                        System.out.println("----------");
                        String localName = activity.getLocalClassName();
                        if (localName.indexOf("Gleap") <= 0) {
                            attachFAB(activity);
                        }

                    }
                }

                @Override
                public void onActivityResumed(@NonNull Activity activity) {

                }

                @Override
                public void onActivityPaused(@NonNull Activity activity) {

                }

                @Override
                public void onActivityStopped(@NonNull Activity activity) {

                }

                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

                }

                @Override
                public void onActivityDestroyed(@NonNull Activity activity) {
                }
            });

        }
    }

    public void attachFAB(Activity activity) {
        if(activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }
        LayoutInflater inflater = activity.getLayoutInflater();

        View view = inflater.inflate(R.layout.activity_gleap_fab, null);
        ImageView imageButton = view.findViewById(R.id.gleap_imageButton);

        Activity local = activity;
        Thread loadImage = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bm = getImageBitmap("https://sdk.gleap.io/res/chatbubble.png");
                    local.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageButton.setImageBitmap(bm);
                        }
                    });

                }catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        });

        loadImage.start();

       imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (running) {
                    Gleap.getInstance().open();
                }
            }
        });


        view.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewGroup viewGroup = (ViewGroup) ((ViewGroup) activity
                .findViewById(android.R.id.content)).getChildAt(0);

        viewGroup.addView(view);
    }

    private Bitmap getImageBitmap(String url) {
        Bitmap bm = null;
        try {
            InputStream stream = new URL(url).openConnection().getInputStream();
            bm = BitmapFactory.decodeStream(stream);

        } catch (IOException e) {
            System.out.println(e);
        }
        return Bitmap.createScaledBitmap(bm, 130, 130, false);
    }
}
