package io.gleap;

import static io.gleap.GleapCaptureUi.dp;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Shows the finished recording before it is sent: the video (looping), Send, Retake and
 * Cancel, and the upload progress. A dialog on the current activity; it is shown again on the
 * next activity after a rotation or an activity change.
 */
final class GleapCapturePreview {
    interface Listener {
        void onSendTapped();

        void onRetakeTapped();

        void onCancelTapped();
    }

    private final GleapCaptureRequest request;
    private final File video;
    private final int videoWidth;
    private final int videoHeight;
    private final Listener listener;

    private WeakReference<Activity> activity = new WeakReference<>(null);
    private Dialog dialog;
    private VideoView videoView;
    private TextView status;
    private LinearLayout buttons;
    private TextView send;
    private TextView retake;
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

    void show(Activity target) {
        dismiss();
        if (target == null || target.isFinishing()) {
            return;
        }
        View decor = target.getWindow() != null ? target.getWindow().peekDecorView() : null;
        if (decor == null || decor.getWindowToken() == null) {
            return;
        }
        activity = new WeakReference<>(target);
        try {
            dialog = new Dialog(target);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setTitle(request.label("previewTitle"));
            dialog.setContentView(buildViews(target, decor.getWidth(), decor.getHeight()));
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(Math.min(decor.getWidth() - 2 * dp(target, 16), dp(target, 520)),
                        WindowManager.LayoutParams.WRAP_CONTENT);
                window.setDimAmount(0.6f);
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
        status = null;
        buttons = null;
        send = null;
        retake = null;
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

    private void applyState() {
        if (status == null || buttons == null) {
            return;
        }
        if (uploading) {
            status.setVisibility(View.VISIBLE);
            String label = request.label("uploading");
            status.setText(progress > 0 ? String.format(Locale.ROOT, "%s %d%%", label, Math.round(progress * 100)) : label);
        } else if (error != null) {
            status.setVisibility(View.VISIBLE);
            status.setText(error);
        } else {
            status.setVisibility(View.GONE);
        }
        // Cancel stays available: it also stops an upload.
        if (send != null) {
            GleapCaptureUi.setButtonsEnabled(send, !uploading);
        }
        if (retake != null) {
            GleapCaptureUi.setButtonsEnabled(retake, !uploading);
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
                view.start();
            }
        });
        view.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer player, int what, int extra) {
                // The recording can still be sent without a preview.
                return true;
            }
        });
        view.setVideoPath(video.getAbsolutePath());
    }

    private View buildViews(Activity context, int windowWidth, int windowHeight) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GleapCaptureUi.card(context));
        int padding = dp(context, 16);
        card.setPadding(padding, padding, padding, dp(context, 12));

        TextView title = GleapCaptureUi.text(context, request.label("previewTitle"), 17, GleapCaptureUi.TEXT_COLOR, true);
        card.addView(title);

        // The video, fitted into the card and at most 55 % of the window high.
        int cardWidth = Math.min(windowWidth - 2 * dp(context, 16), dp(context, 520)) - 2 * padding;
        int maxHeight = Math.round(windowHeight * 0.55f);
        float aspect = videoHeight / (float) videoWidth;
        int width = Math.max(1, cardWidth);
        int height = Math.round(width * aspect);
        if (height > maxHeight) {
            height = maxHeight;
            width = Math.round(height / aspect);
        }
        FrameLayout videoFrame = new FrameLayout(context);
        videoFrame.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        frameParams.topMargin = dp(context, 12);
        videoView = new VideoView(context);
        videoView.setContentDescription(request.label("previewTitle"));
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(width, height);
        videoParams.gravity = Gravity.CENTER;
        videoFrame.addView(videoView, videoParams);
        card.addView(videoFrame, frameParams);

        status = GleapCaptureUi.text(context, "", 14, GleapCaptureUi.SECONDARY_TEXT_COLOR, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(context, 10);
        card.addView(status, statusParams);

        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(context, 12);

        TextView cancel = GleapCaptureUi.button(context, request.label("barCancel"), Color.TRANSPARENT, GleapCaptureUi.TEXT_COLOR);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onCancelTapped();
            }
        });
        buttons.addView(cancel);

        retake = GleapCaptureUi.button(context, request.label("previewRetake"), 0x33FFFFFF, GleapCaptureUi.TEXT_COLOR);
        retake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!uploading) {
                    listener.onRetakeTapped();
                }
            }
        });
        LinearLayout.LayoutParams retakeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retakeParams.setMarginStart(dp(context, 8));
        buttons.addView(retake, retakeParams);

        int primaryColor = GleapCaptureUi.primaryColor();
        send = GleapCaptureUi.button(context, request.label("previewSend"), primaryColor, GleapCaptureUi.contrastingText(primaryColor));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!uploading) {
                    listener.onSendTapped();
                }
            }
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sendParams.setMarginStart(dp(context, 8));
        buttons.addView(send, sendParams);
        card.addView(buttons, buttonsParams);
        return card;
    }
}
