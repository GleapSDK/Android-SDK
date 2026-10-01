package io.gleap;

import static io.gleap.GleapCaptureUi.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * The bar the customer uses while a capture is running: "go to the screen, then tap Capture"
 * (or Start recording) with Cancel, and while recording a red dot, the time and Stop.
 * <p>
 * It is a small panel window of the app (no overlay permission): attached to the topmost window
 * of the current activity (the activity, or the dialog / bottom sheet on top of it), moved when
 * that changes and attached again for every activity. It never takes the focus (the app keeps
 * the keyboard) and is never part of a capture. The customer can drag it up and down.
 */
final class GleapCaptureBar {
    interface Listener {
        void onCaptureTapped();

        void onStartTapped();

        void onStopTapped();

        void onCancelTapped();
    }

    static final int MODE_SCREENSHOT = 0;
    static final int MODE_RECORD_READY = 1;
    static final int MODE_RECORDING = 2;

    private static final long ANCHOR_CHECK_MS = 1000;
    private static final int ATTACH_RETRIES = 30;

    private final GleapCaptureRequest request;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int mode;
    private boolean busy;
    // How far the customer dragged the bar up, in px; kept across activities. -1: not dragged.
    private int offsetPx = -1;

    // While shown.
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private FrameLayout root;
    private View anchor;
    private View focusSource;
    private ViewTreeObserver.OnWindowFocusChangeListener focusListener;
    private TextView timer;
    private int attachAttempt;

    GleapCaptureBar(GleapCaptureRequest request, int mode, Listener listener) {
        this.request = request;
        this.mode = mode;
        this.listener = listener;
    }

    int getMode() {
        return mode;
    }

    boolean isShownOn(Activity candidate) {
        return root != null && activity.get() == candidate;
    }

    /**
     * Shows the bar on the activity (on its topmost window), moving it from where it was.
     */
    void attach(final Activity target) {
        detach();
        if (target == null || target.isFinishing()) {
            return;
        }
        activity = new WeakReference<>(target);
        View decor = target.getWindow() != null ? target.getWindow().peekDecorView() : null;
        if (decor == null || decor.getWindowToken() == null || decor.getWidth() <= 0) {
            // The activity's window is not attached yet: try again shortly.
            if (attachAttempt++ < ATTACH_RETRIES) {
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.get() == target && root == null) {
                            attach(target);
                        }
                    }
                }, 100);
            }
            return;
        }
        attachAttempt = 0;

        try {
            View top = GleapWindowCapture.topWindow(target, decor, null);
            IBinder token = top.getWindowToken();
            if (token == null) {
                top = decor;
                token = decor.getWindowToken();
            }

            root = buildViews(target, decor.getWidth());
            params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.token = token;
            params.y = offsetPx >= 0 ? offsetPx : dp(target, 12);
            params.windowAnimations = android.R.style.Animation_Toast;
            params.setTitle("Gleap capture");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Above the system bars, the display cutout and the keyboard.
                params.setFitInsetsTypes(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                        | WindowInsets.Type.displayCutout());
            }
            windowManager = target.getWindowManager();
            GleapWindowCapture.addOwnWindow(root);
            windowManager.addView(root, params);
            anchor = top;

            listenForWindowChanges(decor);
            main.postDelayed(anchorCheck, ANCHOR_CHECK_MS);
            announce(mode == MODE_RECORDING ? request.label("barRecording") : hintText());
        } catch (Throwable error) {
            // E.g. the window went away meanwhile: the bar comes back with the next activity.
            GleapLog.w("Could not show the capture bar", error);
            removeWindow();
        }
    }

    /**
     * Takes the bar off the screen (it can be attached again).
     */
    void detach() {
        main.removeCallbacksAndMessages(null);
        if (focusListener != null && focusSource != null) {
            try {
                ViewTreeObserver observer = focusSource.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnWindowFocusChangeListener(focusListener);
                }
            } catch (Throwable ignore) {
            }
        }
        focusListener = null;
        focusSource = null;
        removeWindow();
        anchor = null;
    }

    private void removeWindow() {
        if (root != null) {
            GleapWindowCapture.removeOwnWindow(root);
            if (windowManager != null) {
                try {
                    windowManager.removeViewImmediate(root);
                } catch (Throwable ignore) {
                    // Already gone with its parent window (a dismissed dialog).
                }
            }
        }
        root = null;
        timer = null;
        windowManager = null;
        params = null;
    }

    void setMode(int newMode) {
        if (mode == newMode) {
            return;
        }
        mode = newMode;
        busy = false;
        Activity current = activity.get();
        if (root != null && current != null) {
            attach(current);
        }
    }

    /**
     * Disables the buttons while the SDK works (e.g. while it starts the recording).
     */
    void setBusy(boolean isBusy) {
        busy = isBusy;
        if (root != null) {
            GleapCaptureUi.setButtonsEnabled(root, !isBusy);
        }
    }

    void setRecordedTime(long recordedMs, int maxSec) {
        if (timer == null) {
            return;
        }
        String text = GleapCaptureGeometry.formatClock(recordedMs / 1000) + " / " + GleapCaptureGeometry.formatClock(maxSec);
        timer.setText(text);
        timer.setContentDescription(request.label("barRecording") + ", " + text);
    }

    // Moves the bar to the window on top now (a dialog was shown or dismissed).
    private final Runnable anchorCheck = new Runnable() {
        @Override
        public void run() {
            try {
                Activity current = activity.get();
                if (current == null || root == null) {
                    return;
                }
                View decor = current.getWindow().peekDecorView();
                if (decor == null) {
                    return;
                }
                View top = GleapWindowCapture.topWindow(current, decor, null);
                if (top != anchor || anchor == null || anchor.getWindowToken() == null) {
                    attach(current);
                    return;
                }
            } catch (Throwable ignore) {
            }
            main.postDelayed(this, ANCHOR_CHECK_MS);
        }
    };

    private void listenForWindowChanges(View decor) {
        focusSource = decor;
        focusListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                // A dialog took or gave back the focus: look at the top window once it settled.
                main.removeCallbacks(anchorCheck);
                main.postDelayed(anchorCheck, 150);
            }
        };
        try {
            decor.getViewTreeObserver().addOnWindowFocusChangeListener(focusListener);
        } catch (Throwable ignore) {
        }
    }

    private String hintText() {
        return mode == MODE_SCREENSHOT ? request.label("barScreenshotHint") : request.label("barRecordHint");
    }

    private void announce(final String text) {
        final FrameLayout view = root;
        if (view == null || text == null || text.isEmpty()) {
            return;
        }
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    view.announceForAccessibility(text);
                } catch (Throwable ignore) {
                }
            }
        }, 300);
    }

    // ---------------------------------------------------------------------------------------------
    // Views

    private FrameLayout buildViews(Context context, int windowWidth) {
        FrameLayout container = new FrameLayout(context);
        // Room for the card's shadow.
        int shadow = dp(context, 8);
        container.setPadding(shadow, shadow, shadow, shadow);
        container.setClipToPadding(false);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GleapCaptureUi.card(context));
        card.setElevation(dp(context, 6));
        card.setPadding(dp(context, 16), dp(context, 12), dp(context, 12), dp(context, 12));
        makeDraggable(card, context);

        FrameLayout.LayoutParams cardParams;
        if (mode == MODE_RECORDING) {
            cardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            card.addView(recordingRow(context));
        } else {
            int width = Math.max(dp(context, 220), Math.min(windowWidth - 2 * dp(context, 16), dp(context, 440)));
            cardParams = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);

            TextView hint = GleapCaptureUi.text(context, hintText(), 14, GleapCaptureUi.SECONDARY_TEXT_COLOR, false);
            hint.setMaxLines(4);
            card.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonsParams.topMargin = dp(context, 10);

            TextView cancel = GleapCaptureUi.button(context, request.label("barCancel"), Color.TRANSPARENT, GleapCaptureUi.TEXT_COLOR);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!busy) {
                        listener.onCancelTapped();
                    }
                }
            });
            buttons.addView(cancel);

            final boolean screenshot = mode == MODE_SCREENSHOT;
            int primaryColor = GleapCaptureUi.primaryColor();
            TextView primary = GleapCaptureUi.button(context,
                    screenshot ? request.label("barCapture") : request.label("barStart"),
                    primaryColor, GleapCaptureUi.contrastingText(primaryColor));
            primary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (busy) {
                        return;
                    }
                    if (screenshot) {
                        listener.onCaptureTapped();
                    } else {
                        listener.onStartTapped();
                    }
                }
            });
            LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            primaryParams.setMarginStart(dp(context, 8));
            buttons.addView(primary, primaryParams);
            card.addView(buttons, buttonsParams);
        }
        cardParams.gravity = Gravity.CENTER_HORIZONTAL;
        container.addView(card, cardParams);
        if (busy) {
            GleapCaptureUi.setButtonsEnabled(container, false);
        }
        return container;
    }

    private LinearLayout recordingRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(GleapCaptureUi.STOP_COLOR);
        dot.setBackground(circle);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(context, 10), dp(context, 10));
        dotParams.setMarginEnd(dp(context, 10));
        row.addView(dot, dotParams);
        dot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                new Blink(view).run();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                view.animate().cancel();
            }
        });

        timer = GleapCaptureUi.text(context, "", 15, GleapCaptureUi.TEXT_COLOR, true);
        timer.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        row.addView(timer);
        setRecordedTime(0, request.maxDurationSec);

        TextView stop = GleapCaptureUi.button(context, request.label("barStop"), GleapCaptureUi.STOP_COLOR, GleapCaptureUi.TEXT_COLOR);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!busy) {
                    listener.onStopTapped();
                }
            }
        });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopParams.setMarginStart(dp(context, 16));
        row.addView(stop, stopParams);
        return row;
    }

    // The recording dot blinks while it is shown.
    private static final class Blink implements Runnable {
        private final WeakReference<View> view;

        Blink(View view) {
            this.view = new WeakReference<>(view);
        }

        @Override
        public void run() {
            View dot = view.get();
            if (dot == null || !dot.isAttachedToWindow()) {
                return;
            }
            dot.animate().alpha(dot.getAlpha() < 0.7f ? 1f : 0.35f).setDuration(700).withEndAction(this).start();
        }
    }

    private void makeDraggable(final View card, Context context) {
        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        card.setOnTouchListener(new View.OnTouchListener() {
            private float downY;
            private int startOffset;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (params == null || root == null || windowManager == null) {
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getRawY();
                        startOffset = params.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float delta = downY - event.getRawY();
                        if (!dragging && Math.abs(delta) > slop) {
                            dragging = true;
                        }
                        if (dragging) {
                            Activity current = activity.get();
                            View decor = current != null && current.getWindow() != null ? current.getWindow().peekDecorView() : null;
                            int max = decor != null
                                    ? Math.max(0, decor.getHeight() - root.getHeight() - dp(view.getContext(), 48))
                                    : Integer.MAX_VALUE;
                            params.y = Math.max(0, Math.min(max, Math.round(startOffset + delta)));
                            offsetPx = params.y;
                            try {
                                windowManager.updateViewLayout(root, params);
                            } catch (Throwable ignore) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            // A tap on the bar itself (not a button): nothing to do, but
                            // accessibility services see it as a click.
                            view.performClick();
                        }
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        return true;
                    default:
                        return false;
                }
            }
        });
    }
}
