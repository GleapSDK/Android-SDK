package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

/**
 * The feedback button in the overlay: the round launcher (with the unread counter) or the
 * classic "Feedback" tab on a screen edge, as the remote config says.
 */
class GleapFeedbackButton {
    private final GleapOverlayManager owner;
    // The button's parent in the overlay; null while there is no button.
    ConstraintLayout container;
    TextView notificationCountTextView;
    private ImageButton imageButton;
    private Bitmap fabIcon;
    private Button squareButton;

    GleapFeedbackButton(GleapOverlayManager owner) {
        this.owner = owner;
    }

    void destroyFab() {
        if (this.squareButton != null) {
            this.squareButton.setOnClickListener(null);
            this.squareButton = null;
        }

        if (this.imageButton != null) {
            this.imageButton.setOnClickListener(null);
            this.imageButton.setImageDrawable(null);
            this.imageButton = null;
        }

        // fabIcon is owned by GleapImageLoader's cache — drop the reference
        // but never recycle it, or the cache's size accounting breaks and
        // onTrimMemory crashes.
        this.fabIcon = null;

        if (this.notificationCountTextView != null) {
            this.notificationCountTextView = null;
        }

        if (this.container != null) {
            this.container.removeAllViews();

            try {
                if (owner.layout != null) {
                    owner.layout.removeView(this.container);
                }
            } catch (Exception exp) {}

            this.container = null;
        }
    }

    void addFab(Activity activity) {
        if (activity == null) {
            activity = ActivityUtil.getCurrentActivity();
        }

        if (activity == null) {
            return;
        }

        if (owner.layout == null) {
            return;
        }

        if (container != null) {
            return;
        }

        if (ActivityUtil.isGleapActivity(activity)) {
            return;
        }

        try {
            if (container == null) {
                container = new ConstraintLayout(activity);
            }

            Activity finalActivity = activity;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    container.setId(View.generateViewId());
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    container.setLayoutParams(params);
                    container.setVisibility(View.INVISIBLE);
                    owner.layout.addView(container);

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
                            owner.showFab = false;
                        }
                    }
                });
            }

            boolean manualHidden = GleapConfig.getInstance().isHideFeedbackButton();
            if (owner.showFab && !manualHidden) {
                container.setVisibility(View.VISIBLE);
            } else {
                container.setVisibility(View.GONE);
            }

            if (container.indexOfChild(imageButton) < 0) {
                container.addView(imageButton, convertDpToPixel(54, local), convertDpToPixel(54, local));
            }

            GradientDrawable gdDefaultText = new GradientDrawable();
            gdDefaultText.setColor(Color.RED);

            gdDefaultText.setCornerRadius(1000);

            notificationCountTextView = new TextView(local);
            notificationCountTextView.setId(View.generateViewId());
            notificationCountTextView.setBackground(gdDefaultText);
            notificationCountTextView.setTextColor(Color.WHITE);
            notificationCountTextView.setTextSize(12);
            notificationCountTextView.setText(String.valueOf(owner.messageCounter));
            notificationCountTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            notificationCountTextView.setGravity(Gravity.CENTER);
            notificationCountTextView.setVisibility(View.GONE);
            notificationCountTextView.bringToFront();
            container.addView(notificationCountTextView, convertDpToPixel(18, local), convertDpToPixel(18, local));

            if (fabIcon == null) {
                GleapImageLoader.loadRound(GleapConfig.getInstance().getButtonLogo(), imageButton, new GleapImageLoaded() {
                    @Override
                    public void invoke(Bitmap bitmap) {
                        fabIcon = bitmap;

                        local.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                GleapOverlayManager.animateViewInOut(imageButton, true);
                            }
                        });
                    }
                });
            } else {
                // Instantly show FAB if icon is already loaded.
                imageButton.setImageBitmap(fabIcon);
                imageButton.setVisibility(View.VISIBLE);
            }

            int offsetX = GleapConfig.getInstance().getButtonX() + 20;
            int offsetY = GleapConfig.getInstance().getButtonY();

            ConstraintSet set = new ConstraintSet();
            set.clone(owner.layout);
            if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.BOTTOM_RIGHT || GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.HIDDEN) {
                set.connect(container.getId(), ConstraintSet.END, owner.layout.getId(), ConstraintSet.END, convertDpToPixel(offsetX - 20, local));
            } else {
                set.connect(container.getId(), ConstraintSet.START, owner.layout.getId(), ConstraintSet.START, convertDpToPixel(offsetX - 20, local));
            }
            set.connect(container.getId(), ConstraintSet.BOTTOM, owner.layout.getId(), ConstraintSet.BOTTOM, convertDpToPixel(offsetY, local));
            set.applyTo(owner.layout);
        } catch (Exception ex) {}
    }

    void renderClassicFeedbackButton(Activity local) {
        try {
            boolean manualHidden = GleapConfig.getInstance().isHideFeedbackButton();
            if (owner.showFab && !manualHidden) {
                container.setVisibility(View.VISIBLE);
            } else {
                container.setVisibility(View.GONE);
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
                            owner.showFab = false;
                        }
                    }
                });

                if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                    container.setRotation(-90);
                } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                    container.setRotation(90);
                }

                squareButton.post(new Runnable() {
                    @Override
                    public void run() {
                        int height = squareButton.getHeight();
                        int width = squareButton.getWidth();

                        ConstraintSet buttonConstraintSet = new ConstraintSet();
                        buttonConstraintSet.clone(owner.layout);

                        if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_BOTTOM) {
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.END, owner.layout.getId(), ConstraintSet.END, convertDpToPixel(20, local));
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.BOTTOM, owner.layout.getId(), ConstraintSet.BOTTOM, 0);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_LEFT) {
                            container.setPadding(0, (width / 2 - height / 2) + 1, 0, 0);
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.START, owner.layout.getId(), ConstraintSet.START, 0);
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.TOP, owner.layout.getId(), ConstraintSet.TOP, width / 2);
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.BOTTOM, owner.layout.getId(), ConstraintSet.BOTTOM, 0);
                        } else if (GleapConfig.getInstance().getWidgetPosition() == WidgetPosition.CLASSIC_RIGHT) {
                            container.setPadding(0, (width / 2 - height / 2) + 1, 0, 0);
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.END, owner.layout.getId(), ConstraintSet.END, 0);
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.TOP, owner.layout.getId(), ConstraintSet.TOP, width / 2);
                            buttonConstraintSet.connect(container.getId(), ConstraintSet.BOTTOM, owner.layout.getId(), ConstraintSet.BOTTOM, 0);
                        }

                        buttonConstraintSet.applyTo(owner.layout);
                        GleapOverlayManager.animateViewInOut(squareButton, true);
                    }
                });
            }

            if (container.indexOfChild(squareButton) < 0) {
                container.addView(squareButton, 0, convertDpToPixel(36, local));
            }
        } catch (Exception ex) {
            GleapLog.w("Could not render the feedback button", ex);
        }
    }
}
