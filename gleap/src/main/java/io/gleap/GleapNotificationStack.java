package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import gleap.io.gleap.R;

/**
 * The in-app notification cards above the app: a stack of up to four cards (newest in front,
 * collapsed like a deck until tapped) with a close button, anchored next to the feedback button.
 */
class GleapNotificationStack {
    private final GleapOverlayManager owner;
    private List<GleapChatMessage> messages = new LinkedList<>();
    private LinearLayout notificationContainerLayout;
    private FrameLayout notificationStackFrame;
    private FrameLayout closeButtonContainer;
    private boolean stackExpanded = false;
    private View pendingEntranceView;

    GleapNotificationStack(GleapOverlayManager owner) {
        this.owner = owner;
    }

    /**
     * (Re)applies the notification container's position constraints. Runs on
     * creation and again whenever the feedback button's visibility flips —
     * the container anchors to the button when it is shown, and the button's
     * state can settle after the container was first built (the config
     * applies asynchronously). Without the re-apply, notifications rendered
     * in that window sat at the bottom of the screen until the next rebuild.
     */
    void applyNotificationContainerConstraints(Activity activity) {
        try {
            if (owner.layout == null || notificationContainerLayout == null || activity == null) {
                return;
            }

            int offsetX = GleapConfig.getInstance().getButtonX();
            int offsetY = GleapConfig.getInstance().getButtonY();

            ConstraintSet set = new ConstraintSet();
            set.clone(owner.layout);

            // Reset both horizontal anchors — a re-apply may switch sides.
            set.clear(notificationContainerLayout.getId(), ConstraintSet.START);
            set.clear(notificationContainerLayout.getId(), ConstraintSet.END);

            // The container carries the stack frame's fixed height explicitly:
            // left at WRAP_CONTENT, ConstraintLayout measures it AT_MOST the
            // parent's height, the taller frame inside overflows past the
            // container's bottom, and the cards render below the screen.
            set.constrainHeight(notificationContainerLayout.getId(), GleapNotificationStyle.stackFrameHeightPx(activity));

            int viewPadding = 20;

            boolean manualHidden = GleapConfig.getInstance().isHideFeedbackButton();
            boolean canShowFeedbackButton = owner.showFab && !manualHidden;

            // Feedback button hidden - apply default constraints plus optional notification container offset.
            if (owner.button.container == null || !canShowFeedbackButton) {
                int containerOffsetX = GleapConfig.getInstance().getNotificationContainerOffsetX();
                int containerOffsetY = GleapConfig.getInstance().getNotificationContainerOffsetY();
                set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, owner.layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(20 + containerOffsetY, activity));
                set.connect(notificationContainerLayout.getId(), ConstraintSet.START, owner.layout.getId(), ConstraintSet.START, convertDpToPixel(20 + containerOffsetX, activity));
            } else {
                // Apply constraints based on feedback button type.
                int containerOffsetX = GleapConfig.getInstance().getNotificationContainerOffsetX();
                int containerOffsetY = GleapConfig.getInstance().getNotificationContainerOffsetY();
                if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_LEFT) {
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, owner.button.container.getId(), ConstraintSet.TOP, convertDpToPixel(15 + containerOffsetY, activity));
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.START, owner.layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX + containerOffsetX, activity));
                    viewPadding = offsetX;
                } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT) {
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, owner.button.container.getId(), ConstraintSet.TOP, convertDpToPixel(15 + containerOffsetY, activity));
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.END, owner.layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX + containerOffsetX, activity));
                    viewPadding = offsetX;
                    notificationContainerLayout.setGravity(Gravity.RIGHT);
                } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, owner.layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY + containerOffsetY, activity));
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.START, owner.layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX + containerOffsetX, activity));
                } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, owner.button.container.getId(), ConstraintSet.TOP, convertDpToPixel(15 + containerOffsetY, activity));
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.END, owner.layout.getId(), ConstraintSet.END, convertDpToPixel(20 + containerOffsetX, activity));
                    notificationContainerLayout.setGravity(Gravity.RIGHT);
                } else {
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.BOTTOM, owner.layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY + containerOffsetY, activity));
                    set.connect(notificationContainerLayout.getId(), ConstraintSet.END, owner.layout.getId(), ConstraintSet.END, convertDpToPixel(20 + containerOffsetX, activity));
                    notificationContainerLayout.setGravity(Gravity.RIGHT);
                }
            }

            // Set max width.
            try {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int deviceWidth = displayMetrics.widthPixels;
                int deviceHeight = displayMetrics.heightPixels;
                int smallerDimension = Math.min(deviceWidth, deviceHeight);
                int maxWidthPx = smallerDimension - convertDpToPixel(viewPadding * 2, activity);
                set.constrainMaxWidth(notificationContainerLayout.getId(), maxWidthPx);
            } catch (Exception exp) {}

            set.applyTo(owner.layout);
        } catch (Exception exp) {
        }
    }

    void createNotificationLayout(Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }

        if (activity == null) {
            return;
        }

        if (owner.layout == null) {
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

                    owner.layout.addView(notificationContainerLayout);

                    applyNotificationContainerConstraints(finalActivity);

                    // The stack frame holds the cards (bottom-anchored, the
                    // newest in front) plus the floating close button. Nothing
                    // on this path may clip — peeking card edges, the close
                    // button overhang and the card shadows all draw outside
                    // their parents' bounds.
                    notificationContainerLayout.setClipChildren(false);
                    notificationContainerLayout.setClipToPadding(false);
                    owner.layout.setClipChildren(false);
                    owner.layout.setClipToPadding(false);

                    if (notificationStackFrame == null) {
                        notificationStackFrame = new FrameLayout(finalActivity);
                        notificationStackFrame.setClipChildren(false);
                        notificationStackFrame.setClipToPadding(false);

                        // The frame keeps one FIXED height, tall enough for any
                        // stack. Resizing it per arrival re-anchored the
                        // bottom-pinned cards mid-animation — the whole deck
                        // rendered offset by the height delta and visibly slid
                        // into place. With a constant height nothing ever
                        // re-bases; only the card animators move cards. The
                        // frame is transparent and not clickable, so the empty
                        // space above the cards stays inert.
                        int stackFrameHeight = GleapNotificationStyle.stackFrameHeightPx(finalActivity);
                        notificationContainerLayout.addView(notificationStackFrame, new LinearLayout.LayoutParams(GleapNotificationStyle.stackWidthPx(finalActivity), stackFrameHeight));
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
                        // Above the cards' 4dp elevation, with the same
                        // softened shadow tint.
                        closeButtonContainer.setElevation(convertDpToPixel(6, finalActivity));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            closeButtonContainer.setOutlineSpotShadowColor(Color.argb(150, 0, 0, 0));
                        }

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

                    // Initially add all messages (if any). Re-adds after a
                    // rebuild are not arrivals — no entrance animation.
                    if (messages.size() > 0) {
                        for (GleapChatMessage notification : messages) {
                            addNotificationViewToLayout(notification, finalActivity, false);
                        }
                        updateCloseButtonState();
                    }
                } catch (Exception ex) {
                    GleapLog.w("Could not build the notification layout", ex);
                }
            }
        });
    }

    void removeNotificationViewFromLayout(GleapChatMessage notification) {
        try {
            LinearLayout component = notification.getComponent(null);
            if (component != null && component.getParent() instanceof ViewGroup) {
                ((ViewGroup) component.getParent()).removeView(component);
            }
        } catch (Exception exp) {
            GleapLog.w("Could not remove a notification", exp);
        }

        try {
            notification.clearComponent();
        } catch (Exception exp) {
            GleapLog.w("Could not clear a notification", exp);
        }

        // Remove from list.
        this.messages.remove(notification);

        updateCloseButtonState();
        relayoutStack(false);
    }

    void updateCloseButtonState() {
        if (closeButtonContainer != null) {
            if (this.messages.size() > 0) {
                // Its elevation shadow ignores alpha and would pop in at full
                // strength under the still-transparent button — ramp it with
                // the fade.
                if (closeButtonContainer.getVisibility() != View.VISIBLE) {
                    try {
                        float targetElevation = convertDpToPixel(6, ActivityUtil.getCurrentActivity());
                        ObjectAnimator elevationAnimator = ObjectAnimator.ofFloat(closeButtonContainer, "elevation", 0f, targetElevation);
                        elevationAnimator.setDuration(200);
                        elevationAnimator.start();
                    } catch (Exception exp) {
                    }
                }
                GleapOverlayManager.animateViewInOut(closeButtonContainer, true);
            } else {
                closeButtonContainer.setVisibility(View.GONE);
            }
        }
    }

    void addNotificationViewToLayout(GleapChatMessage notification, Activity activity, boolean isNewArrival) {
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
            if (isNewArrival) {
                // Invisible until the stack layout pass places it and starts
                // the entrance — it must never flash at a resting position.
                commentComponent.setAlpha(0f);
                pendingEntranceView = commentComponent;
            }
        }
        relayoutStack(isNewArrival);
    }

    void addNotification(GleapChatMessage comment, Activity activity) {
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
        addNotificationViewToLayout(comment, activity, true);
        updateCloseButtonState();
    }

    void destroyNotificationLayout() {
        // The cards' views belong to the activity they were built for: the next activity builds
        // its own (the messages stay).
        for (GleapChatMessage message : this.messages) {
            try {
                message.clearComponent();
            } catch (Exception exp) {
                GleapLog.w("Could not clear a notification", exp);
            }
        }

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
        } catch (Exception ex) {
            GleapLog.w("Could not clear the notifications", ex);
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

    // An elevation shadow is drawn from the view's outline and ignores the
    // view's alpha — under a card fading in, the shadow would pop to full
    // strength instantly (a short dark flicker before the card appears).
    // Ramp the card's elevation from zero alongside the fade instead.
    private void rampCardElevationWithFade(View cardRoot) {
        try {
            if (!(cardRoot instanceof ViewGroup)) {
                return;
            }
            View inner = ((ViewGroup) cardRoot).getChildAt(0);
            if (!(inner instanceof CardView)) {
                return;
            }
            CardView cardView = (CardView) inner;
            float targetElevation = cardView.getCardElevation();
            if (targetElevation <= 0f) {
                return;
            }
            ObjectAnimator elevationAnimator = ObjectAnimator.ofFloat(cardView, "cardElevation", 0f, targetElevation);
            elevationAnimator.setDuration(350);
            elevationAnimator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
            elevationAnimator.start();
        } catch (Exception exp) {
        }
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

            int frameHeight = GleapNotificationStyle.stackFrameHeightPx(activity);
            ViewGroup.LayoutParams frameParams = notificationStackFrame.getLayoutParams();
            if (frameParams != null && frameParams.height > 0) {
                frameHeight = frameParams.height;
            }

            boolean collapsed = count > 1 && !stackExpanded;

            // A new arrival on an existing stack is choreographed as one deck
            // motion: the previous cards animate back into their tuck while
            // the new card emerges from the stack's front slot — rather than
            // the old front snapping back and the new card floating up from
            // the empty space below the stack.
            boolean arrival = entranceView != null && !animate && collapsed;

            int newerHeights = 0;
            for (int i = count - 1; i >= 0; i--) {
                View card = cards.get(i);
                int depth = (count - 1) - i;

                float targetTy;
                float targetScale;
                float targetAlpha = 1f;
                int overscan = convertDpToPixel(60, activity);
                // Every card carries a clip at ALL times. The default opens
                // generously past the body (a visual no-op — the elevation
                // shadow is outline-based and ignores clipBounds entirely, so
                // nothing is ever cut in a resting state). Collapsed cards
                // behind the front clip to the front card's height in card
                // space, like the web widget, and every transition ANIMATES
                // the clip in lockstep with the card's motion — so a tall
                // card's body can never poke out below the stack mid-flight.
                Rect clip = new Rect(-overscan, -overscan, stackWidth + overscan, heights[i] + overscan);

                if (collapsed && depth > 0) {
                    // Tuck the card's top edge `peek`px above the front card's
                    // top; anything deeper than two peeks hides entirely.
                    int peek = convertDpToPixel(depth == 1 ? 9 : 17, activity);
                    targetScale = depth == 1 ? 0.955f : 0.91f;
                    targetTy = heights[i] - frontHeight - peek;
                    if (depth > 2) {
                        targetAlpha = 0f;
                    }

                    if (heights[i] > frontHeight) {
                        clip = new Rect(-overscan, -overscan, stackWidth + overscan, frontHeight);
                    }
                } else {
                    targetScale = 1f;
                    targetTy = -(newerHeights + (depth * gap));
                }

                // transform-origin: top center.
                card.setPivotX(stackWidth / 2f);
                card.setPivotY(0f);

                if (animate || (arrival && card != entranceView)) {
                    // The clip animates in lockstep with the card (same
                    // duration and curve), starting clamped to the card's
                    // body — a visual no-op, but it guarantees the sweeping
                    // edge stays at or above the front card's bottom for the
                    // whole flight.
                    Rect startClip = card.getClipBounds();
                    if (startClip == null) {
                        startClip = new Rect(-overscan, -overscan, stackWidth + overscan, heights[i]);
                    } else if (startClip.bottom > heights[i]) {
                        startClip = new Rect(startClip.left, startClip.top, startClip.right, heights[i]);
                    }
                    card.setClipBounds(startClip);
                    ObjectAnimator clipAnimator = ObjectAnimator.ofObject(card, "clipBounds", new RectEvaluator(), startClip, clip);
                    clipAnimator.setDuration(350);
                    clipAnimator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
                    clipAnimator.start();

                    card.animate()
                            .translationY(targetTy)
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .alpha(targetAlpha)
                            .setDuration(350)
                            .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f))
                            .start();
                } else if (arrival) {
                    // The new front card materializes in its slot — a fade
                    // with a slight scale-up and NO travel, so it can never
                    // read as arriving from somewhere else on the screen.
                    card.animate().cancel();
                    card.setClipBounds(clip);
                    card.setTranslationY(targetTy);
                    card.setScaleX(0.97f);
                    card.setScaleY(0.97f);
                    card.setAlpha(0f);
                    rampCardElevationWithFade(card);
                    card.animate()
                            .translationY(targetTy)
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .alpha(1f)
                            .setDuration(350)
                            .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f))
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

            // The very first notification has no stack to emerge from — it
            // slides up with a fade, matching the web widget's entrance.
            if (entranceView != null && !animate && !arrival) {
                // The very first notification materializes in place too —
                // fade plus a slight scale-up, no travel.
                entranceView.animate().cancel();
                entranceView.setAlpha(0f);
                entranceView.setScaleX(0.97f);
                entranceView.setScaleY(0.97f);
                rampCardElevationWithFade(entranceView);
                entranceView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f))
                        .start();
            }

            // The close button floats 9dp outside the stack's visual top
            // corner and rides along as the stack expands or collapses.
            if (closeButtonContainer != null) {
                int overhang = convertDpToPixel(9, activity);
                float visualTop = collapsed ? frameHeight - (frontHeight + headroom) : frameHeight - expandedHeight;
                float closeTy = visualTop - overhang;
                boolean isRTL = notificationStackFrame.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                closeButtonContainer.setTranslationX(isRTL ? -overhang : overhang);
                if (animate || arrival) {
                    closeButtonContainer.animate()
                            .translationY(closeTy)
                            .setDuration(350)
                            .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f))
                            .start();
                } else {
                    closeButtonContainer.animate().cancel();
                    closeButtonContainer.setTranslationY(closeTy);
                }
            }
        } catch (Exception exp) {
        }
    }
}
