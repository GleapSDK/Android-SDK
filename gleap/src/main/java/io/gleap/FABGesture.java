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

                }

                @Override
                public void onActivityStarted(@NonNull Activity activity) {

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

        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (running) {
                    Gleap.getInstance().open();
                }
            }
        });

        Activity local = activity;

        Thread loadImage = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bm = getImageBitmap(GleapConfig.getInstance().getButtonLogo());
                    local.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                GradientDrawable gdDefault = new GradientDrawable();

                                gdDefault.setColor(Color.parseColor(GleapConfig.getInstance().getButtonColor()));
                                gdDefault.setCornerRadius(1000);
                                imageButton.setBackground(gdDefault);

                                imageButton.setAdjustViewBounds(true);

                                imageButton.setScaleType(ImageView.ScaleType.FIT_CENTER);

                                imageButton.setImageBitmap(bm);
                                imageButton.setVerticalScrollbarPosition(400);
                                view.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                                ViewGroup viewGroup = (ViewGroup) ((ViewGroup) local
                                        .findViewById(android.R.id.content)).getChildAt(0);

                                imageButton.setY(view.getHeight() - imageButton.getHeight());
                                imageButton.setX(view.getWidth() - imageButton.getWidth());

                                viewGroup.addView(view);
                            }catch (Exception ex){}
                        }
                    });

                }catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        });

        loadImage.start();




    }

    private Bitmap getImageBitmap(String url) {
        Bitmap bm = null;
        try {
            InputStream stream = new URL(url).openConnection().getInputStream();
            bm = BitmapFactory.decodeStream(stream);

        } catch (IOException e) {
            System.out.println(e);
        }
        if(bm != null) {
            return bm;
        }
        return getImageBitmap("https://sdk.gleap.io/res/chatbubble.png");
    }
}
