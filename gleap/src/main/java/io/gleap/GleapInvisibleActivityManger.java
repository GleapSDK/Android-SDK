package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
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
    private FrameLayout notificationStackFrame;
    private ImageButton imageButton;
    private Bitmap fabIcon;
    private FrameLayout closeButtonContainer;
    private boolean stackExpanded = false;
    private View pendingEntranceView;
    private Button squareButton;
    private GleapBanner banner;
    private JSONObject bannerData;
    private ConstraintLayout feedbackButtonRelativeLayout;
    private int messageCounter = 0;
    boolean showFab = false;
    public boolean attached = false;
    private GleapModal modal;
    private JSONObject modalData;
    private int originalVisibility = 0;

    private GleapInvisibleActivityManger() {
        messages = new LinkedList<>();
    }

    public static void animateViewInOut(View view, boolean show) {
        if (view == null) {
            return;
        }

        if (show && view.getVisibility() == View.VISIBLE) {
            return;
        }

        if (!show && (view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE)) {
            return;
        }

        view.setAlpha(show ? 0f : 1f);
        if (show) {
            view.setVisibility(View.VISIBLE);
        }

        ObjectAnimator fadeInAnimation = ObjectAnimator.ofFloat(view, "alpha", show ? 0f : 1f, show ? 1f : 0f);
        fadeInAnimation.setDuration(200);
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
            feedbackButtonRelativeLayout.setVisibility(View.INVISIBLE);
        }
    }

    public void setVisible() {
        if (feedbackButtonRelativeLayout != null && !GleapConfig.getInstance().isHideFeedbackButton()) {
            feedbackButtonRelativeLayout.setVisibility(View.VISIBLE);
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

                    boolean manualHidden = GleapConfig.getInstance().isHideFeedbackButton();
                    boolean canShowFeedbackButton = showFab && !manualHidden;

                    // Feedback button hidden - apply default constraints plus optional notification container offset.
                    if (feedbackButtonRelativeLayout == null || !canShowFeedbackButton) {
                        int containerOffsetX = GleapConfig.getInstance().getNotificationContainerOffsetX();
                        int containerOffsetY = GleapConfig.getInstance().getNotificationContainerOffsetY();
                        set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(20 + containerOffsetY, finalActivity));
                        set.connect(notificationContainerLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(20 + containerOffsetX, finalActivity));
                    } else {
                        // Apply constraints based on feedback button type.
                        int containerOffsetX = GleapConfig.getInstance().getNotificationContainerOffsetX();
                        int containerOffsetY = GleapConfig.getInstance().getNotificationContainerOffsetY();
                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_LEFT) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(15 + containerOffsetY, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX + containerOffsetX, finalActivity));
                            viewPadding = offsetX;
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(15 + containerOffsetY, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX + containerOffsetX, finalActivity));
                            viewPadding = offsetX;
                            notificationContainerLayout.setGravity(Gravity.RIGHT);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY + containerOffsetY, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX + containerOffsetX, finalActivity));
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, feedbackButtonRelativeLayout.getId(), ConstraintSet.TOP, convertDpToPixel(15 + containerOffsetY, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(20 + containerOffsetX, finalActivity));
                            notificationContainerLayout.setGravity(Gravity.RIGHT);
                        } else {
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY + containerOffsetY, finalActivity));
                            set.connect(notificationContainerLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(20 + containerOffsetX, finalActivity));
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

                    // The stack frame holds the cards (bottom-anchored, the
                    // newest in front) plus the floating close button. Nothing
                    // on this path may clip — peeking card edges, the close
                    // button overhang and the card shadows all draw outside
                    // their parents' bounds.
                    notificationContainerLayout.setClipChildren(false);
                    notificationContainerLayout.setClipToPadding(false);
                    layout.setClipChildren(false);
                    layout.setClipToPadding(false);

                    if (notificationStackFrame == null) {
                        notificationStackFrame = new FrameLayout(finalActivity);
                        notificationStackFrame.setClipChildren(false);
                        notificationStackFrame.setClipToPadding(false);
                        notificationContainerLayout.addView(notificationStackFrame, new LinearLayout.LayoutParams(GleapNotificationStyle.stackWidthPx(finalActivity), LinearLayout.LayoutParams.WRAP_CONTENT));
                    }

                    // The close button floats over the stack's top corner
                    // instead of taking a row of its own above it. Its
                    // elevation keeps it above the cards' shadows.
                    if (closeButtonContainer == null) {
                        closeButtonContainer = new FrameLayout(finalActivity);
                        GradientDrawable closeBackground = new GradientDrawable();
                        closeBackground.setShape(GradientDrawable.OVAL);
                        closeBackground.setColor(GleapNotificationStyle.backgroundColor());
                        closeButtonContainer.setBackground(closeBackground);
                        closeButtonContainer.setElevation(convertDpToPixel(8, finalActivity));

                        ImageView closeCross = new ImageView(finalActivity);
                        closeCross.setImageResource(R.drawable.close_white);
                        closeCross.setColorFilter(GleapNotificationStyle.contrastColor());
                        int crossSize = convertDpToPixel(10, finalActivity);
                        closeButtonContainer.addView(closeCross, new FrameLayout.LayoutParams(crossSize, crossSize, Gravity.CENTER));

                        closeButtonContainer.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                clearMessages();
                            }
                        });

                        closeButtonContainer.setVisibility(View.GONE);
                        int closeSize = convertDpToPixel(26, finalActivity);
                        notificationStackFrame.addView(closeButtonContainer, new FrameLayout.LayoutParams(closeSize, closeSize, Gravity.TOP | Gravity.END));
                    }

                    // Initially add all messages (if any)
                    if (messages.size() > 0) {
                        for (GleapChatMessage notification : messages) {
                            addNotificationViewToLayout(notification, finalActivity);
                        }
                        updateCloseButtonState();
                    }
                } catch (Exception ex) {
                    System.out.println(ex);
                }
            }
        });
    }

    public void removeNotificationViewFromLayout(GleapChatMessage notification) {
        try {
            LinearLayout component = notification.getComponent(null);
            if (component != null && component.getParent() instanceof ViewGroup) {
                ((ViewGroup) component.getParent()).removeView(component);
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

        updateCloseButtonState();
        relayoutStack(false);
    }

    public void updateCloseButtonState() {
        if (closeButtonContainer != null) {
            if (this.messages.size() > 0) {
                animateViewInOut(closeButtonContainer, true);
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

        if (notificationStackFrame == null) {
            return;
        }

        LinearLayout commentComponent = notification.getComponent(activity);
        if (commentComponent != null && commentComponent.getParent() == null) {
            // Bottom-anchored: the stack math positions every card purely via
            // translationY, and the add order keeps the newest card in front.
            notificationStackFrame.addView(commentComponent, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            pendingEntranceView = commentComponent;
        }
        relayoutStack(true);
    }

    public void destroyBanner(boolean clearData) {
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

    public void destroyModal(boolean clearData, boolean ignoreButton) {
        if (this.modal != null) {
            LinearLayout innerModalLayout = this.modal.getComponent();
            if (innerModalLayout != null) {
                ConstraintLayout parentLayout = (ConstraintLayout) innerModalLayout.getParent();
                if (parentLayout != null) {
                    parentLayout.removeView(innerModalLayout);
                }
            }

            this.modal.clearComponent();
            this.modal = null;
        }

        // Revert background color.
        if (layout != null) {
            layout.setBackgroundColor(Color.TRANSPARENT);
        }

        // Show feedback button.
        if (feedbackButtonRelativeLayout != null && !ignoreButton) {
            feedbackButtonRelativeLayout.setVisibility(this.originalVisibility);
        }

        if (clearData) {
            this.modalData = null;
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

        if (this.banner != null) {
            this.destroyBanner(true);
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

    public void showModal(JSONObject modalData, Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }

        if (activity == null || modalData == null) {
            return;
        }

        if (this.layout == null) {
            return;
        }

        if (this.modal != null) {
            this.destroyModal(true, true);
        }

        this.modalData = modalData;
        this.modal = new GleapModal(this.modalData, activity);

        // Attach the modal to the current layout.
        LinearLayout innerModalLayout = this.modal.getComponent();
        if (innerModalLayout != null) {
            if (innerModalLayout.getParent() == null) {
                // Setup constraints.
                ConstraintSet modalSet = new ConstraintSet();
                modalSet.clone(layout);
                modalSet.connect(innerModalLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, 0);
                modalSet.connect(innerModalLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 0);
                modalSet.connect(innerModalLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0);
                modalSet.connect(innerModalLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, 0);
                modalSet.applyTo(layout);

                // Set stage for backdrop.
                layout.setBackgroundColor(Color.parseColor("#80000000"));

                // Hide feedback button.
                if (feedbackButtonRelativeLayout != null) {
                    this.originalVisibility = feedbackButtonRelativeLayout.getVisibility();
                    feedbackButtonRelativeLayout.setVisibility(View.GONE);
                }

                // Add modal view.
                layout.addView(innerModalLayout);
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

        // More than one notification renders as a collapsed stack (newest in
        // front), so a higher cap no longer costs vertical space. The oldest
        // drop off beyond it.
        while (this.messages.size() >= 4) {
            removeNotificationViewFromLayout(this.messages.get(0));
        }

        // Make sure to only show one news or checklist notification at a time. If
        // either is already in the list, remove it first. Collected up front:
        // removeNotificationViewFromLayout mutates the message list, so it must
        // not run inside an iteration over it.
        if (comment.getType().equals("news") || comment.getType().equals("checklist")) {
            List<GleapChatMessage> messagesToRemove = new ArrayList<>();
            for (GleapChatMessage message : this.messages) {
                if (message.getType().equals("news") || message.getType().equals("checklist")) {
                    messagesToRemove.add(message);
                }
            }
            for (GleapChatMessage message : messagesToRemove) {
                removeNotificationViewFromLayout(message);
            }
        }

        // A new arrival collapses the stack again.
        this.stackExpanded = false;

        this.messages.add(comment);
        addNotificationViewToLayout(comment, activity);
        updateCloseButtonState();
    }

    public void destoryLayout() {
        if (this.layout != null) {
            this.layout.removeAllViews();
            this.layout.setOnApplyWindowInsetsListener(null);

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
        this.destroyBanner(false);
        this.destroyModal(false, false);
        this.destroyNotificationLayout();
        this.destoryLayout();
    }

    private void destroyNotificationLayout() {
        if (this.closeButtonContainer != null) {
            this.closeButtonContainer.removeAllViews();
            this.closeButtonContainer = null;
        }

        if (this.notificationStackFrame != null) {
            this.notificationStackFrame.removeAllViews();
            this.notificationStackFrame = null;
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

        // Show the modal if set.
        if (this.modalData != null) {
            showModal(this.modalData, activity);
        }

        // Initialize notifications views.
        createNotificationLayout(activity);

        this.attached = true;
    }

    public void addLocalLayoutToActivity(Activity activity) {
        try {
            if (activity == null) {
                activity = ActivityUtil.getCurrentActivity();
            }
            if (activity == null) {
                return;
            }

            activity.addContentView(this.layout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            layout.setFocusable(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                this.layout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        int topPadding = insets.getSystemWindowInsetTop();
                        int bottomPadding = insets.getSystemWindowInsetBottom();
                        v.setPadding(0, topPadding, 0, bottomPadding);
                        return insets.consumeSystemWindowInsets();
                    }
                });
                this.layout.requestApplyInsets();
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

        if (this.fabIcon != null) {
            this.fabIcon.recycle();
            this.fabIcon = null;
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
                        renderClassicFeedbackButton(finalActivity);
                    } else {
                        renderModernFeedbackButton(finalActivity);
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
            this.stackExpanded = false;
        }catch (Exception ex) {
            System.out.println(ex);
        }
    }

    /**
     * A collapsed stack expands on the first tap instead of activating the
     * front card — same as the web widget on touch devices. Returns true when
     * the tap was consumed by the expansion.
     */
    boolean maybeExpandStackOnTap() {
        if (this.messages.size() > 1 && !stackExpanded) {
            stackExpanded = true;
            applyStackLayout(null, true);
            return true;
        }
        return false;
    }

    private void relayoutStack(boolean withEntrance) {
        if (notificationStackFrame == null) {
            return;
        }

        final View entranceView = withEntrance ? pendingEntranceView : null;
        pendingEntranceView = null;
        notificationStackFrame.post(new Runnable() {
            @Override
            public void run() {
                applyStackLayout(entranceView, false);
            }
        });
    }

    /**
     * Places every card for the current stack state. Cards are bottom-anchored
     * in the stack frame: expanded they form a column with a fixed gap,
     * collapsed the newest card sits in front with up to two older cards
     * peeking out behind its top edge, scaled back like a deck. Anything
     * deeper stays hidden until the stack expands.
     *
     * The frame always keeps the expanded height — collapsing only transforms
     * the cards. The frame itself is not clickable, so the empty area above a
     * collapsed stack stays transparent to touches.
     */
    private void applyStackLayout(View entranceView, boolean animate) {
        try {
            if (notificationStackFrame == null) {
                return;
            }

            Activity activity = ActivityUtil.getCurrentActivity();
            if (activity == null) {
                return;
            }

            // Cards in visual order: oldest first, the newest last — the front
            // card of the stack, and the bottom card of the expanded list.
            List<View> cards = new ArrayList<>();
            for (GleapChatMessage message : this.messages) {
                LinearLayout component = message.getComponent(null);
                if (component != null && component.getParent() == notificationStackFrame) {
                    cards.add(component);
                }
            }

            if (cards.isEmpty()) {
                return;
            }

            int gap = convertDpToPixel(12, activity);
            int headroom = convertDpToPixel(17, activity);
            int stackWidth = GleapNotificationStyle.stackWidthPx(activity);

            // Measure the heights — a just-added card has not been laid out yet.
            int count = cards.size();
            int[] heights = new int[count];
            for (int i = 0; i < count; i++) {
                View card = cards.get(i);
                int height = card.getHeight();
                if (height <= 0) {
                    card.measure(View.MeasureSpec.makeMeasureSpec(stackWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    height = card.getMeasuredHeight();
                }
                heights[i] = height;
            }

            int frontHeight = heights[count - 1];
            int expandedHeight = (count - 1) * gap;
            for (int i = 0; i < count; i++) {
                expandedHeight += heights[i];
            }

            ViewGroup.LayoutParams frameParams = notificationStackFrame.getLayoutParams();
            if (frameParams != null && frameParams.height != expandedHeight) {
                frameParams.height = expandedHeight;
                notificationStackFrame.setLayoutParams(frameParams);
            }

            boolean collapsed = count > 1 && !stackExpanded;

            int newerHeights = 0;
            for (int i = count - 1; i >= 0; i--) {
                View card = cards.get(i);
                int depth = (count - 1) - i;

                float targetTy;
                float targetScale;
                float targetAlpha = 1f;
                Rect clip = null;

                if (collapsed && depth > 0) {
                    // Tuck the card's top edge `peek`px above the front card's
                    // top; anything deeper than two peeks hides entirely.
                    int peek = convertDpToPixel(depth == 1 ? 9 : 17, activity);
                    targetScale = depth == 1 ? 0.955f : 0.91f;
                    targetTy = heights[i] - frontHeight - peek;
                    if (depth > 2) {
                        targetAlpha = 0f;
                    }

                    // Clips a taller card behind down to the front card's
                    // bottom edge, so e.g. a news cover can't hang out below
                    // the stack. The generous negative insets keep the shadow
                    // outside the clipped edge alive.
                    int visibleHeight = (int) ((frontHeight + peek) / targetScale);
                    if (heights[i] > visibleHeight) {
                        int overscan = convertDpToPixel(40, activity);
                        clip = new Rect(-overscan, -overscan, stackWidth + overscan, visibleHeight);
                    }
                } else {
                    targetScale = 1f;
                    targetTy = -(newerHeights + (depth * gap));
                }

                // transform-origin: top center.
                card.setPivotX(stackWidth / 2f);
                card.setPivotY(0f);

                if (animate) {
                    // Release the clip before the spread so nothing pops
                    // mid-animation.
                    card.setClipBounds(null);
                    card.animate()
                            .translationY(targetTy)
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .alpha(targetAlpha)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator(2f))
                            .start();
                } else {
                    card.animate().cancel();
                    card.setTranslationY(targetTy);
                    card.setScaleX(targetScale);
                    card.setScaleY(targetScale);
                    card.setAlpha(targetAlpha);
                    card.setClipBounds(clip);
                }

                newerHeights += heights[i];
            }

            // Only the just-arrived front card plays the entrance animation —
            // a slide-up with a fade, matching the web widget.
            if (entranceView != null && !animate) {
                float restingTy = entranceView.getTranslationY();
                entranceView.setAlpha(0f);
                entranceView.setTranslationY(restingTy + convertDpToPixel(12, activity));
                entranceView.animate()
                        .alpha(1f)
                        .translationY(restingTy)
                        .setDuration(450)
                        .setInterpolator(new DecelerateInterpolator(2f))
                        .start();
            }

            // The close button floats 9dp outside the stack's visual top
            // corner and rides along as the stack expands or collapses.
            if (closeButtonContainer != null) {
                int overhang = convertDpToPixel(9, activity);
                float visualTop = collapsed ? expandedHeight - (frontHeight + headroom) : 0;
                float closeTy = visualTop - overhang;
                boolean isRTL = notificationStackFrame.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                closeButtonContainer.setTranslationX(isRTL ? -overhang : overhang);
                if (animate) {
                    closeButtonContainer.animate()
                            .translationY(closeTy)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator(2f))
                            .start();
                } else {
                    closeButtonContainer.animate().cancel();
                    closeButtonContainer.setTranslationY(closeTy);
                }
            }
        } catch (Exception exp) {
        }
    }

    public void setMessageCounter(int messageCounter) {
        this.messageCounter = messageCounter;

        try {
            if (GleapConfig.getInstance().getNotificationUnreadCountUpdatedCallback() != null) {
                GleapConfig.getInstance().getNotificationUnreadCountUpdatedCallback().invoke(messageCounter);
            }
        } catch (Exception exp) {}

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
                    boolean manualHidden = GleapConfig.getInstance().isHideFeedbackButton();
                    if (!manualHidden) {
                        if (showFabIn) {
                            if (feedbackButtonRelativeLayout != null) {
                                feedbackButtonRelativeLayout.setVisibility(View.VISIBLE);

                                // Re-add classic button.
                                if (GleapConfig.getInstance().getWidgetPositionType() == WidgetPositionType.CLASSIC) {
                                    Activity currentActivity = ActivityUtil.getCurrentActivity();
                                    if (currentActivity != null) {
                                        renderClassicFeedbackButton(currentActivity);
                                    }
                                }
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

    private void renderModernFeedbackButton(Activity local) {
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
                imageButton.setVisibility(View.INVISIBLE);

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

            boolean manualHidden = GleapConfig.getInstance().isHideFeedbackButton();
            if (showFab && !manualHidden) {
                feedbackButtonRelativeLayout.setVisibility(View.VISIBLE);
            } else {
                feedbackButtonRelativeLayout.setVisibility(View.GONE);
            }

            if (feedbackButtonRelativeLayout.indexOfChild(imageButton) < 0) {
                feedbackButtonRelativeLayout.addView(imageButton, convertDpToPixel(54, local), convertDpToPixel(54, local));
            }

            GradientDrawable gdDefaultText = new GradientDrawable();
            gdDefaultText.setColor(Color.RED);

            gdDefaultText.setCornerRadius(1000);

            notificationCountTextView = new TextView(local);
            notificationCountTextView.setId(View.generateViewId());
            notificationCountTextView.setBackground(gdDefaultText);
            notificationCountTextView.setTextColor(Color.WHITE);
            notificationCountTextView.setTextSize(12);
            notificationCountTextView.setText(String.valueOf(messageCounter));
            notificationCountTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            notificationCountTextView.setGravity(Gravity.CENTER);
            notificationCountTextView.setVisibility(View.GONE);
            notificationCountTextView.bringToFront();
            feedbackButtonRelativeLayout.addView(notificationCountTextView, convertDpToPixel(18, local), convertDpToPixel(18, local));

            if (fabIcon == null) {
                new GleapRoundImageHandler(GleapConfig.getInstance().getButtonLogo(), imageButton, new GleapImageLoaded() {
                    @Override
                    public void invoke(Bitmap bitmap) {
                        fabIcon = bitmap;

                        local.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                GleapInvisibleActivityManger.animateViewInOut(imageButton, true);
                            }
                        });
                    }
                }).execute();
            } else {
                // Instantly show FAB if icon is already loaded.
                imageButton.setImageBitmap(fabIcon);
                imageButton.setVisibility(View.VISIBLE);
            }

            int offsetX = GleapConfig.getInstance().getButtonX() + 20;
            int offsetY = GleapConfig.getInstance().getButtonY();

            ConstraintSet set = new ConstraintSet();
            set.clone(layout);
            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT || GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.HIDDEN) {
                set.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX - 20, local));
            } else {
                set.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX - 20, local));
            }
            set.connect(feedbackButtonRelativeLayout.getId(), ConstraintSet.BOTTOM, layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, local));
            set.applyTo(layout);
        } catch (Exception ex) {}
    }

    private void renderClassicFeedbackButton(Activity local) {
        try {
            boolean manualHidden = GleapConfig.getInstance().isHideFeedbackButton();
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