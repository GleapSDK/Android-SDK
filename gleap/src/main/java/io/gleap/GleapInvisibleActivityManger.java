package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.LinkedList;
import java.util.List;

import gleap.io.gleap.R;

/**
 * Control over invisible overlay
 * adds fab and notifictions if needed
 */
class GleapInvisibleActivityManger {
    private static GleapInvisibleActivityManger instance;
    private List<GleapChatMessage> messages;
    private ConstraintLayout layout;
    private LinearLayout chatMessages;
    private ViewGroup prev;
    private ImageButton imageButton;
    private int prevSize = 0;

    private GleapInvisibleActivityManger() {
        messages = new LinkedList<>();
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

    public void render(Activity activity, boolean force) {
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

                        if (imageButton != null) {
                            set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, imageButton.getId(), ConstraintSet.TOP, 0);
                        } else {
                            set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(20, local));
                        }

                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                            chatMessages.setGravity(Gravity.RIGHT);
                            if (imageButton != null) {
                                set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(15, local));
                            }else {
                                set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(0, local));
                            }
                        } else {
                            set.connect(chatMessages.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(15, local));
                        }

                        set.applyTo(layout);
                    }


                    if ((messages.size() > 0 && messages.size() != prevSize) || force) {
                        prevSize = messages.size();
                        //parent.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        chatMessages.removeAllViews();
                        for (GleapChatMessage comment :
                                messages) {
                            LinearLayout commentComponent = comment.getComponent(local);
                            if(commentComponent != null) {
                                chatMessages.addView(commentComponent);
                            }
                        }

                    }

                    if(messages.size() == 0) {
                        chatMessages.removeAllViews();
                    }


                    addLayout(local);
                } catch (Exception ex) {
                }
            }
        });
    }




    public void addComment(GleapChatMessage comment) {
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
                        layout.addView(imageButton, convertDpToPixel(60, local), convertDpToPixel(60, local));
                        addLayout(local);

                        ConstraintSet set = new ConstraintSet();
                        set.clone(layout);


                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                            set.connect(imageButton.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(20, local));
                        } else {
                            set.connect(imageButton.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(20, local));
                        }

                        set.connect(imageButton.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(20, local));

                        set.applyTo(layout);
                    } catch (Exception ex) {
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

    void clearMessages() {
        this.messages = new LinkedList<>();
    }
}
