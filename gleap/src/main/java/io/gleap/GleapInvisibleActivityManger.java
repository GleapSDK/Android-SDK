package io.gleap;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import gleap.io.gleap.R;

/**
 * Control over invisible overlay
 * adds fab and notifictions if needed
 */
public class GleapInvisibleActivityManger {
    private static GleapInvisibleActivityManger instance;
    private List<Comment> messages;
    private GleapArrayHelper<Comment> gleapArrayHelper;
    private ConstraintLayout layout;
    private LinearLayout chatMessages;
    private ViewGroup prev;
    private ImageButton imageButton;
    private int prevSize = 0;

    private GleapInvisibleActivityManger() {
        messages = new LinkedList<>();
        gleapArrayHelper = new GleapArrayHelper<>();
    }

    public static GleapInvisibleActivityManger getInstance() {
        if (instance == null) {
            instance = new GleapInvisibleActivityManger();
        }
        return instance;
    }

    public void setInvisible() {
        layout.setVisibility(View.GONE);
    }

    public void setVisible() {
        layout.setVisibility(View.VISIBLE);
    }

    public void render(Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }
        if (activity == null) {
            return;
        }


        LayoutInflater inflater = activity.getLayoutInflater();

        if (this.layout == null) {
            this.layout = (ConstraintLayout) inflater.inflate(R.layout.activity_gleap_fab, null);
            this.layout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        Activity local = activity;
        local.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (chatMessages == null) {
                        chatMessages = new LinearLayout(layout.getContext());
                        chatMessages.setId(View.generateViewId());
                        chatMessages.setOrientation(LinearLayout.VERTICAL);
                        layout.removeView(chatMessages);
                        layout.addView(chatMessages);

                        ConstraintSet set = new ConstraintSet();
                        set.clone(layout);

                        if(imageButton != null) {
                            set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, imageButton.getId(), ConstraintSet.TOP, 20);
                        }else {
                            set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 30);
                        }

                        if(GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                            set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 30);
                        }else {
                            set.connect(chatMessages.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 30);
                        }

                        set.applyTo(layout);
                    }


                    if (messages.size() > 0 && messages.size() != prevSize) {
                        prevSize = messages.size();
                        //parent.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        chatMessages.removeAllViews();
                        for (Comment comment :
                                messages) {
                            LinearLayout messageContainer   = new LinearLayout(local.getApplication().getApplicationContext());
                            LinearLayout completeMessage = new LinearLayout(local.getApplication().getApplicationContext());

                            TextView titleComponent = new TextView(local.getApplication().getApplicationContext());
                            titleComponent.setId(View.generateViewId());
                            titleComponent.setText(comment.getSender().getName());
                            titleComponent.setTextSize(13);
                            titleComponent.setTextColor(Color.BLACK);

                            TextView messageComponent = new TextView(local.getApplication().getApplicationContext());
                            messageComponent.setId(View.generateViewId());
                            messageComponent.setText(comment.getText());
                            messageComponent.setTextSize(13);
                            //use half screen max
                            messageComponent.setMaxWidth(getScreenWidth()/2);


                            completeMessage.setOrientation(LinearLayout.VERTICAL);
                            completeMessage.addView(titleComponent);
                            completeMessage.addView(messageComponent);


                            completeMessage.setPadding(16,20,16,20);

                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            params.setMargins(10,15,10,2);

                            completeMessage.setBackgroundResource(R.drawable.chatbubble);
                            completeMessage.setElevation(30f);
                            completeMessage.setLayoutParams(params);
                            completeMessage.setBaselineAligned(true);

                            ImageView avatarImage = new ImageView(local.getApplication().getApplicationContext());

                            new GleapImageHandler(comment.getSender().getProfileImageUrl(), avatarImage).execute();

                            completeMessage.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if(comment.getShareToken().equals("")) {
                                        Gleap.getInstance().open();
                                    } else {

                                 //       Gleap.getInstance().open();
                                    }

                                }
                            });

                            //add avatar and bubble
                            messageContainer.addView(avatarImage, 90, 90);
                            messageContainer.addView(completeMessage);
                            messageContainer.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
                            messageContainer.setPadding(5,10,5,10);
                            messageContainer.setOrientation(LinearLayout.HORIZONTAL);

                            chatMessages.addView(messageContainer);
                        }

                    }

                 addLayout(local);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }


    public void addComment(Comment comment) {
        this.messages.add(comment);
    }

    public void addFab(Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }
        if (activity == null) {
            return;
        }

        LayoutInflater inflater = activity.getLayoutInflater();

        if (this.layout == null) {
            this.layout = (ConstraintLayout) inflater.inflate(R.layout.activity_gleap_fab, null);
            this.layout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (imageButton != null) {
            addLayout(activity);
            return;
        }

        Activity local = activity;

        try {
            local.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        imageButton = new ImageButton(local);
                        imageButton.setId(View.generateViewId());

                        GradientDrawable gdDefault = new GradientDrawable();
                        gdDefault.setColor(Color.parseColor(GleapConfig.getInstance().getButtonColor()));
                        gdDefault.setCornerRadius(1000);

                        imageButton.setBackground(gdDefault);
                        imageButton.setAdjustViewBounds(true);
                        imageButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        new GleapImageHandler(GleapConfig.getInstance().getButtonLogo(), imageButton).execute();

                        imageButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                if (!Gleap.getInstance().isOpened()) {
                                    Gleap.getInstance().open();
                                }
                            }
                        });

                        layout.removeView(imageButton);
                        layout.addView(imageButton, 140, 140);
                        addLayout(local);

                        ConstraintSet set = new ConstraintSet();
                        set.clone(layout);


                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                            set.connect(imageButton.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 30);
                        } else {
                            set.connect(imageButton.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 30);
                        }

                        set.connect(imageButton.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 30);

                        set.applyTo(layout);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    private void addLayout(Activity local) {
        ViewGroup viewGroup = (ViewGroup) ((ViewGroup) local
                .findViewById(android.R.id.content)).getChildAt(0);
        if (prev != null) {
            prev.removeView(layout);
        }

        if (viewGroup.indexOfChild(layout) < 0) {
            viewGroup.addView(layout);
            prev = viewGroup;
        }
    }
}
