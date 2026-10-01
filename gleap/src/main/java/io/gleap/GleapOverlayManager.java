package io.gleap;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

import gleap.io.gleap.R;

/**
 * The SDK's overlay above the app's activities: the feedback button, the in-app notification
 * cards, banners and modals. It is rebuilt for every activity that resumes.
 */
class GleapOverlayManager {
    // Created with the class: getInstance() is called from several threads.
    private static final GleapOverlayManager instance = new GleapOverlayManager();
    // The overlay's root view, added to the shown activity.
    ConstraintLayout layout;
    // The activity the overlay's views were added to.
    private WeakReference<Activity> layoutActivity = new WeakReference<>(null);
    final GleapFeedbackButton button = new GleapFeedbackButton(this);
    final GleapNotificationStack notifications = new GleapNotificationStack(this);
    private GleapBanner banner;
    private JSONObject bannerData;
    int messageCounter = 0;
    boolean showFab = false;
    private GleapModal modal;
    private JSONObject modalData;
    private int originalVisibility = 0;
    // While a capture runs the overlay (button, notifications, banner, modal) is hidden: it is
    // not part of the app the customer shows.
    private boolean hiddenForCapture = false;

    private GleapOverlayManager() {
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

    public static GleapOverlayManager getInstance() {
        return instance;
    }

    public void setInvisible() {
        if (button.container != null) {
            button.container.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Hides the whole overlay while a capture runs (main thread).
     */
    void setHiddenForCapture(boolean hidden) {
        hiddenForCapture = hidden;
        if (layout != null) {
            layout.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
        }
    }

    public void setVisible() {
        if (button.container != null && !GleapConfig.getInstance().isHideFeedbackButton()) {
            button.container.setVisibility(View.VISIBLE);
        }
    }

    public void destroyBanner(boolean clearData) {
        if (this.banner != null) {
            LinearLayout bannerLayout = this.banner.getComponent();
            if (bannerLayout != null) {
                ConstraintLayout parentLayout = (ConstraintLayout) bannerLayout.getParent();
                if (parentLayout != null) {
                    parentLayout.removeView(bannerLayout);
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
        if (button.container != null && !ignoreButton) {
            button.container.setVisibility(this.originalVisibility);
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
        LinearLayout bannerLayout = this.banner.getComponent();
        if (bannerLayout != null) {
            if (bannerLayout.getParent() == null) {
                // Setup constraints.
                ConstraintSet bannerSet = new ConstraintSet();
                bannerSet.clone(layout);
                bannerSet.connect(bannerLayout.getId(), ConstraintSet.TOP, layout.getId(), ConstraintSet.TOP, 0); // Connect top of bannerContainer to top of layout
                bannerSet.connect(bannerLayout.getId(), ConstraintSet.START, layout.getId(), ConstraintSet.START, 0); // Connect start of bannerContainer to start of layout
                bannerSet.connect(bannerLayout.getId(), ConstraintSet.END, layout.getId(), ConstraintSet.END, 0); // Connect end of bannerContainer to end of layout
                bannerSet.applyTo(layout);

                // Add banner view.
                layout.addView(bannerLayout);
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
                if (button.container != null) {
                    this.originalVisibility = button.container.getVisibility();
                    button.container.setVisibility(View.GONE);
                }

                // Add modal view.
                layout.addView(innerModalLayout);
            }
        }
    }

    public void destroyLayout() {
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

    public void destroyUI() {
        button.destroyFab();
        this.destroyBanner(false);
        this.destroyModal(false, false);
        notifications.destroyNotificationLayout();
        this.destroyLayout();
    }

    /**
     * The activity the overlay is shown in was destroyed: releases the overlay's views (and the
     * banner's and modal's WebViews), which would otherwise keep the activity in memory until
     * another one resumes. What is shown (notifications, banner, modal) is kept and shown
     * again in the next activity.
     */
    void onActivityDestroyed(Activity activity) {
        if (this.layout != null && layoutActivity.get() == activity) {
            destroyUI();
        }
    }

    /**
     * Re-renders everything that is colored from the widget background after the color scheme
     * changed: the notification cards with their close button, and the modal (which gets its
     * colors resent). The feedback button only uses the button color and the banner has no
     * themed colors.
     */
    void refreshColorScheme() {
        Activity activity = layoutActivity.get();
        if (this.layout != null && activity != null) {
            notifications.refreshColorScheme(activity);
        }

        if (this.modal != null) {
            this.modal.resendModalData();
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

        if (ActivityUtil.isGleapActivity(activity)) {
            return;
        }

        // Cleanup.
        this.destroyUI();

        // Recreate layout.
        if (this.layout == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            this.layout = (ConstraintLayout) inflater.inflate(R.layout.activity_gleap_fab, null);
            this.layout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (hiddenForCapture) {
                this.layout.setVisibility(View.INVISIBLE);
            }
            addLocalLayoutToActivity(activity);
        }

        // Initialize the FAB UI.
        button.addFab(activity);

        // Show the banner if set.
        if (this.bannerData != null) {
            showBanner(this.bannerData, activity);
        }

        // Show the modal if set.
        if (this.modalData != null) {
            showModal(this.modalData, activity);
        }

        // Initialize notifications views.
        notifications.createNotificationLayout(activity);
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
            layoutActivity = new WeakReference<>(activity);
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

    public void addNotification(GleapChatMessage comment, Activity activity) {
        notifications.addNotification(comment, activity);
    }

    void clearMessages() {
        notifications.clearMessages();
    }

    /**
     * A collapsed stack expands on the first tap instead of activating the
     * front card — same as the web widget on touch devices. Returns true when
     * the tap was consumed by the expansion.
     */
    boolean maybeExpandStackOnTap() {
        return notifications.maybeExpandStackOnTap();
    }

    public void setMessageCounter(int messageCounter) {
        this.messageCounter = messageCounter;

        try {
            if (GleapCallbacks.getInstance().getNotificationUnreadCountUpdatedCallback() != null) {
                GleapCallbacks.getInstance().getNotificationUnreadCountUpdatedCallback().invoke(messageCounter);
            }
        } catch (Exception exp) {}

        TextView notificationCountTextView = button.notificationCountTextView;
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
                            if (button.container != null) {
                                button.container.setVisibility(View.VISIBLE);

                                // Re-add classic button.
                                if (GleapConfig.getInstance().getWidgetPositionType() == WidgetPositionType.CLASSIC) {
                                    Activity currentActivity = ActivityUtil.getCurrentActivity();
                                    if (currentActivity != null) {
                                        button.renderClassicFeedbackButton(currentActivity);
                                    }
                                }
                            }
                        } else {
                            if (button.container != null) {
                                button.container.setVisibility(View.INVISIBLE);
                            }
                        }
                    } else {
                        if (button.container != null) {
                            button.container.setVisibility(View.INVISIBLE);
                        }
                    }

                    // The notification container anchors to the button when it
                    // is visible — follow the state change.
                    notifications.applyNotificationContainerConstraints(ActivityUtil.getCurrentActivity());
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

}