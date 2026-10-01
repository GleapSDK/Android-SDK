package io.gleap;

import static io.gleap.GleapCaptureUi.dp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;

/**
 * Shows the finished recording before it is sent: full screen on a dark backdrop, the video
 * (looping, tap to pause or play, a thin scrubber) with rounded corners, × to cancel, Retake and
 * Send, and the upload progress on Send. A dialog on the current activity; it is shown again on
 * the next activity after a rotation or an activity change.
 */
final class GleapCapturePreview {
    interface Listener {
        void onSendTapped();

        void onRetakeTapped();

        void onCancelTapped();
    }

    private static final long POSITION_TICK_MS = 100;
    private static final int SHOW_RETRIES = 30;

    private final GleapCaptureRequest request;
    private final File video;
    private final int videoWidth;
    private final int videoHeight;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private WeakReference<Activity> activity = new WeakReference<>(null);
    private int showAttempt;
    private Dialog dialog;
    private VideoView videoView;
    private VideoFrame frame;
    private TextView status;
    private View close;
    private GleapCaptureUi.Button retake;
    private GleapCaptureUi.Button send;
    // What the preview shows.
    private boolean uploading;
    private float progress;
    private String error;

    GleapCapturePreview(GleapCaptureRequest request, File video, int videoWidth, int videoHeight, Listener listener) {
        this.request = request;
        this.video = video;
        this.videoWidth = Math.max(1, videoWidth);
        this.videoHeight = Math.max(1, videoHeight);
        this.listener = listener;
    }

    boolean isShownOn(Activity candidate) {
        return dialog != null && activity.get() == candidate;
    }

    void show(final Activity target) {
        dismiss();
        if (target == null || target.isFinishing()) {
            return;
        }
        if (activity.get() != target) {
            showAttempt = 0;
        }
        activity = new WeakReference<>(target);
        View decor = target.getWindow() != null ? target.getWindow().peekDecorView() : null;
        if (decor == null || decor.getWindowToken() == null) {
            // The activity's window is not attached yet (e.g. right after a rotation): shortly.
            if (showAttempt++ < SHOW_RETRIES) {
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.get() == target && dialog == null) {
                            show(target);
                        }
                    }
                }, 100);
            } else {
                showAttempt = 0;
            }
            return;
        }
        showAttempt = 0;
        try {
            // Full screen and dark, whatever the app's theme.
            dialog = new Dialog(target, android.R.style.Theme_Material_NoActionBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setTitle(request.label("previewTitle"));
            dialog.setContentView(buildViews(dialog.getContext()));
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(GleapCaptureUi.BACKDROP_COLOR));
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setWindowAnimations(android.R.style.Animation_Toast);
                if (Build.VERSION.SDK_INT < 35) {
                    // Since API 35 the window draws behind the system bars itself.
                    window.setStatusBarColor(GleapCaptureUi.BACKDROP_COLOR);
                    window.setNavigationBarColor(GleapCaptureUi.BACKDROP_COLOR);
                }
            }
            dialog.show();
            if (window != null) {
                GleapWindowCapture.addOwnWindow(window.getDecorView());
            }
            applyState();
            startVideo();
        } catch (Throwable error) {
            GleapLog.w("Could not show the recording preview", error);
            dismiss();
        }
    }

    void dismiss() {
        main.removeCallbacksAndMessages(null);
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (Throwable ignore) {
            }
        }
        if (dialog != null) {
            try {
                Window window = dialog.getWindow();
                if (window != null) {
                    GleapWindowCapture.removeOwnWindow(window.peekDecorView());
                }
                dialog.dismiss();
            } catch (Throwable ignore) {
                // The activity is gone already.
            }
        }
        dialog = null;
        videoView = null;
        frame = null;
        status = null;
        close = null;
        retake = null;
        send = null;
    }

    void setUploading(float value) {
        uploading = true;
        progress = value;
        error = null;
        applyState();
    }

    void setError(String message) {
        uploading = false;
        error = message;
        applyState();
    }

    // While uploading Send shows a spinner and the percentage and everything else waits; an error
    // shows above the buttons and Send tries again.
    private void applyState() {
        if (send == null) {
            return;
        }
        if (uploading) {
            String percent = NumberFormat.getPercentInstance().format(Math.max(0f, Math.min(1f, progress)));
            send.setBusy(true);
            send.setText(percent);
            send.setContentDescription(request.label("uploading") + " " + percent);
        } else {
            send.setBusy(false);
            send.setText(request.label("previewSend"));
            send.setContentDescription(null);
        }
        GleapCaptureUi.setEnabled(retake, !uploading);
        GleapCaptureUi.setEnabled(close, !uploading);
        if (frame != null) {
            frame.setEnabled(!uploading);
        }
        if (status != null) {
            if (!uploading && error != null) {
                status.setText(error);
                status.setVisibility(View.VISIBLE);
            } else {
                status.setVisibility(View.GONE);
            }
        }
    }

    private void startVideo() {
        final VideoView view = videoView;
        if (view == null) {
            return;
        }
        view.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer player) {
                try {
                    player.setLooping(true);
                    player.setVolume(0f, 0f);
                } catch (Throwable ignore) {
                }
                if (frame == null || !frame.pausedByCustomer) {
                    view.start();
                }
                trackPosition();
            }
        });
        view.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer player, int what, int extra) {
                // The recording can still be sent without a preview.
                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The preview is silent: it never pauses the music the customer listens to.
            view.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
        }
        view.setVideoPath(video.getAbsolutePath());
    }

    // The scrubber follows the video while the preview is shown.
    private void trackPosition() {
        main.removeCallbacks(positionTick);
        main.post(positionTick);
    }

    private final Runnable positionTick = new Runnable() {
        @Override
        public void run() {
            VideoView view = videoView;
            VideoFrame target = frame;
            if (view == null || target == null) {
                return;
            }
            try {
                int duration = view.getDuration();
                if (duration > 0 && !target.scrubbing) {
                    target.setPosition(view.getCurrentPosition() / (float) duration, !view.isPlaying());
                }
            } catch (Throwable ignore) {
            }
            main.postDelayed(this, POSITION_TICK_MS);
        }
    };

    private void togglePlayback() {
        VideoView view = videoView;
        if (view == null || frame == null) {
            return;
        }
        try {
            if (view.isPlaying()) {
                view.pause();
                frame.pausedByCustomer = true;
            } else {
                view.start();
                frame.pausedByCustomer = false;
            }
            frame.setPosition(frame.position, !view.isPlaying());
        } catch (Throwable ignore) {
        }
    }

    private void seekTo(float fraction) {
        VideoView view = videoView;
        if (view == null) {
            return;
        }
        try {
            int duration = view.getDuration();
            if (duration > 0) {
                view.seekTo(Math.round(fraction * duration));
            }
        } catch (Throwable ignore) {
        }
    }

    private View buildViews(Context context) {
        FrameLayout root = new FrameLayout(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            root.setAccessibilityPaneTitle(request.label("previewTitle"));
        }
        // Drawn behind the system bars (API 35+): the content stays in the safe area.
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int[] safe = safeInsets(insets);
                view.setPadding(safe[0], safe[1], safe[2], safe[3]);
                return insets;
            }
        });

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top: the title as a small centered caption and × (cancel) at the end, clear of each other.
        FrameLayout top = new FrameLayout(context);
        column.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 60)));
        int closeSize = dp(context, 44);
        close = GleapCaptureUi.iconButton(context, GleapCaptureUi.Icon.CLOSE, request.label("barCancel"), closeSize, closeSize);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!uploading) {
                    listener.onCancelTapped();
                }
            }
        });
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(closeSize, closeSize, Gravity.END | Gravity.CENTER_VERTICAL);
        closeParams.setMarginEnd(dp(context, 8));
        top.addView(close, closeParams);

        TextView title = GleapCaptureUi.text(context, request.label("previewTitle"), 13, GleapCaptureUi.MUTED_TEXT_COLOR, false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        // The window's title says it.
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        titleParams.leftMargin = dp(context, 60);
        titleParams.rightMargin = dp(context, 60);
        top.addView(title, titleParams);

        // The video, centered, rounded, with a hairline.
        frame = new VideoFrame(context, videoWidth, videoHeight);
        videoView = new VideoView(context);
        videoView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(videoView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.setContentDescription(request.label("previewPause"));
        frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!uploading) {
                    togglePlayback();
                }
            }
        });
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        frameParams.setMargins(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 8));
        column.addView(frame, frameParams);

        status = GleapCaptureUi.text(context, "", 14, GleapCaptureUi.ERROR_TEXT_COLOR, false);
        status.setGravity(Gravity.CENTER);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(dp(context, 24), 0, dp(context, 24), dp(context, 12));
        column.addView(status, statusParams);

        // Retake and Send: a full-width row on phones, right-aligned on tablets.
        boolean tablet = context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(tablet ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        int height = dp(context, 48);
        retake = GleapCaptureUi.secondaryButton(context, request.label("previewRetake"), 24);
        retake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!uploading) {
                    listener.onRetakeTapped();
                }
            }
        });
        // "Send ➤": the icon after the label (before it right to left).
        send = GleapCaptureUi.button(context, request.label("previewSend"), GleapCaptureUi.Icon.SEND, true,
                GleapCaptureUi.primaryFill(), 24);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!uploading) {
                    listener.onSendTapped();
                }
            }
        });
        LinearLayout.LayoutParams retakeParams;
        LinearLayout.LayoutParams sendParams;
        if (tablet) {
            retake.setMinimumWidth(dp(context, 140));
            send.setMinimumWidth(dp(context, 140));
            retakeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            sendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
        } else {
            retakeParams = new LinearLayout.LayoutParams(0, height, 1f);
            sendParams = new LinearLayout.LayoutParams(0, height, 1f);
        }
        sendParams.setMarginStart(dp(context, 12));
        buttons.addView(retake, retakeParams);
        buttons.addView(send, sendParams);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.setMargins(dp(context, 16), 0, dp(context, 16), dp(context, 16));
        column.addView(buttons, buttonsParams);
        return root;
    }

    private static int[] safeInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[]{safe.left, safe.top, safe.right, safe.bottom};
        }
        int[] result = new int[]{insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
            DisplayCutout cutout = insets.getDisplayCutout();
            result[0] = Math.max(result[0], cutout.getSafeInsetLeft());
            result[1] = Math.max(result[1], cutout.getSafeInsetTop());
            result[2] = Math.max(result[2], cutout.getSafeInsetRight());
            result[3] = Math.max(result[3], cutout.getSafeInsetBottom());
        }
        return result;
    }

    /**
     * Lays the video out as large as fits, keeping its aspect ratio, with rounded corners and a
     * hairline, the play glyph while paused and a thin scrubber under it. A tap pauses or plays.
     */
    private final class VideoFrame extends FrameLayout {
        private final int sourceWidth;
        private final int sourceHeight;
        private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GleapCaptureUi.Icon playIcon = new GleapCaptureUi.Icon(GleapCaptureUi.Icon.PLAY, GleapCaptureUi.TEXT_COLOR);
        private final Path corners = new Path();
        private final RectF video = new RectF();
        private final float radius;
        private final int scrubberHeight;
        private final int slop;
        boolean pausedByCustomer;
        boolean scrubbing;
        float position;
        private boolean paused;
        private float downX;
        private float downY;
        private boolean moved;

        VideoFrame(Context context, int sourceWidth, int sourceHeight) {
            super(context);
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            setWillNotDraw(false);
            setClickable(true);
            radius = dp(context, 12);
            scrubberHeight = dp(context, 28);
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            mask.setColor(GleapCaptureUi.BACKDROP_COLOR);
            hairline.setColor(GleapCaptureUi.HAIRLINE_COLOR);
            hairline.setStyle(Paint.Style.STROKE);
            hairline.setStrokeWidth(Math.max(1, dp(context, 1)));
            glyphBackground.setColor(0x73000000);
            track.setColor(0x33FFFFFF);
            track.setStrokeWidth(dp(context, 3));
            track.setStrokeCap(Paint.Cap.ROUND);
            played.setColor(GleapCaptureUi.TEXT_COLOR);
            played.setStrokeWidth(dp(context, 3));
            played.setStrokeCap(Paint.Cap.ROUND);
        }

        void setPosition(float value, boolean isPaused) {
            if (isPaused != paused) {
                // What a tap does now.
                setContentDescription(request.label(isPaused ? "previewPlay" : "previewPause"));
            }
            if (Math.abs(value - position) > 0.001f || isPaused != paused) {
                position = Math.max(0f, Math.min(1f, value));
                paused = isPaused;
                invalidate();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int availableHeight = Math.max(1, height - scrubberHeight);
            float scale = Math.min(width / (float) sourceWidth, availableHeight / (float) sourceHeight);
            int videoWidthPx = Math.max(1, Math.round(sourceWidth * scale));
            int videoHeightPx = Math.max(1, Math.round(sourceHeight * scale));
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(videoWidthPx, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(videoHeightPx, MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int childLeft = (width - childWidth) / 2;
                int childTop = Math.max(0, (height - scrubberHeight - childHeight) / 2);
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                video.set(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            }
            corners.reset();
            corners.setFillType(Path.FillType.EVEN_ODD);
            corners.addRect(video, Path.Direction.CW);
            corners.addRoundRect(video, radius, radius, Path.Direction.CW);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (video.isEmpty()) {
                return;
            }
            // Rounded corners: the backdrop over the video's corners, then the hairline.
            canvas.drawPath(corners, mask);
            float inset = hairline.getStrokeWidth() / 2;
            canvas.drawRoundRect(video.left + inset, video.top + inset, video.right - inset, video.bottom - inset,
                    radius, radius, hairline);
            if (paused) {
                float glyph = dp(getContext(), 56);
                canvas.drawCircle(video.centerX(), video.centerY(), glyph / 2, glyphBackground);
                int icon = dp(getContext(), 26);
                playIcon.setBounds(Math.round(video.centerX() - icon / 2f), Math.round(video.centerY() - icon / 2f),
                        Math.round(video.centerX() + icon / 2f), Math.round(video.centerY() + icon / 2f));
                playIcon.draw(canvas);
            }
            // The scrubber: a thin line under the video.
            float y = video.bottom + scrubberHeight / 2f;
            float start = video.left + radius / 2;
            float end = video.right - radius / 2;
            canvas.drawLine(start, y, end, y, track);
            canvas.drawLine(start, y, start + (end - start) * position, y, played);
            if (scrubbing) {
                canvas.drawCircle(start + (end - start) * position, y, dp(getContext(), 6), played);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) {
                return false;
            }
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    moved = false;
                    scrubbing = y >= video.bottom && y <= video.bottom + scrubberHeight
                            && x >= video.left - scrubberHeight && x <= video.right + scrubberHeight;
                    if (scrubbing) {
                        scrubTo(x);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                        moved = true;
                    }
                    if (scrubbing) {
                        scrubTo(x);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (scrubbing) {
                        scrubbing = false;
                        invalidate();
                    } else if (!moved && video.contains(x, y)) {
                        performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    scrubbing = false;
                    invalidate();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        private void scrubTo(float x) {
            float start = video.left + radius / 2;
            float end = video.right - radius / 2;
            float fraction = end > start ? (x - start) / (end - start) : 0f;
            position = Math.max(0f, Math.min(1f, fraction));
            seekTo(position);
            invalidate();
        }
    }
}
