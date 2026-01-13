package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.json.JSONObject;

import gleap.io.gleap.R;

class GleapChatMessage {
    private String outboundId;
    private String type = "comment";
    private String text;
    private String checklistId;
    private String shareToken;
    private String newsId;
    private String image;
    private GleapSender sender;
    private int currentStep;
    private int totalSteps;
    private String nextStepTitle;
    private Bitmap avatarBitmap = null;
    private Bitmap topImageBitmap = null;
    private LinearLayout layout;

    public GleapChatMessage(String outboundId, String type, String text, String shareToken, GleapSender sender, String newsId, String image, int currentStep, int totalSteps, String nextStepTitle, String checklistId) {
        this.outboundId = outboundId;
        this.sender = sender;
        this.type = type;
        this.text = text;
        this.shareToken = shareToken;
        this.newsId = newsId;
        this.image = image;
        this.currentStep = currentStep;
        this.totalSteps = totalSteps;
        this.nextStepTitle = nextStepTitle;
        this.checklistId = checklistId;
    }

    private void generateComponent(Activity activity) {
        if (type.equals("news")) {
            this.layout = getNews(activity);
        } else if (type.equals("checklist")) {
            this.layout = getChecklistCard(activity);
        } else {
            this.layout = getPlainMessage(activity);
        }
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public GleapSender getSender() {
        return sender;
    }

    public String getShareToken() {
        return shareToken;
    }

    public LinearLayout getComponent(Activity activity) {
        if (this.layout == null && activity != null) {
            generateComponent(activity);
        }
        return this.layout;
    }

    public void clearComponent() {
        if (this.avatarBitmap != null) {
            this.avatarBitmap.recycle();
            this.avatarBitmap = null;
        }

        if (this.topImageBitmap != null) {
            this.topImageBitmap.recycle();
            this.topImageBitmap = null;
        }

        this.layout = null;
    }

    public LinearLayout getNews(Activity local) {
        Activity activity = ActivityUtil.getCurrentActivity();
        LinearLayout completeMessage = new LinearLayout(local);
        completeMessage.setId(View.generateViewId());
        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.setVisibility(View.GONE);

        ImageView topImage = new ImageView(local);
        topImage.setMaxHeight(convertDpToPixel(155, activity));
        topImage.setMinimumHeight(convertDpToPixel(155, activity));
        topImage.setAdjustViewBounds(true);
        topImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        new GleapImageHandler(image, topImage, new GleapImageLoaded() {
            @Override
            public void invoke(Bitmap bitmap) {
                topImageBitmap = bitmap;
                completeMessage.setVisibility(View.VISIBLE);
                GleapInvisibleActivityManger.getInstance().updateCloseButtonState();
            }
        }).execute();

        completeMessage.addView(topImage);

        LinearLayout bottomPart = new LinearLayout(local);
        bottomPart.setOrientation(LinearLayout.VERTICAL);
        ImageView avatarImage = new ImageView(local);
        avatarImage.setMaxHeight(convertDpToPixel(24, activity));
        avatarImage.setMinimumHeight(convertDpToPixel(24, activity));
        avatarImage.setMinimumWidth(convertDpToPixel(24, activity));
        avatarImage.setMaxWidth(convertDpToPixel(24, activity));
        avatarImage.setAdjustViewBounds(true);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        avatarImage.setLayoutParams(avatarParams);

        if (avatarBitmap == null) {
            new GleapRoundImageHandler(getSender().getProfileImageUrl(), avatarImage, new GleapImageLoaded() {
                @Override
                public void invoke(Bitmap bitmap) {
                    avatarBitmap = bitmap;
                    GleapInvisibleActivityManger.animateViewInOut(completeMessage, true);
                }
            }).execute();
        } else {
            avatarImage.setImageBitmap(this.avatarBitmap);
            completeMessage.setVisibility(View.VISIBLE);
        }

        float width = (float) (getScreenWidth() * 0.8);
        if (width > convertDpToPixel(280, activity)) {
            width = convertDpToPixel(280, activity);
        }

        TextView titleComponent = new TextView(local);
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getText().replace("{{name}}", getName()));
        titleComponent.setTextSize(16);
        titleComponent.setTextColor(Color.BLACK);
        titleComponent.setSingleLine();
        titleComponent.setMaxWidth((int) width);
        titleComponent.setWidth((int) width);
        titleComponent.setEllipsize(TextUtils.TruncateAt.END);
        titleComponent.setTypeface(Typeface.DEFAULT_BOLD);
        titleComponent.setTextColor(Color.BLACK);
        titleComponent.setPadding(convertDpToPixel(0, local), convertDpToPixel(0, local), convertDpToPixel(10, local), convertDpToPixel(0, local));
        bottomPart.addView(titleComponent);

        TextView usernameTextView = new TextView(local);
        usernameTextView.setId(View.generateViewId());
        usernameTextView.setText(getSender().getName());
        usernameTextView.setTextColor(Color.GRAY);
        usernameTextView.setTextSize(14);

        LinearLayout.LayoutParams messageComponentParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageComponentParams.setMargins(convertDpToPixel(10, local), convertDpToPixel(0, local), convertDpToPixel(0, local), convertDpToPixel(0, local));
        usernameTextView.setLayoutParams(messageComponentParams);

        LinearLayout userLayout = new LinearLayout(local);
        userLayout.addView(avatarImage, convertDpToPixel(24, local), convertDpToPixel(24, local));
        userLayout.addView(usernameTextView);
        userLayout.setGravity(Gravity.CENTER_VERTICAL);
        userLayout.setPadding(0, convertDpToPixel(5, activity), 0, 0);

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomParams.setMargins(convertDpToPixel(17, local), convertDpToPixel(13, local), convertDpToPixel(17, local), convertDpToPixel(13, local));
        bottomPart.setLayoutParams(bottomParams);
        bottomPart.addView(userLayout);
        completeMessage.addView(bottomPart);
        completeMessage.setBackgroundColor(Color.WHITE);

        completeMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (!shareToken.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("shareToken", getShareToken());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-conversation", message));
                    } else if (!newsId.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("id", getNewsId());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-news-article", message));
                    } else if (!checklistId.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("id", getChecklistId());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-checklist", message));
                    }
                    Gleap.getInstance().open();
                } catch (Exception ex) {
                }

                GleapInvisibleActivityManger.getInstance().clearMessages();
            }
        });

        layout = completeMessage;
        return completeMessage;
    }

    private GradientDrawable createRoundedRectangleDrawable(int color, float topLeft, float topRight, float bottomRight, float bottomLeft) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setColor(color);
        gradientDrawable.setCornerRadii(new float[]{
                topLeft, topLeft,   // Top-left radius
                topRight, topRight, // Top-right radius
                bottomRight, bottomRight, // Bottom-right radius
                bottomLeft, bottomLeft    // Bottom-left radius
        });
        return gradientDrawable;
    }

    public LinearLayout getChecklistCard(Activity local) {
        Activity activity = ActivityUtil.getCurrentActivity();

        float width = (float) (getScreenWidth() * 0.8);
        if (width > convertDpToPixel(280, activity)) {
            width = convertDpToPixel(280, activity);
        }

        LinearLayout completeMessage = new LinearLayout(local);
        completeMessage.setId(View.generateViewId());
        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.setVisibility(View.VISIBLE);
        completeMessage.setBackgroundColor(Color.WHITE);
        completeMessage.setMinimumWidth((int) width);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        completeMessage.setPadding(convertDpToPixel(16, local), convertDpToPixel(12, local), convertDpToPixel(16, local), convertDpToPixel(12, local));
        completeMessage.setLayoutParams(mainParams);

        TextView titleComponent = new TextView(local);
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getText().replace("{{name}}", getName()));
        titleComponent.setTextSize(16);
        titleComponent.setTextColor(Color.BLACK);
        titleComponent.setSingleLine();
        titleComponent.setMaxWidth((int) width);
        titleComponent.setWidth((int) width);
        titleComponent.setMinWidth((int) width);
        titleComponent.setEllipsize(TextUtils.TruncateAt.END);
        titleComponent.setTypeface(Typeface.DEFAULT_BOLD);
        titleComponent.setTextColor(Color.BLACK);
        titleComponent.setPadding(convertDpToPixel(0, local), convertDpToPixel(0, local), convertDpToPixel(10, local), convertDpToPixel(0, local));
        completeMessage.addView(titleComponent);

        float cornerRadius = convertDpToPixel(4, local);

        float progress = (float)getCurrentStep() / (float)getTotalSteps();
        if (progress < 1.0) {
            progress += 0.04;
        }

        // Progress Bar Container
        LinearLayout progressBarContainer = new LinearLayout(local);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, convertDpToPixel(8, local));
        containerParams.setMargins(0, convertDpToPixel(12, local), 0, convertDpToPixel(12, local));
        progressBarContainer.setLayoutParams(containerParams);
        GradientDrawable progressBarBgDrawable = createRoundedRectangleDrawable(Color.parseColor("#EEEEEE"), cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        progressBarContainer.setBackground(progressBarBgDrawable);
        progressBarContainer.setOrientation(LinearLayout.HORIZONTAL); // Horizontal orientation
        completeMessage.addView(progressBarContainer);

        // Progress Bar
        View progressBar = new View(local);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, progress); // weight = progress
        progressBar.setLayoutParams(barParams);
        GradientDrawable progressBarDrawable = createRoundedRectangleDrawable(Color.parseColor(GleapConfig.getInstance().getColor()), cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        progressBar.setBackground(progressBarDrawable);
        progressBarContainer.addView(progressBar);

        // Progress Bar Background View
        View progressBarBg = new View(local);
        LinearLayout.LayoutParams bgParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1-progress); // weight = 1-progress
        progressBarBg.setLayoutParams(bgParams);
        progressBarContainer.addView(progressBarBg);

        TextView nextStepComponent = new TextView(local);
        nextStepComponent.setId(View.generateViewId());
        nextStepComponent.setText(getNextStepTitle().replace("{{name}}", getName()));
        nextStepComponent.setTextColor(Color.DKGRAY);
        nextStepComponent.setTextSize(15);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nextStepComponent.setLayoutParams(messageParams);
        completeMessage.addView(nextStepComponent);

        completeMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (!checklistId.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("id", getChecklistId());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-checklist", message));
                    }
                    Gleap.getInstance().open();
                } catch (Exception ex) {
                }

                GleapInvisibleActivityManger.getInstance().clearMessages();
            }
        });

        layout = completeMessage;
        return completeMessage;
    }

    public LinearLayout getPlainMessage(Activity local) {
        LinearLayout messageContainer = new LinearLayout(local);
        messageContainer.setVisibility(View.GONE);

        TextView titleComponent = new TextView(local);
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getSender().getName());
        titleComponent.setTextSize(14);
        titleComponent.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = convertDpToPixel(2, local);
        titleComponent.setLayoutParams(titleParams);

        TextView messageComponent = new TextView(local);
        messageComponent.setId(View.generateViewId());
        messageComponent.setText(getText().replace("{{name}}", getName()));
        messageComponent.setTextColor(Color.BLACK);
        messageComponent.setTextSize(16);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageComponent.setLayoutParams(titleParams);

        LinearLayout completeMessage = new LinearLayout(local);
        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.addView(titleComponent);
        completeMessage.addView(messageComponent);
        completeMessage.setBackgroundResource(R.drawable.chatbubble);
        completeMessage.setBaselineAligned(true);
        completeMessage.setPadding(convertDpToPixel(17, local), convertDpToPixel(13, local), convertDpToPixel(17, local), convertDpToPixel(13, local));

        CardView cardView = new CardView(local);
        cardView.setBackgroundResource(R.drawable.rounded_corner);
        LinearLayout.LayoutParams paramsBubble = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsBubble.setMargins(convertDpToPixel(5, local), convertDpToPixel(9, local), convertDpToPixel(15, local), convertDpToPixel(4, local));
        cardView.setLayoutParams(paramsBubble);

        cardView.setElevation(4f);
        cardView.addView(completeMessage);

        ImageView avatarImage = new ImageView(local);

        avatarImage.setMaxHeight(convertDpToPixel(28, local));
        avatarImage.setMinimumHeight(convertDpToPixel(28, local));
        avatarImage.setMinimumWidth(convertDpToPixel(28, local));
        avatarImage.setMaxWidth(convertDpToPixel(28, local));
        avatarImage.setAdjustViewBounds(true);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

        new GleapImageHandler(getSender().getProfileImageUrl(), avatarImage, new GleapImageLoaded() {
            @Override
            public void invoke(Bitmap bitmap) {
                avatarBitmap = bitmap;
                GleapInvisibleActivityManger.animateViewInOut(messageContainer, true);
                GleapInvisibleActivityManger.getInstance().updateCloseButtonState();
            }
        }).execute();

        completeMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (!shareToken.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("shareToken", getShareToken());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-conversation", message));

                    } else if (!newsId.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("id", getNewsId());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-news-article", message));
                    }
                    Gleap.getInstance().open();
                } catch (Exception ex) {
                }

                GleapInvisibleActivityManger.getInstance().clearMessages();
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        CardView rounded = new CardView(local);
        rounded.setRadius(convertDpToPixel(32, local) / 2);
        rounded.addView(avatarImage, convertDpToPixel(28, local), convertDpToPixel(28, local));

        messageContainer.addView(rounded);
        messageContainer.addView(cardView);
        rounded.setElevation(4f);
        LinearLayout.LayoutParams paramsAvatar = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsAvatar.setMargins(convertDpToPixel(1, local), convertDpToPixel(10, local), convertDpToPixel(6, local), convertDpToPixel(4, local));
        rounded.setLayoutParams(paramsAvatar);

        messageContainer.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        messageContainer.setOrientation(LinearLayout.HORIZONTAL);
        if (GleapConfig.getInstance().getWidgetPosition() != WidgetPosition.CLASSIC_LEFT && GleapConfig.getInstance().getWidgetPosition() != WidgetPosition.BOTTOM_LEFT) {
            params.setMargins(convertDpToPixel(20, local), convertDpToPixel(0, local), convertDpToPixel(5, local), convertDpToPixel(4, local));
        }

        messageContainer.setLayoutParams(params);
        return messageContainer;
    }

    private int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    private String getName() {
        try {
            GleapSessionProperties userProperties = GleapSessionController.getInstance().getGleapUserSession();
            if (userProperties != null) {
                return userProperties.getName().split(" ")[0].split("@")[0].split("\\.")[0].split("\\+")[0];
            }
            return "";
        } catch (Exception exp) {
            return "";
        }
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public String getNextStepTitle() {
        return nextStepTitle;
    }

    public String getNewsId() {
        return newsId;
    }

    public String getChecklistId() {
        return checklistId;
    }

    public String getOutboundId() {
        return outboundId;
    }
}
