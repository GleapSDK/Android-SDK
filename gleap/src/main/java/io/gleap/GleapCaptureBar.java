package io.gleap;

import static io.gleap.GleapCaptureUi.dp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.AttachedSurfaceControl;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * The bar the customer uses while a capture runs: a small dark pill with a drag grip, the main
 * action (Capture or Start recording) and a close button; while recording a pulsing red dot, the
 * time and Stop. For a few seconds a caption above it says what to do.
 * <p>
 * The pill is a small panel window of the app (no overlay permission) on the topmost window of the
 * current activity (the activity, or the dialog / bottom sheet on top of it), moved when that
 * changes and attached again for every activity. It never takes the focus (the app keeps the
 * keyboard), is never part of a capture, and touches next to it reach the app. The customer can
 * drag it anywhere in the safe area (never under the system bars or the display cutout); it keeps
 * that place for the rest of the process, also for the next capture.
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
    private static final long CAPTION_MS = 4000;
    private static final long FADE_MS = 160;
    private static final long SETTLE_MS = 180;
    // How often the window's place is corrected after the window manager laid it out elsewhere.
    private static final int MAX_CORRECTIONS = 3;
    // In dp.
    private static final float PILL_HEIGHT = 44;
    private static final float GRIP_WIDTH = 32;
    // Room around the pill in its window for the shadow; also the pill's distance to the edges of
    // the safe area.
    private static final float MARGIN = 8;
    private static final float DEFAULT_BOTTOM = 16;
    private static final float SNAP_DISTANCE = 24;
    private static final float SCREEN_MARGIN = 16;
    private static final float CAPTION_GAP = 6;
    private static final float CAPTION_MAX_WIDTH = 280;

    // Where the customer left the bar: its center as fractions of the safe area, for the rest of
    // the process (the next capture starts there). Null: the default place, bottom center.
    private static float[] rememberedCenter;

    private final GleapCaptureRequest request;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int mode;
    private boolean busy;
    private String timeText;
    private int announcedMode = -1;

    // While shown.
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private BarRoot root;
    // Capture / Start, or Stop while recording: shows the spinner while the SDK works.
    private GleapCaptureUi.Busy primary;
    // Stop gets a 48 dp touch target, also a bit beyond the pill.
    private View stop;
    private TextView timer;
    private View anchor;
    // The activity's window: the safe area is computed from it.
    private View decorView;
    private View.OnLayoutChangeListener decorListener;
    private ViewTreeObserver.OnWindowFocusChangeListener focusListener;
    private int attachAttempt;
    // The screen position params.x / params.y count from (checked once the window is laid out).
    private int originX;
    private int originY;
    private Rect safeArea = new Rect();
    // The pill's center on screen (px).
    private float centerX;
    private float centerY;
    private boolean checkPending;
    private int corrections;
    private boolean revealed;
    private boolean dragging;
    private ValueAnimator settle;

    // The caption ("Go to the screen you want to show, then tap Capture.").
    private View caption;
    private boolean captionDone;
    private long captionUntil;

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
    @SuppressLint("RtlHardcoded")
    void attach(final Activity target) {
        detach();
        if (target == null || target.isFinishing()) {
            return;
        }
        if (activity.get() != target) {
            // Another screen: it gets its own attempts.
            attachAttempt = 0;
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
            } else {
                // Given up on this window; the next attach (e.g. when the screen resumes) starts over.
                attachAttempt = 0;
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

            decorView = decor;
            safeArea = safeArea(decor);
            estimateOrigin(decor);
            root = new BarRoot(target);
            root.addView(buildPill(target), pillParams(target));
            root.setAlpha(0f);
            revealed = false;

            params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.token = token;
            params.setTitle(windowTitle());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Laid out in the safe area: never under the system bars, the cutout or the keyboard.
                params.setFitInsetsTypes(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                        | WindowInsets.Type.displayCutout());
            }
            float[] center = startCenter(target);
            int[] size = measure(root);
            place(center[0], center[1], size[0], size[1], false);

            windowManager = target.getWindowManager();
            checkPending = true;
            corrections = 0;
            root.getViewTreeObserver().addOnPreDrawListener(placeCheck);
            GleapWindowCapture.addOwnWindow(root);
            windowManager.addView(root, params);
            anchor = top;

            listenForChanges(decor);
            main.postDelayed(anchorCheck, ANCHOR_CHECK_MS);
        } catch (Throwable error) {
            // E.g. the window went away meanwhile: the bar comes back with the next activity.
            GleapLog.w("Could not show the capture bar", error);
            removeCaption(false);
            removeWindow();
        }
    }

    /**
     * Takes the bar off the screen (it can be attached again).
     */
    void detach() {
        main.removeCallbacksAndMessages(null);
        cancelSettle();
        dragging = false;
        if (decorView != null) {
            if (focusListener != null) {
                try {
                    ViewTreeObserver observer = decorView.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnWindowFocusChangeListener(focusListener);
                    }
                } catch (Throwable ignore) {
                }
            }
            if (decorListener != null) {
                decorView.removeOnLayoutChangeListener(decorListener);
            }
        }
        focusListener = null;
        decorListener = null;
        decorView = null;
        removeCaption(false);
        removeWindow();
        anchor = null;
    }

    private void removeWindow() {
        if (root != null) {
            GleapWindowCapture.removeOwnWindow(root);
            try {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(placeCheck);
                }
            } catch (Throwable ignore) {
            }
            if (windowManager != null) {
                try {
                    windowManager.removeViewImmediate(root);
                } catch (Throwable ignore) {
                    // Already gone with its parent window (a dismissed dialog).
                }
            }
        }
        root = null;
        primary = null;
        stop = null;
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
        // The new step gets its own caption.
        captionDone = false;
        captionUntil = 0;
        if (root != null && activity.get() != null) {
            rebuild();
        }
    }

    /**
     * While the SDK works (taking the screenshot, finishing the recording) the main button shows a
     * spinner and ignores taps; nothing else moves.
     */
    void setBusy(boolean isBusy) {
        busy = isBusy;
        if (primary != null) {
            primary.setBusy(isBusy);
        }
        if (isBusy) {
            dismissCaption();
        }
    }

    void setRecordedTime(long recordedMs, int maxSec) {
        timeText = GleapCaptureGeometry.formatClock(recordedMs / 1000) + " / " + GleapCaptureGeometry.formatClock(maxSec);
        if (timer != null) {
            timer.setText(timeText);
            timer.setContentDescription(request.label("barRecording") + ", " + timeText);
        }
    }

    // The content changed (another step): the pill keeps its center.
    private void rebuild() {
        try {
            Context context = root.getContext();
            removeCaption(false);
            root.removeAllViews();
            root.addView(buildPill(context), pillParams(context));
            int[] size = measure(root);
            params.setTitle(windowTitle());
            place(centerX, centerY, size[0], size[1], true);
            checkPending = true;
            corrections = 0;
            if (revealed) {
                showCaption();
                announce();
            }
        } catch (Throwable error) {
            GleapLog.w("Could not update the capture bar", error);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Place

    /**
     * The safe area on screen (px): the activity's window without the system bars, the display
     * cutout and the keyboard.
     */
    private static Rect safeArea(View decor) {
        int[] location = new int[2];
        decor.getLocationOnScreen(location);
        Rect area = new Rect(location[0], location[1], location[0] + decor.getWidth(), location[1] + decor.getHeight());
        int[] insets = insets(decor);
        Rect safe = new Rect(area.left + insets[0], area.top + insets[1], area.right - insets[2], area.bottom - insets[3]);
        return safe.width() > 0 && safe.height() > 0 ? safe : area;
    }

    @SuppressLint({"DiscouragedApi", "InternalInsetResource"})
    private static int[] insets(View decor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsets windowInsets = decor.getRootWindowInsets();
                if (windowInsets != null) {
                    Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                    return new int[]{insets.left, insets.top, insets.right, insets.bottom};
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                WindowInsets windowInsets = decor.getRootWindowInsets();
                if (windowInsets != null) {
                    int[] result = new int[]{windowInsets.getSystemWindowInsetLeft(), windowInsets.getSystemWindowInsetTop(),
                            windowInsets.getSystemWindowInsetRight(), windowInsets.getSystemWindowInsetBottom()};
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && windowInsets.getDisplayCutout() != null) {
                        DisplayCutout cutout = windowInsets.getDisplayCutout();
                        result[0] = Math.max(result[0], cutout.getSafeInsetLeft());
                        result[1] = Math.max(result[1], cutout.getSafeInsetTop());
                        result[2] = Math.max(result[2], cutout.getSafeInsetRight());
                        result[3] = Math.max(result[3], cutout.getSafeInsetBottom());
                    }
                    return result;
                }
            }
            // Before API 23: the status bar (the window manager keeps the bar off the navigation bar).
            Resources resources = decor.getResources();
            int id = resources.getIdentifier("status_bar_height", "dimen", "android");
            int top = id > 0 ? resources.getDimensionPixelSize(id) : dp(decor.getContext(), 24);
            return new int[]{0, top, 0, 0};
        } catch (Throwable error) {
            return new int[4];
        }
    }

    // Where params.x / params.y count from: the safe area since API 30 (the window manager lays
    // the bar out there), the activity's window before. Checked once the window is laid out.
    private void estimateOrigin(View decor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            originX = safeArea.left;
            originY = safeArea.top;
        } else {
            int[] location = new int[2];
            decor.getLocationOnScreen(location);
            originX = location[0];
            originY = location[1];
        }
    }

    // Where the pill's center goes: where the customer left it, else bottom center, 16 dp above
    // the navigation bar.
    private float[] startCenter(Context context) {
        float[] remembered = rememberedCenter;
        if (remembered != null) {
            return new float[]{safeArea.left + remembered[0] * safeArea.width(), safeArea.top + remembered[1] * safeArea.height()};
        }
        return new float[]{safeArea.exactCenterX(), safeArea.bottom - dp(context, DEFAULT_BOTTOM) - dp(context, PILL_HEIGHT) / 2f};
    }

    private int[] measure(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, safeArea.width()), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(Math.max(0, safeArea.height()), View.MeasureSpec.AT_MOST));
        return new int[]{view.getMeasuredWidth(), view.getMeasuredHeight()};
    }

    /**
     * Puts the pill's center at (x, y) on screen, kept in the safe area.
     */
    private void place(float x, float y, int width, int height, boolean update) {
        if (params == null) {
            return;
        }
        int left = clamp(Math.round(x - width / 2f), safeArea.left, safeArea.right - width);
        int top = clamp(Math.round(y - height / 2f), safeArea.top, safeArea.bottom - height);
        centerX = left + width / 2f;
        centerY = top + height / 2f;
        params.x = left - originX;
        params.y = top - originY;
        if (update && root != null && windowManager != null) {
            try {
                windowManager.updateViewLayout(root, params);
            } catch (Throwable ignore) {
            }
        }
    }

    private void moveTo(float x, float y) {
        if (root != null) {
            place(x, y, root.getWidth(), root.getHeight(), true);
        }
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min + (max - min) / 2;
        }
        return Math.max(min, Math.min(max, value));
    }

    // Once laid out: the window is where it should be (else the origin is corrected), then it
    // fades in.
    private final ViewTreeObserver.OnPreDrawListener placeCheck = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            try {
                checkPlace();
            } catch (Throwable error) {
                GleapLog.w("Could not place the capture bar", error);
                reveal();
            }
            return true;
        }
    };

    private void checkPlace() {
        if (root == null || params == null || !checkPending || dragging) {
            return;
        }
        int width = root.getWidth();
        int height = root.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        int left = params.x + originX;
        int top = params.y + originY;
        boolean moved = location[0] != left || location[1] != top;
        boolean resized = Math.abs(left + width / 2f - centerX) > 1 || Math.abs(top + height / 2f - centerY) > 1;
        if ((moved || resized) && corrections < MAX_CORRECTIONS) {
            corrections++;
            if (moved) {
                originX = location[0] - params.x;
                originY = location[1] - params.y;
            }
            place(centerX, centerY, width, height, true);
            return;
        }
        checkPending = false;
        corrections = 0;
        reveal();
    }

    private void reveal() {
        if (revealed || root == null) {
            return;
        }
        revealed = true;
        root.setTranslationY(dp(root.getContext(), 6));
        root.animate().alpha(1f).translationY(0f).setDuration(FADE_MS).setInterpolator(new DecelerateInterpolator()).start();
        showCaption();
        announce();
    }

    // The customer let go: the bar snaps to the horizontal center when near it and keeps its place.
    private void settle() {
        if (root == null) {
            return;
        }
        final float fromX = centerX;
        final float y = centerY;
        float target = fromX;
        if (Math.abs(fromX - safeArea.exactCenterX()) <= dp(root.getContext(), SNAP_DISTANCE)) {
            target = safeArea.exactCenterX();
        }
        remember(target, y);
        if (Math.abs(target - fromX) < 1) {
            return;
        }
        final float toX = target;
        cancelSettle();
        settle = ValueAnimator.ofFloat(0f, 1f);
        settle.setDuration(SETTLE_MS);
        settle.setInterpolator(new DecelerateInterpolator());
        settle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = (float) animation.getAnimatedValue();
                moveTo(fromX + (toX - fromX) * fraction, y);
            }
        });
        settle.start();
    }

    private void cancelSettle() {
        if (settle != null) {
            settle.cancel();
            settle = null;
        }
    }

    private void remember(float x, float y) {
        if (safeArea.width() <= 0 || safeArea.height() <= 0) {
            return;
        }
        rememberedCenter = new float[]{
                Math.max(0f, Math.min(1f, (x - safeArea.left) / safeArea.width())),
                Math.max(0f, Math.min(1f, (y - safeArea.top) / safeArea.height()))};
    }

    // The safe area changed (rotation, a resized window, the keyboard): the bar goes to its place
    // in the new one.
    private void checkSafeArea() {
        if (root == null || decorView == null || dragging) {
            return;
        }
        Rect area = safeArea(decorView);
        if (area.equals(safeArea)) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Placed and checked again from scratch.
            Activity current = activity.get();
            if (current != null) {
                attach(current);
            }
            return;
        }
        cancelSettle();
        safeArea = area;
        estimateOrigin(decorView);
        removeCaption(false);
        float[] center = startCenter(root.getContext());
        int[] size = measure(root);
        place(center[0], center[1], size[0], size[1], true);
        checkPending = true;
        corrections = 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Changes of the app's windows

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
                checkSafeArea();
            } catch (Throwable ignore) {
            }
            main.postDelayed(this, ANCHOR_CHECK_MS);
        }
    };

    private final Runnable safeAreaCheck = new Runnable() {
        @Override
        public void run() {
            try {
                checkSafeArea();
            } catch (Throwable ignore) {
            }
        }
    };

    private void listenForChanges(View decor) {
        focusListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                // A dialog took or gave back the focus: look at the top window once it settled.
                main.removeCallbacks(anchorCheck);
                main.postDelayed(anchorCheck, 150);
            }
        };
        decorListener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                main.removeCallbacks(safeAreaCheck);
                main.post(safeAreaCheck);
            }
        };
        try {
            decor.getViewTreeObserver().addOnWindowFocusChangeListener(focusListener);
            decor.addOnLayoutChangeListener(decorListener);
        } catch (Throwable ignore) {
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The caption

    // The windows' titles are what accessibility services may say: the labels.
    private String windowTitle() {
        String title = mode == MODE_RECORDING ? request.label("barRecording") : hintText();
        return title != null && !title.isEmpty() ? title : request.label("barCapture");
    }

    private String hintText() {
        if (mode == MODE_SCREENSHOT) {
            return request.label("barScreenshotHint");
        }
        if (mode == MODE_RECORD_READY) {
            return request.label("barRecordHint");
        }
        return "";
    }

    // Above the pill (below it when there is no room above) for about 4 s, or until the first
    // drag or tap. It takes no touches.
    @SuppressLint("RtlHardcoded")
    private void showCaption() {
        if (mode == MODE_RECORDING || captionDone || caption != null || root == null || params == null
                || windowManager == null || busy) {
            return;
        }
        String text = hintText();
        if (text == null || text.isEmpty()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (captionUntil == 0) {
            captionUntil = now + CAPTION_MS;
        }
        long remaining = captionUntil - now;
        if (remaining < 300) {
            captionDone = true;
            return;
        }
        try {
            Context context = root.getContext();
            FrameLayout window = new FrameLayout(context);
            int margin = dp(context, MARGIN);
            window.setPadding(margin, margin, margin, margin);
            window.setClipToPadding(false);
            // Announced when the bar appears.
            window.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            TextView bubble = GleapCaptureUi.text(context, text, 13, GleapCaptureUi.CAPTION_TEXT_COLOR, false);
            bubble.setMaxLines(2);
            bubble.setEllipsize(TextUtils.TruncateAt.END);
            bubble.setMaxWidth(Math.min(dp(context, CAPTION_MAX_WIDTH), Math.max(0, safeArea.width() - 2 * margin)));
            bubble.setGravity(Gravity.CENTER);
            bubble.setLineSpacing(dp(context, 2), 1f);
            bubble.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            bubble.setBackground(GleapCaptureUi.pill(context, 12));
            bubble.setElevation(dp(context, 4));
            window.addView(bubble, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int[] size = measure(window);
            float pillHalf = dp(context, PILL_HEIGHT) / 2f;
            int gap = dp(context, CAPTION_GAP);
            int top = Math.round(centerY - pillHalf - gap + margin - size[1]);
            if (top < safeArea.top) {
                top = Math.round(centerY + pillHalf + gap - margin);
            }
            int left = clamp(Math.round(centerX - size[0] / 2f), safeArea.left, safeArea.right - size[0]);
            top = clamp(top, safeArea.top, safeArea.bottom - size[1]);

            WindowManager.LayoutParams captionParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                    params.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            captionParams.gravity = Gravity.TOP | Gravity.LEFT;
            captionParams.token = params.token;
            captionParams.x = left - originX;
            captionParams.y = top - originY;
            captionParams.setTitle(text);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captionParams.setFitInsetsTypes(params.getFitInsetsTypes());
            }
            window.setAlpha(0f);
            GleapWindowCapture.addOwnWindow(window);
            windowManager.addView(window, captionParams);
            caption = window;
            window.animate().alpha(1f).setDuration(FADE_MS).start();
            main.postDelayed(captionTimeout, remaining);
        } catch (Throwable error) {
            GleapLog.w("Could not show the capture hint", error);
            captionDone = true;
        }
    }

    private final Runnable captionTimeout = new Runnable() {
        @Override
        public void run() {
            dismissCaption();
        }
    };

    // Fades the caption out for good (this step).
    private void dismissCaption() {
        captionDone = true;
        removeCaption(true);
    }

    private void removeCaption(boolean fade) {
        main.removeCallbacks(captionTimeout);
        final View view = caption;
        final WindowManager manager = windowManager;
        caption = null;
        if (view == null) {
            return;
        }
        if (fade && GleapCaptureUi.animationsEnabled()) {
            view.animate().cancel();
            view.animate().alpha(0f).setDuration(FADE_MS).withEndAction(new Runnable() {
                @Override
                public void run() {
                    removeCaptionView(view, manager);
                }
            }).start();
        } else {
            removeCaptionView(view, manager);
        }
    }

    private static void removeCaptionView(View view, WindowManager manager) {
        GleapWindowCapture.removeOwnWindow(view);
        if (manager != null) {
            try {
                manager.removeViewImmediate(view);
            } catch (Throwable ignore) {
            }
        }
    }

    private void announce() {
        if (announcedMode == mode || root == null) {
            return;
        }
        announcedMode = mode;
        final String text = mode == MODE_RECORDING ? request.label("barRecording") : hintText();
        final View view = root;
        if (text == null || text.isEmpty()) {
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

    private FrameLayout.LayoutParams pillParams(Context context) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, PILL_HEIGHT));
    }

    private LinearLayout buildPill(Context context) {
        primary = null;
        stop = null;
        timer = null;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int maxWidth = Math.min(screenWidth - 2 * dp(context, SCREEN_MARGIN), safeArea.width() - 2 * dp(context, MARGIN));
        PillLayout pill = new PillLayout(context, Math.max(dp(context, 120), maxWidth));
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(GleapCaptureUi.pill(context, GleapCaptureUi.PILL_RADIUS_DP));
        pill.setElevation(dp(context, 6));
        pill.setOnTouchListener(new Drag(context));

        View grip = new GleapCaptureUi.Grip(context);
        grip.setContentDescription(request.label("barDragHint"));
        grip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        pill.addView(grip, new LinearLayout.LayoutParams(dp(context, GRIP_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

        int pillHeight = dp(context, PILL_HEIGHT);
        if (mode == MODE_RECORDING) {
            View dot = recordingDot(context);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(context, 10), dp(context, 10));
            dotParams.setMarginEnd(dp(context, 8));
            pill.addView(dot, dotParams);

            timer = GleapCaptureUi.text(context, "", 15, GleapCaptureUi.TIMER_COLOR, true);
            timer.setSingleLine(true);
            timer.setFontFeatureSettings("tnum");
            if (timeText == null) {
                timeText = GleapCaptureGeometry.formatClock(0) + " / " + GleapCaptureGeometry.formatClock(request.maxDurationSec);
            }
            setRecordedTimeText();
            // A fixed width (tabular digits): the time changes without laying the window out again.
            int timerWidth = (int) Math.ceil(timer.getPaint().measureText(timeText)) + 1;
            pill.addView(timer, new LinearLayout.LayoutParams(timerWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Icon only: a red circle; its label is what accessibility services say.
            int stopWidth = dp(context, 48);
            GleapCaptureUi.CircleButton stopButton = GleapCaptureUi.stopButton(context, request.label("barStop"), stopWidth, pillHeight);
            stopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!busy) {
                        listener.onStopTapped();
                    }
                }
            });
            LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(stopWidth, pillHeight);
            stopParams.setMarginStart(dp(context, 6));
            pill.addView(stopButton, stopParams);
            primary = stopButton;
            stop = stopButton;
        } else {
            final boolean screenshot = mode == MODE_SCREENSHOT;
            GleapCaptureUi.Button action = GleapCaptureUi.button(context,
                    screenshot ? request.label("barCapture") : request.label("barStart"),
                    screenshot ? GleapCaptureUi.Icon.CAMERA : GleapCaptureUi.Icon.RECORD, false,
                    GleapCaptureUi.primaryFill(), GleapCaptureUi.BUTTON_RADIUS_DP);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (busy) {
                        return;
                    }
                    dismissCaption();
                    if (screenshot) {
                        listener.onCaptureTapped();
                    } else {
                        listener.onStartTapped();
                    }
                }
            });
            // Takes what is left on a narrow screen (its label is cut).
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(context, GleapCaptureUi.BUTTON_HEIGHT_DP));
            actionParams.weight = 1f;
            pill.addView(action, actionParams);
            primary = action;

            // A 36 dp circle in a 44 dp square: 4 dp to the pill's edge and to the main button.
            GleapCaptureUi.CircleButton close = GleapCaptureUi.iconButton(context, GleapCaptureUi.Icon.CLOSE,
                    request.label("barCancel"), pillHeight, pillHeight);
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Also while a screenshot is taken: it is dropped.
                    listener.onCancelTapped();
                }
            });
            pill.addView(close, new LinearLayout.LayoutParams(pillHeight, pillHeight));
        }
        if (busy && primary != null) {
            primary.setBusy(true);
        }
        return pill;
    }

    private void setRecordedTimeText() {
        if (timer != null && timeText != null) {
            timer.setText(timeText);
            timer.setContentDescription(request.label("barRecording") + ", " + timeText);
        }
    }

    private static View recordingDot(Context context) {
        View dot = new View(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(GleapCaptureUi.RECORDING_DOT_COLOR);
        dot.setBackground(circle);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                if (GleapCaptureUi.animationsEnabled()) {
                    new Pulse(view).run();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                view.animate().cancel();
            }
        });
        return dot;
    }

    // The recording dot pulses while it is shown.
    private static final class Pulse implements Runnable {
        private final WeakReference<View> view;

        Pulse(View view) {
            this.view = new WeakReference<>(view);
        }

        @Override
        public void run() {
            View dot = view.get();
            if (dot == null || !dot.isAttachedToWindow()) {
                return;
            }
            dot.animate().alpha(dot.getAlpha() < 0.7f ? 1f : 0.4f).setDuration(700)
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                    .withEndAction(this).start();
        }
    }

    /**
     * The window's content: the pill with room for its shadow. A touch on it ends the caption;
     * touches next to the pill reach the app (API 34+, where the window can say so).
     */
    private final class BarRoot extends FrameLayout {
        private final Rect touchable = new Rect();
        private final Rect stopTarget = new Rect();
        private final Region region = new Region();
        private final Rect regionBounds = new Rect();

        BarRoot(Context context) {
            super(context);
            int margin = dp(context, MARGIN);
            setPadding(margin, margin, margin, margin);
            setClipToPadding(false);
            setClipChildren(false);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dismissCaption();
            }
            return false;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (getChildCount() == 0) {
                return;
            }
            View pill = getChildAt(0);
            region.set(pill.getLeft(), pill.getTop(), pill.getRight(), pill.getBottom());
            View stopButton = stop;
            if (stopButton != null && stopButton.getParent() == pill) {
                // Stop's touch target: 48 dp high, also over the pill's edge.
                int half = dp(getContext(), 24);
                int x = pill.getLeft() + (stopButton.getLeft() + stopButton.getRight()) / 2;
                int y = pill.getTop() + (stopButton.getTop() + stopButton.getBottom()) / 2;
                int halfWidth = Math.max(half, stopButton.getWidth() / 2);
                if (stopTarget.left != x - halfWidth || stopTarget.top != y - half || getTouchDelegate() == null) {
                    stopTarget.set(x - halfWidth, y - half, x + halfWidth, y + half);
                    setTouchDelegate(new TouchDelegate(new Rect(stopTarget), stopButton));
                }
                region.op(stopTarget, Region.Op.UNION);
            } else if (getTouchDelegate() != null) {
                stopTarget.setEmpty();
                setTouchDelegate(null);
            }
            region.getBounds(regionBounds);
            if (Build.VERSION.SDK_INT >= 34 && !regionBounds.equals(touchable)) {
                touchable.set(regionBounds);
                try {
                    AttachedSurfaceControl surface = getRootSurfaceControl();
                    if (surface != null) {
                        surface.setTouchableRegion(region);
                    }
                } catch (Throwable ignore) {
                }
            }
        }
    }

    // A row that is never wider than the screen allows.
    private static final class PillLayout extends LinearLayout {
        private final int maxWidth;

        PillLayout(Context context, int maxWidth) {
            super(context);
            this.maxWidth = maxWidth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = MeasureSpec.getSize(widthMeasureSpec);
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || size > maxWidth) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        // A tap on the bar itself (see Drag): nothing to do.
        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    // Drags the bar from the grip or any part of the pill that is not a button; it follows the
    // finger and stays in the safe area.
    private final class Drag implements View.OnTouchListener {
        private final int slop;
        private float downX;
        private float downY;
        private float startX;
        private float startY;

        Drag(Context context) {
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (params == null || root == null || windowManager == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!revealed) {
                        return false;
                    }
                    cancelSettle();
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = centerX;
                    startY = centerY;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && dx * dx + dy * dy > slop * slop) {
                        dragging = true;
                        checkPending = false;
                        dismissCaption();
                    }
                    if (dragging) {
                        moveTo(startX + dx, startY + dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        dragging = false;
                        settle();
                    } else {
                        // A tap on the bar itself: nothing to do, but accessibility services see a click.
                        view.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        dragging = false;
                        settle();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
