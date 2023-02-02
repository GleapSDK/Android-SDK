package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
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
    private Button squareButton;
    private ConstraintLayout relativeLayout;
    private int prevSize = 0;
    private int messageCounter = 0;
    private int prevMessageCounter = -1;
    boolean showFab = false;
    boolean isForce = true;

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
        if (relativeLayout != null) {
            relativeLayout.setVisibility(View.INVISIBLE);
        }
    }

    public void setVisible() {
        if (relativeLayout != null && !GleapConfig.getInstance().isHideWidget()) {
            relativeLayout.setVisibility(View.VISIBLE);
            relativeLayout.setVisibility(View.VISIBLE);
            render(null, true);
        }
    }

    public void render(Activity activity, boolean force) {
        if(force) {
            isForce = true;
        }
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }
        if (activity == null) {
            return;
        }

        if (activity.getClass().getSimpleName().contains("Gleap")) {
            return;
        }



        if (this.layout == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            this.layout = (ConstraintLayout) inflater.inflate(R.layout.activity_gleap_fab, null);
            this.layout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        Activity local = activity;
        local.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (messages.size() == 0) {
                        return;
                    }

                    if (chatMessages == null ) {
                        chatMessages = new LinearLayout(local);
                        chatMessages.setId(View.generateViewId());
                        chatMessages.setOrientation(LinearLayout.VERTICAL);

                        layout.removeView(chatMessages);
                        layout.addView(chatMessages);

                        ConstraintSet set = new ConstraintSet();
                        set.clone(layout);

                        int offsetX = GleapConfig.getInstance().getButtonX();
                        int offsetY = GleapConfig.getInstance().getButtonY();

                        if (relativeLayout != null) {
                            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT || GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                                set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, local));
                            } else {
                                set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, relativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(10, ActivityUtil.getCurrentActivity()));
                            }
                        } else {
                            set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        }

                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT || GleapConfig.getInstance().getWidgetPositionType() == WidgetPositionType.CLASSIC) {
                            chatMessages.setGravity(Gravity.RIGHT);
                            if (relativeLayout != null) {
                                //TODO: pass the offset??
                                set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                            } else {
                                set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(0, local));
                            }
                        } else {
                            set.connect(chatMessages.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX + 5, local));
                        }

                        set.applyTo(layout);
                    }

                    LinearLayout listMessage = new LinearLayout(local.getApplication().getApplicationContext());
                    listMessage.setOrientation(LinearLayout.VERTICAL);

                    if ((messages.size() > 0 && messages.size() != prevSize) || force) {

                        if (messages.size() > 0 && GleapConfig.getInstance().getWidgetPositionType() == WidgetPositionType.CLASSIC && GleapConfig.getInstance().getWidgetPosition() != WidgetPosition.CLASSIC_BOTTOM) {
                            setShowFab(false);
                        }
                        prevSize = messages.size();
                        //parent.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        chatMessages.removeAllViews();

                        ImageButton close = new ImageButton(local.getApplication().getApplicationContext());

                        GradientDrawable gradientDrawable = new GradientDrawable();
                        gradientDrawable.setCornerRadius(1000);
                        gradientDrawable.setColor(Color.parseColor("#878787"));

                        RelativeLayout buttonContainer = new RelativeLayout(local.getApplication().getApplicationContext());

                        buttonContainer.setGravity(Gravity.RIGHT);

                        LinearLayout.LayoutParams closeContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                        closeContainerParams.setMargins(convertDpToPixel(20, local), convertDpToPixel(0, local), convertDpToPixel(20, local), convertDpToPixel(15, local));
                        buttonContainer.setLayoutParams(closeContainerParams);

                        close.setBackgroundResource(R.drawable.close_white);

                        close.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                clearMessages();
                            }
                        });


                        RelativeLayout view = new RelativeLayout(local.getApplication().getApplicationContext());
                        view.addView(close, convertDpToPixel(18, local), convertDpToPixel(18, local));
                        view.setBackground(gradientDrawable);
                        view.setPadding(15,15,15,15);
                        buttonContainer.addView(view);

                        chatMessages.addView(buttonContainer);
                        listMessage.setGravity(Gravity.RIGHT);
                        for (GleapChatMessage comment :
                                messages) {
                            LinearLayout commentComponent = comment.getComponent(local);
                            if (commentComponent != null) {
                                if (comment.getType().equals("news")) {
                                    CardView cardView = new CardView(local.getApplication().getApplicationContext());

                                    cardView.setBackgroundResource(R.drawable.rounded_corner);
                                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                    params.setMargins(convertDpToPixel(20, local), convertDpToPixel(0, local), convertDpToPixel(20, local), convertDpToPixel(15, local));
                                    cardView.setLayoutParams(params);

                                    cardView.setElevation(4f);
                                    cardView.addView(commentComponent);

                                    listMessage.addView(cardView);
                                } else {
                                    listMessage.addView(commentComponent);
                                }
                            }
                        }

                        chatMessages.addView(listMessage);
                    }

                    if (messages.size() == 0) {
                        chatMessages.removeAllViews();
                    }

                    if (layout.indexOfChild(chatMessages) < 0) {
                        layout.addView(chatMessages);
                    }

                    if(isForce) {
                        isForce = false;
                        addLayout(local);
                    }
                } catch (Exception ex) {
                }
            }
        });
    }

    public void addComment(GleapChatMessage comment) {
        GleapArrayHelper<GleapChatMessage> helper = new GleapArrayHelper<>();

        if (this.messages.size() >= 3) {
            GleapChatMessage oldMessage = this.messages.get(0);
            oldMessage.clearComponent();

            this.messages = helper.shiftArray(this.messages);
        }

        this.messages.add(comment);
        render(null, true);
    }


    public void addFab(Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }
        if (activity == null) {
            return;
        }

        if (this.layout == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            this.layout = (ConstraintLayout) inflater.inflate(R.layout.activity_gleap_fab, null);
            this.layout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        Activity local = activity;
        String screenName = activity.getClass().getSimpleName();

        if (screenName.equals("GleapMainActivity")) {
            return;
        }

        try {
            local.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layout.removeAllViews();
                    prevMessageCounter = messageCounter;
                    if (relativeLayout == null) {
                        relativeLayout = new ConstraintLayout(local);
                        relativeLayout.setId(View.generateViewId());
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

                        relativeLayout.setLayoutParams(params);
                        //  setShowFab(true);
                        relativeLayout.setVisibility(View.INVISIBLE);

                    }

                    if (GleapConfig.getInstance().getWidgetPositionType() == WidgetPositionType.CLASSIC) {
                        renderSquareWidget(local);
                    } else {
                        renderCircularWidget(local);
                    }
                }
            });
        } catch (Exception ex) {
        }
    }

    private void addLayout(Activity local) {
        try {
            if (GleapBug.getInstance().getApplicationtype() != APPLICATIONTYPE.NATIVE) {
                local.addContentView(this.layout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            } else {
                ViewGroup viewGroup = (ViewGroup) ((ViewGroup) local
                        .findViewById(android.R.id.content)).getChildAt(0);

                if (prev != null) {
                    prev.removeView(layout);
                }

                if (viewGroup.indexOfChild(layout) < 0) {
                    layout.setFocusable(false);
                    viewGroup.addView(layout);
                    prev = viewGroup;
                }
            }
        } catch (Error | Exception ignore) {
        }
    }

    void clearMessages() {
        for (GleapChatMessage message :
                this.messages) {
            message.clearComponent();
        }
        this.messages = new LinkedList<>();
        this.showFab = true;
        addFab(null);
        //  render(null, true);
    }

    public void setMessageCounter(int messageCounter) {
        this.messageCounter = messageCounter;
    }

    public void setShowFab(boolean showFabIn) {
        try {
            this.showFab = showFabIn;
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean manualHidden = GleapConfig.getInstance().isHideWidget();
                    if (!manualHidden) {
                        if (showFabIn) {
                            if (relativeLayout != null) {
                                relativeLayout.setVisibility(View.VISIBLE);
                                //GleapConfig.getInstance().setHideWidget(false);
                            }
                        } else {
                            if (relativeLayout != null) {
                                relativeLayout.setVisibility(View.GONE);
                            }
                        }
                    } else {
                        if (relativeLayout != null) {
                            relativeLayout.setVisibility(View.GONE);
                        }
                    }
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    private void renderCircularWidget(Activity local) {
        try {
            if (imageButton == null) {
                imageButton = new ImageButton(local);
                imageButton.setId(View.generateViewId());

                GradientDrawable gdDefault = new GradientDrawable();
                gdDefault.setColor(Color.parseColor(GleapConfig.getInstance().getButtonColor()));
                gdDefault.setCornerRadius(1000);

                imageButton.setBackground(gdDefault);
                imageButton.setAdjustViewBounds(true);
                imageButton.setScaleType(ImageView.ScaleType.FIT_CENTER);

                new GleapRoundImageHandler(GleapConfig.getInstance().getButtonLogo(), imageButton).execute();

                imageButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!Gleap.getInstance().isOpened()) {
                            Gleap.getInstance().open();
                            showFab = false;
                        }
                    }
                });
            }


            boolean manualHidden = GleapConfig.getInstance().isHideWidget();

            if (showFab && !manualHidden) {
                relativeLayout.setVisibility(View.VISIBLE);
            } else {
                relativeLayout.setVisibility(View.GONE);
                return;
            }

            relativeLayout.removeAllViews();
            if (relativeLayout.indexOfChild(imageButton) < 0) {
                relativeLayout.addView(imageButton, convertDpToPixel(56, local), convertDpToPixel(56, local));
            }

            if (layout.indexOfChild(relativeLayout) < 0) {
                layout.addView(relativeLayout);
                addLayout(local);
            }

            GradientDrawable gdDefaultText = new GradientDrawable();
            gdDefaultText.setColor(Color.RED);

            gdDefaultText.setCornerRadius(1000);
            TextView textView = new TextView(local);
            textView.setId(View.generateViewId());
            textView.setBackground(gdDefaultText);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(10);

            textView.setText(String.valueOf(messageCounter));
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            if (messageCounter > 0) {
                relativeLayout.addView(textView, convertDpToPixel(16, local), convertDpToPixel(16, local));
                ConstraintSet set = new ConstraintSet();
                set.clone(relativeLayout);
                set.connect(textView.getId(), ConstraintSet.END, relativeLayout.getId(), ConstraintSet.END, 0);
                set.applyTo(relativeLayout);

                textView.bringToFront();
            } else {
                if (relativeLayout.indexOfChild(textView) > 0) {
                    relativeLayout.removeView(textView);
                }
            }

            ConstraintSet set = new ConstraintSet();
            set.clone(layout);

            int offsetX = GleapConfig.getInstance().getButtonX();
            int offsetY = GleapConfig.getInstance().getButtonY();

            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                set.connect(relativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX, local));
            } else {
                set.connect(relativeLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX, local));
            }
            set.connect(relativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, local));
            set.applyTo(layout);

        } catch (Exception ex) {
        }
    }


    private void renderSquareWidget(Activity local) {
        try {
            boolean manualHidden = GleapConfig.getInstance().isHideWidget();

            if (showFab && !manualHidden) {
                relativeLayout.setVisibility(View.VISIBLE);
            } else {
                relativeLayout.setVisibility(View.GONE);
                return;
            }

            if (squareButton == null) {
                squareButton = new Button(local);
                squareButton.setId(View.generateViewId());
                squareButton.setPadding(100, 0, 100, 0);

                GradientDrawable gdDefault = new GradientDrawable();
                gdDefault.setColor(Color.parseColor(GleapConfig.getInstance().getButtonColor()));
                float[] corners = {
                        20, 20, 20, 20, 0, 0, 0, 0
                };
                gdDefault.setCornerRadii(corners);

                squareButton.setBackground(gdDefault);
                squareButton.setText(GleapConfig.getInstance().getWidgetButtonText());
                squareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!Gleap.getInstance().isOpened()) {
                            Gleap.getInstance().open();
                            showFab = false;
                        }
                    }
                });
                squareButton.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        int width = squareButton.getWidth();
                        int height = squareButton.getHeight();
                        ConstraintSet set = new ConstraintSet();
                        set.clone(layout);

                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                            set.connect(relativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(20, local));
                            set.connect(relativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                            relativeLayout.setPadding(0, height,0,0);
                            set.connect(relativeLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 0);
                            set.connect(relativeLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, 0);
                            set.connect(relativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                            relativeLayout.setPadding(0, height,0,0);
                            set.connect(relativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                            set.connect(relativeLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, 0);
                            set.connect(relativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        }

                        set.applyTo(layout);
                    }
                });
            }

            relativeLayout.removeAllViews();

            if (layout.indexOfChild(relativeLayout) < 0) {
                if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                    relativeLayout.setRotation(-90);
                }
                if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                    relativeLayout.setRotation(90);
                }

                layout.addView(relativeLayout);
                addLayout(local);
            }


            relativeLayout.addView(squareButton);


        } catch (Exception ex) {
        }
    }

}