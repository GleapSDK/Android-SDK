package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
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

import org.json.JSONObject;

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
    private TextView notificationCountTextView;
    private LinearLayout notificationContainerLayout;
    private LinearLayout notificationListContainer;
    private ImageButton imageButton;
    private RelativeLayout closeButtonContainer;
    private Button squareButton;
    private GleapBanner banner;
    private JSONObject bannerData;
    private ConstraintLayout feedbackButtonRelativeLayout;
    private int messageCounter = 0;
    boolean showFab = false;
    boolean isForce = true;
    boolean attached = false;

    private GleapInvisibleActivityManger() {
        messages = new LinkedList<>();
    }

    public static void animateViewInOut(View view, boolean show) {
        if (view == null) {
            return;
        }

        view.setAlpha(show ? 0f : 1f);
        if (show) {
            view.setVisibility(View.VISIBLE);
        }

        ObjectAnimator fadeInAnimation = ObjectAnimator.ofFloat(view, "alpha", show ? 0f : 1f, show ? 1f : 0f);
        fadeInAnimation.setDuration(300);
        fadeInAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                if (!show && view != null) {
                    view.setVisibility(View.GONE);
                }
            }
        });
        fadeInAnimation.start();
    }

    public static GleapInvisibleActivityManger getInstance() {
        if (instance == null) {
            instance = new GleapInvisibleActivityManger();
        }
        return instance;
    }

    public void setInvisible() {
        if (feedbackButtonRelativeLayout != null) {
            //feedbackButtonRelativeLayout.setVisibility(View.INVISIBLE);
        }
    }

    public void setVisible() {
        if (feedbackButtonRelativeLayout != null && !GleapConfig.getInstance().isHideWidget()) {
            //feedbackButtonRelativeLayout.setVisibility(View.VISIBLE);
        }
    }

    public void createNotificationLayout(Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }

        if (activity == null) {
            return;
        }

        if (this.layout == null) {
            return;
        }

        Activity finalActivity = activity;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Add our notification container.
                    notificationContainerLayout = new LinearLayout(finalActivity);
                    notificationContainerLayout.setId(View.generateViewId());
                    notificationContainerLayout.setOrientation(LinearLayout.VERTICAL);
                    notificationContainerLayout.setGravity(Gravity.LEFT);

                    int offsetX = GleapConfig.getInstance().getButtonX();
                    int offsetY = GleapConfig.getInstance().getButtonY();

                    layout.addView(notificationContainerLayout);

                    ConstraintSet set = new ConstraintSet();
                    set.clone(layout);

                    int viewPadding = 20;

                    set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                    if (feedbackButtonRelativeLayout == null) {
                        set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                    } else {
                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_LEFT) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(15, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX, finalActivity));
                            viewPadding = offsetX;
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(15, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX - 20, finalActivity));
                            viewPadding = offsetX;
                            notificationContainerLayout.setGravity(Gravity.RIGHT);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetY, finalActivity));
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(15, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                            notificationContainerLayout.setGravity(Gravity.RIGHT);
                        } else {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                            notificationContainerLayout.setGravity(Gravity.RIGHT);
                        }
                    }

                    // Set max width.
                    try {
                        DisplayMetrics displayMetrics = new DisplayMetrics();
                        finalActivity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                        int deviceWidth = displayMetrics.widthPixels;
                        int deviceHeight = displayMetrics.heightPixels;
                        int smallerDimension = Math.min(deviceWidth, deviceHeight);
                        int maxWidthPx = smallerDimension - convertDpToPixel(viewPadding * 2, finalActivity);
                        set.constrainMaxWidth(notificationContainerLayout.getId(), maxWidthPx);
                    } catch (Exception exp) {}

                    set.applyTo(layout);

                    // Initialize close button.
                    if (closeButtonContainer == null) {
                        ImageButton closeButton = new ImageButton(finalActivity.getApplication().getApplicationContext());

                        GradientDrawable gradientDrawable = new GradientDrawable();
                        gradientDrawable.setCornerRadius(1000);
                        gradientDrawable.setColor(Color.parseColor("#878787"));

                        closeButtonContainer = new RelativeLayout(finalActivity.getApplication().getApplicationContext());
                        closeButtonContainer.setGravity(Gravity.RIGHT);

                        LinearLayout.LayoutParams closeContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
                        closeContainerParams.setMargins(convertDpToPixel(15, finalActivity), convertDpToPixel(0, finalActivity), convertDpToPixel(15, finalActivity), 0);
                        closeButtonContainer.setLayoutParams(closeContainerParams);

                        closeButton.setBackgroundResource(R.drawable.close_white);
                        closeButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                clearMessages();
                            }
                        });

                        RelativeLayout view = new RelativeLayout(finalActivity.getApplication().getApplicationContext());
                        view.addView(closeButton, convertDpToPixel(18, finalActivity), convertDpToPixel(18, finalActivity));
                        view.setBackground(gradientDrawable);
                        view.setPadding(15, 15, 15, 15);
                        closeButtonContainer.setVisibility(View.GONE);
                        closeButtonContainer.addView(view);
                        notificationContainerLayout.addView(closeButtonContainer);
                    }

                    if (notificationListContainer == null) {
                        notificationListContainer = new LinearLayout(finalActivity.getApplication().getApplicationContext());
                        notificationListContainer.setOrientation(LinearLayout.VERTICAL);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            notificationListContainer.setGravity(notificationContainerLayout.getGravity());
                        }
                        notificationContainerLayout.addView(notificationListContainer);
                    }

                    // Initially add all messages (if any)
                    if (messages.size() > 0) {
                        for (GleapChatMessage notification : messages) {
                            addNotificationViewToLayout(notification, finalActivity);
                        }
                    }
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        });
    }

    public void removeNotificationViewFromLayout(GleapChatMessage notification) {
        try {
            LinearLayout layout = notification.getComponent(null);
            if (layout != null && layout.getParent() != null) {
                if (layout.getParent() instanceof CardView) {
                    CardView parent = (CardView) layout.getParent();
                    if (parent != null) {
                        parent.removeView(layout);

                        LinearLayout grandParent = (LinearLayout) parent.getParent();
                        if (grandParent != null) {
                            grandParent.removeView(parent);
                        }
                    }
                } else if (layout.getParent() instanceof LinearLayout) {
                    LinearLayout parent = (LinearLayout) layout.getParent();
                    parent.removeView(layout);
                }
            }
        } catch (Exception exp) {
            System.out.println(exp);
        }

        try {
            notification.clearComponent();
        } catch (Exception exp) {
            System.out.println(exp);
        }

        // Remove from list.
        this.messages.remove(notification);

        if (closeButtonContainer != null) {
            if (this.messages.size() > 0) {
                closeButtonContainer.setVisibility(View.VISIBLE);
            } else {
                closeButtonContainer.setVisibility(View.GONE);
            }
        }
    }

    public void addNotificationViewToLayout(GleapChatMessage notification, Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }

        if (activity == null) {
            return;
        }

        if (notificationListContainer == null) {
            return;
        }

        LinearLayout commentComponent = notification.getComponent(activity);
        if (commentComponent != null) {
            if (notification.getType().equals("news")) {
                CardView cardView = new CardView(activity.getApplication().getApplicationContext());
                cardView.setBackgroundResource(R.drawable.rounded_corner);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (notificationListContainer.getChildCount() == 0) {
                    params.setMargins(convertDpToPixel(1, activity), convertDpToPixel(10, activity), convertDpToPixel(20, activity), convertDpToPixel(15, activity));
                } else {
                    params.setMargins(convertDpToPixel(1, activity), convertDpToPixel(0, activity), convertDpToPixel(20, activity), convertDpToPixel(15, activity));
                }
                cardView.setLayoutParams(params);
                cardView.setElevation(4f);
                if(commentComponent.getParent() == null) {
                    cardView.addView(commentComponent);
                    notificationListContainer.addView(cardView);
                }
            } else {
                if (commentComponent.getParent() == null) {
                    notificationListContainer.addView(commentComponent);
                }
            }
        }

        // Show close button.
        if (closeButtonContainer != null) {
            closeButtonContainer.setVisibility(View.VISIBLE);
        }
    }

    public void destoryBanner(boolean clearData) {
        if (this.banner != null) {
            LinearLayout innerBannerLayoutbanner = this.banner.getComponent();
            if (innerBannerLayoutbanner != null) {
                ConstraintLayout parentLayout = (ConstraintLayout) innerBannerLayoutbanner.getParent();
                if (parentLayout != null) {
                    parentLayout.removeView(innerBannerLayoutbanner);
                }
            }

            this.banner.clearComponent();
            this.banner = null;
        }

        if (clearData) {
            this.bannerData = null;
        }
    }

    public void showBanner(JSONObject bannerData, Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }

        if (activity == null || bannerData == null) {
            return;
        }

        if (this.layout == null) {
            return;
        }

        this.bannerData = bannerData;
        this.banner = new GleapBanner(this.bannerData, activity);

        // Attach the banner to the current layout.
        LinearLayout innerBannerLayoutbanner = this.banner.getComponent();
        if (innerBannerLayoutbanner != null) {
            if (innerBannerLayoutbanner.getParent() == null) {
                // Setup constraints.
                ConstraintSet bannerSet = new ConstraintSet();
                bannerSet.clone(layout);
                bannerSet.connect(innerBannerLayoutbanner.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, 0); // Connect top of bannerContainer to top of layout
                bannerSet.connect(innerBannerLayoutbanner.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 0); // Connect start of bannerContainer to start of layout
                bannerSet.connect(innerBannerLayoutbanner.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0); // Connect end of bannerContainer to end of layout
                bannerSet.applyTo(layout);

                // Add banner view.
                layout.addView(innerBannerLayoutbanner);
            }
        }
    }

    public void addNotification(GleapChatMessage comment, Activity activity) {
        // Check if notification already present.
        for (GleapChatMessage message : this.messages) {
            if (message.getOutboundId().equals(comment.getOutboundId())) {
                return;
            }
        }

        // Check notification limit.
        GleapArrayHelper<GleapChatMessage> helper = new GleapArrayHelper<>();
        if (this.messages.size() >= 2) {
            // Remove from layout.
            GleapChatMessage notificationToRemove = this.messages.get(0);
            removeNotificationViewFromLayout(notificationToRemove);
            this.messages = helper.shiftArray(this.messages);
        }

        this.messages.add(comment);
        addNotificationViewToLayout(comment, activity);
    }

    public void destoryLayout() {
        if (this.layout != null) {
            this.layout.removeAllViews();

            // Remove layout from parent.
            try {
                ViewParent parent = this.layout.getParent();
                if (parent != null) {
                    if (parent instanceof ViewGroup) {
                        ViewGroup viewGroupParent = (ViewGroup) parent;
                        viewGroupParent.removeView(this.layout);
                    }
                }
            } catch (Exception exp) {}
            this.layout = null;
        }
    }

    public void destoryUI() {
        this.destroyFab();
        this.destoryBanner(false);
        this.destroyNotificationLayout();
        this.destoryLayout();
    }

    private void destroyNotificationLayout() {
        if (this.closeButtonContainer != null) {
            this.closeButtonContainer.removeAllViews();
            this.closeButtonContainer = null;
        }

        if (this.notificationListContainer != null) {
            this.notificationListContainer.removeAllViews();
            this.notificationListContainer = null;
        }

        if (this.notificationContainerLayout != null) {
            this.notificationContainerLayout.removeAllViews();
            this.notificationContainerLayout = null;
        }
    }

    public void addLayoutToActivity(Activity activity) {
        if (GleapConfig.getInstance().getPlainConfig() == null) {
            return;
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

        // Cleanup.
        this.destoryUI();

        // Recreate layout.
        if (this.layout == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            this.layout = (ConstraintLayout) inflater.inflate(R.layout.activity_gleap_fab, null);
            this.layout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addLocalLayoutToActivity(activity);
        }

        // Initialize the FAB UI.
        addFab(activity);

        // Show the banner if set.
        if (this.bannerData != null) {
            showBanner(this.bannerData, activity);
        }

        // Initialize notifications views.
        createNotificationLayout(activity);
    }

    public void addLocalLayoutToActivity(Activity activity) {
        try {
            if (activity == null) {
                activity = ActivityUtil.getCurrentActivity();
            }
            if (activity == null) {
                return;
            }

            if (GleapBug.getInstance().getApplicationtype() != APPLICATIONTYPE.NATIVE) {
                activity.addContentView(this.layout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            } else {
                ViewGroup viewGroup = (ViewGroup) ((ViewGroup) activity
                        .findViewById(android.R.id.content)).getChildAt(0);
                if (viewGroup.indexOfChild(layout) < 0) {
                    layout.setFocusable(false);
                    viewGroup.addView(layout);
                }
            }
        } catch (Error | Exception ignore) {
        }
    }

    public void destroyFab() {
        if (this.squareButton != null) {
            this.squareButton.setOnClickListener(null);
            this.squareButton = null;
        }

        if (this.imageButton != null) {
            this.imageButton.setOnClickListener(null);
            this.imageButton.setImageDrawable(null);
            this.imageButton = null;
        }

        if (this.notificationCountTextView != null) {
            this.notificationCountTextView = null;
        }

        if (this.feedbackButtonRelativeLayout != null) {
            this.feedbackButtonRelativeLayout.removeAllViews();

            try {
                if (this.layout != null) {
                    this.layout.removeView(this.feedbackButtonRelativeLayout);
                }
            } catch (Exception exp) {}

            this.feedbackButtonRelativeLayout = null;
        }
    }

    public void addFab(Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }

        if (activity == null) {
            return;
        }

        if (this.layout == null) {
            return;
        }

        if (feedbackButtonRelativeLayout != null) {
            return;
        }

        String screenName = activity.getClass().getSimpleName();
        if (screenName.equals("GleapMainActivity")) {
            return;
        }

        try {
            if (feedbackButtonRelativeLayout == null) {
                feedbackButtonRelativeLayout = new ConstraintLayout(activity);
            }

            Activity finalActivity = activity;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    feedbackButtonRelativeLayout.setId(View.generateViewId());
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    feedbackButtonRelativeLayout.setLayoutParams(params);
                    feedbackButtonRelativeLayout.setVisibility(View.INVISIBLE);
                    layout.addView(feedbackButtonRelativeLayout);

                    if (GleapConfig.getInstance().getWidgetPositionType() == WidgetPositionType.CLASSIC) {
                        renderSquareWidget(finalActivity);
                    } else {
                        renderCircularWidget(finalActivity);
                    }
                }
            });
        } catch (Exception ex) {
        }
    }

    void clearMessages() {
        try {
            // Remove all message layouts.
            for (int i = this.messages.size() - 1; i >= 0; i--) {
                GleapChatMessage message = this.messages.get(i);
                removeNotificationViewFromLayout(message);
            }

            // Clear message list.
            this.messages = new LinkedList<>();
        }catch (Exception ex) {
            System.out.println(ex);
        }
    }

    public void setMessageCounter(int messageCounter) {
        this.messageCounter = messageCounter;

        if (notificationCountTextView != null) {
            notificationCountTextView.setText(String.valueOf(this.messageCounter));

            if (this.messageCounter <= 0) {
                notificationCountTextView.setVisibility(View.GONE);
            } else {
                notificationCountTextView.setVisibility(View.VISIBLE);
            }
        }
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
                            if (feedbackButtonRelativeLayout != null) {
                                feedbackButtonRelativeLayout.setVisibility(View.VISIBLE);
                            }
                        } else {
                            if (feedbackButtonRelativeLayout != null) {
                                feedbackButtonRelativeLayout.setVisibility(View.INVISIBLE);
                            }
                        }
                    } else {
                        if (feedbackButtonRelativeLayout != null) {
                            feedbackButtonRelativeLayout.setVisibility(View.INVISIBLE);
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
                feedbackButtonRelativeLayout.setVisibility(View.VISIBLE);
            } else {
                feedbackButtonRelativeLayout.setVisibility(View.GONE);
                return;
            }

            if (feedbackButtonRelativeLayout.indexOfChild(imageButton) < 0) {
                feedbackButtonRelativeLayout.addView(imageButton, convertDpToPixel(56, local), convertDpToPixel(56, local));
            }

            GradientDrawable gdDefaultText = new GradientDrawable();
            gdDefaultText.setColor(Color.RED);

            gdDefaultText.setCornerRadius(1000);
            notificationCountTextView = new TextView(local);
            notificationCountTextView.setId(View.generateViewId());
            notificationCountTextView.setBackground(gdDefaultText);
            notificationCountTextView.setTextColor(Color.WHITE);
            notificationCountTextView.setTextSize(10);
            notificationCountTextView.setText(String.valueOf(messageCounter));
            notificationCountTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            notificationCountTextView.setGravity(Gravity.CENTER);

            ConstraintSet feedbackButtonRelativeLayoutSet = new ConstraintSet();
            feedbackButtonRelativeLayoutSet.clone(feedbackButtonRelativeLayout);
            feedbackButtonRelativeLayoutSet.connect(notificationCountTextView.getId(), ConstraintSet.END, feedbackButtonRelativeLayout.getId(), ConstraintSet.END, 0);
            feedbackButtonRelativeLayoutSet.applyTo(feedbackButtonRelativeLayout);
            notificationCountTextView.bringToFront();
            feedbackButtonRelativeLayout.addView(notificationCountTextView, convertDpToPixel(16, local), convertDpToPixel(16, local));

            if (messageCounter <= 0) {
                notificationCountTextView.setVisibility(View.GONE);
            }

            int offsetX = GleapConfig.getInstance().getButtonX() + 20;
            int offsetY = GleapConfig.getInstance().getButtonY();

            ConstraintSet set = new ConstraintSet();
            set.clone(layout);
            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                set.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX - 20, local));
            } else {
                set.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX - 20, local));
            }
            set.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, local));
            set.applyTo(layout);
        } catch (Exception ex) {}
    }

    private void renderSquareWidget(Activity local) {
        try {
            boolean manualHidden = GleapConfig.getInstance().isHideWidget();
            if (showFab && !manualHidden) {
                feedbackButtonRelativeLayout.setVisibility(View.VISIBLE);
            } else {
                feedbackButtonRelativeLayout.setVisibility(View.GONE);
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
                squareButton.setVisibility(View.INVISIBLE);
                squareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!Gleap.getInstance().isOpened()) {
                            Gleap.getInstance().open();
                            showFab = false;
                        }
                    }
                });

                if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                    feedbackButtonRelativeLayout.setRotation(-90);
                } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                    feedbackButtonRelativeLayout.setRotation(90);
                }

                squareButton.post(new Runnable() {
                    @Override
                    public void run() {
                        int height = squareButton.getHeight();
                        int width = squareButton.getWidth();

                        ConstraintSet buttonConstraintSet = new ConstraintSet();
                        buttonConstraintSet.clone(layout);

                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(20, local));
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                            feedbackButtonRelativeLayout.setPadding(0, (width / 2 - height / 2) + 1, 0, 0);
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 0);
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, width / 2);
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                            feedbackButtonRelativeLayout.setPadding(0, (width / 2 - height / 2) + 1, 0, 0);
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, width / 2);
                            buttonConstraintSet.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                        }

                        buttonConstraintSet.applyTo(layout);
                        animateViewInOut(squareButton, true);
                    }
                });
            }

            if (feedbackButtonRelativeLayout.indexOfChild(squareButton) < 0) {
                feedbackButtonRelativeLayout.addView(squareButton, 0, convertDpToPixel(36, local));
            }
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
}