package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
    private RelativeLayout closeButtonContainer;
    private Button squareButton;
    private ConstraintLayout relativeLayout;
    private int prevSize = 0;
    private int messageCounter = 0;
    private int prevMessageCounter = -1;
    boolean showFab = false;
    boolean isForce = true;
    boolean attached = false;

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
            render(null, true);
        }
    }

    public void render(Activity activity, boolean force) {
        if (force) {
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

                    if (chatMessages == null) {
                        chatMessages = new LinearLayout(local);
                        chatMessages.setId(View.generateViewId());
                        chatMessages.setOrientation(LinearLayout.VERTICAL);

                        layout.removeView(chatMessages);
                        layout.addView(chatMessages);

                        ConstraintSet set = new ConstraintSet();
                        set.clone(layout);

                        int offsetX = GleapConfig.getInstance().getButtonX();
                        int offsetY = GleapConfig.getInstance().getButtonY();

                        if (relativeLayout == null) {
                            set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        } else {
                            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_LEFT) {
                                set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, relativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(10, local));
                                set.connect(chatMessages.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX, local));
                            } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                                set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, relativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(10, local));
                                set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX - 20, local));
                            } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                                set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, local));
                                set.connect(chatMessages.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetY, local));
                                chatMessages.setGravity(Gravity.RIGHT);
                            } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                                set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, relativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(10, local));
                                set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                            } else {
                                //for hidden and Bottom right classic view
                                set.connect(chatMessages.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, local));
                                set.connect(chatMessages.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                                chatMessages.setGravity(Gravity.RIGHT);
                            }
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

                        if (closeButtonContainer == null || closeButtonContainer.getParent() == null) {
                            ImageButton closeButton = new ImageButton(local.getApplication().getApplicationContext());

                            GradientDrawable gradientDrawable = new GradientDrawable();
                            gradientDrawable.setCornerRadius(1000);
                            gradientDrawable.setColor(Color.parseColor("#878787"));

                            closeButtonContainer = new RelativeLayout(local.getApplication().getApplicationContext());

                            closeButtonContainer.setGravity(Gravity.RIGHT);

                            LinearLayout.LayoutParams closeContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                            closeContainerParams.setMargins(convertDpToPixel(20, local), convertDpToPixel(0, local), convertDpToPixel(20, local), 0);
                            closeButtonContainer.setLayoutParams(closeContainerParams);

                            closeButton.setBackgroundResource(R.drawable.close_white);

                            closeButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    clearMessages();
                                }
                            });


                            RelativeLayout view = new RelativeLayout(local.getApplication().getApplicationContext());
                            view.addView(closeButton, convertDpToPixel(18, local), convertDpToPixel(18, local));
                            view.setBackground(gradientDrawable);
                            view.setPadding(15, 15, 15, 15);
                            closeButtonContainer.addView(view);

                            chatMessages.addView(closeButtonContainer);
                        }

                        listMessage.setGravity(Gravity.RIGHT);

                        //counter if first message
                        int counter = 0;
                        for (GleapChatMessage comment :
                                messages) {
                            LinearLayout commentComponent = comment.getComponent();
                            if (commentComponent != null) {
                                if (comment.getType().equals("news")) {
                                    CardView cardView = new CardView(local.getApplication().getApplicationContext());

                                    cardView.setBackgroundResource(R.drawable.rounded_corner);
                                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                    if (counter == 0) {
                                        params.setMargins(convertDpToPixel(1, local), convertDpToPixel(10, local), convertDpToPixel(20, local), convertDpToPixel(15, local));
                                    } else {
                                        params.setMargins(convertDpToPixel(1, local), convertDpToPixel(0, local), convertDpToPixel(20, local), convertDpToPixel(15, local));
                                    }
                                    counter++;
                                    cardView.setLayoutParams(params);

                                    cardView.setElevation(4f);
                                    if(commentComponent.getParent() == null) {
                                        cardView.addView(commentComponent);
                                        listMessage.addView(cardView);
                                    }
                                } else {
                                    if (commentComponent.getParent() == null) {
                                        listMessage.addView(commentComponent);
                                    }
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

                    if (isForce) {
                        isForce = false;
                        addLayout(local);
                    }
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        });
    }

    public void addComment(GleapChatMessage comment) {
        for(GleapChatMessage message : this.messages) {
            if(message.getOutboundId().equals(comment.getOutboundId())) {
                return;
            }
        }
        GleapArrayHelper<GleapChatMessage> helper = new GleapArrayHelper<>();

        if (this.messages.size() >= 3) {
            GleapChatMessage oldMessage = this.messages.get(0);
            LinearLayout layout = oldMessage.getComponent();
            LinearLayout parent = (LinearLayout) layout.getParent();
            parent.removeView(layout);

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
            LinearLayout oldMessage = (LinearLayout) message.getComponent();
            ViewGroup parent = null;
            try{
                parent = (LinearLayout) oldMessage.getParent();
            }catch (Exception err) {}

            try{
                parent = (CardView) oldMessage.getParent();
            }catch (Exception err) {}

            parent.removeView(layout);

            message.clearComponent();

        }
        this.messages = new LinkedList<>();
        this.showFab = true;
        chatMessages.removeAllViews();
        addFab(null);
         //render(null, true);
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
                            }
                        } else {
                            if (relativeLayout != null) {
                                relativeLayout.setVisibility(View.INVISIBLE);
                            }
                        }
                    } else {
                        if (relativeLayout != null) {
                            relativeLayout.setVisibility(View.INVISIBLE);
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

            int offsetX = GleapConfig.getInstance().getButtonX() + 20;
            int offsetY = GleapConfig.getInstance().getButtonY();

            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                set.connect(relativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX - 20, local));
            } else {
                set.connect(relativeLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX - 20, local));
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
                squareButton.setVisibility(View.INVISIBLE);
                squareButton.setId(View.generateViewId());
                int padding = 22;
                squareButton.setPadding(convertDpToPixel(padding, local), 0, convertDpToPixel(padding, local), 0);

                GradientDrawable gdDefault = new GradientDrawable();
                gdDefault.setColor(Color.parseColor(GleapConfig.getInstance().getButtonColor()));
                int corner = convertDpToPixel(10, local);
                float[] corners = {
                        corner, corner, corner, corner, 0, 0, 0, 0
                };
                gdDefault.setCornerRadii(corners);

                squareButton.setAllCaps(false);
                squareButton.setBackground(gdDefault);
                squareButton.setText(GleapConfig.getInstance().getWidgetButtonText());
                squareButton.setTextColor(Color.WHITE);
                squareButton.setTypeface(Typeface.DEFAULT);


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
                        if (!attached) {
                            int height = squareButton.getHeight();
                            int width = squareButton.getWidth();
                            ConstraintSet set = new ConstraintSet();
                            set.clone(layout);

                            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                                set.connect(relativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(20, local));
                                set.connect(relativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                            } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                                relativeLayout.setPadding(0, (width / 2 - height / 2) + 1, 0, 0);
                                set.connect(relativeLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 0);
                                set.connect(relativeLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, width / 2);
                                set.connect(relativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                            } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                                relativeLayout.setPadding(0, (width / 2 - height / 2) + 1, 0, 0);
                                set.connect(relativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                                set.connect(relativeLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, width / 2);
                                set.connect(relativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                            }
                            set.applyTo(layout);
                            attached = true;
                        }
                    }
                });
            }

            relativeLayout.removeAllViews();

            if (layout.indexOfChild(relativeLayout) < 0) {
                if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                    relativeLayout.setRotation(-90);
                } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                    relativeLayout.setRotation(90);
                }

                //display button when its loaded
                if (squareButton.getWidth() > 0) {
                    squareButton.setVisibility(View.VISIBLE);
                }

                layout.addView(relativeLayout);
                addLayout(local);
            }

            if (relativeLayout.indexOfChild(imageButton) < 0) {
                relativeLayout.addView(squareButton, 0, convertDpToPixel(36, local));
            }
        } catch (Exception ex) {
        }
    }

}