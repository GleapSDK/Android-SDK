package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.json.JSONObject;

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
    private String sendAt;
    private String createdAt;
    private Bitmap avatarBitmap = null;
    private Bitmap topImageBitmap = null;
    private LinearLayout layout;

    public GleapChatMessage(String outboundId, String type, String text, String shareToken, GleapSender sender, String newsId, String image, int currentStep, int totalSteps, String nextStepTitle, String checklistId, String sendAt, String createdAt) {
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
        this.sendAt = sendAt;
        this.createdAt = createdAt;
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

    /**
     * Every notification card shares one chrome: full stack width, the
     * project's container radius, a hairline border and a soft shadow, on the
     * widget theme's background color. The card clips its children to the
     * rounded outline, so e.g. a news cover squares off against the corners.
     */
    private CardView styledCard(Activity local, View content) {
        CardView cardView = new CardView(local);
        int containerRadius = GleapNotificationStyle.containerRadiusPx(local);
        cardView.setRadius(containerRadius);
        // Soft and airy, matching the iOS SDK's two-layer look — a low
        // elevation, further lightened where the platform allows tinting.
        cardView.setCardElevation(convertDpToPixel(3, local));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cardView.setOutlineSpotShadowColor(Color.argb(115, 0, 0, 0));
        }
        cardView.setCardBackgroundColor(GleapNotificationStyle.backgroundColor());
        cardView.setClipToOutline(true);
        cardView.setUseCompatPadding(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            GradientDrawable hairline = new GradientDrawable();
            hairline.setShape(GradientDrawable.RECTANGLE);
            hairline.setColor(Color.TRANSPARENT);
            hairline.setCornerRadius(containerRadius);
            hairline.setStroke(convertDpToPixel(1, local), GleapNotificationStyle.hairlineColor());
            cardView.setForeground(hairline);
        }

        cardView.addView(content, new CardView.LayoutParams(CardView.LayoutParams.MATCH_PARENT, CardView.LayoutParams.WRAP_CONTENT));
        return cardView;
    }

    // Sender avatar with the shape split the messenger makes: teammates stay
    // circular, the bot gets a rounded square. `isBot` is absent on payloads
    // from servers that don't send it yet, which falls through to the circle.
    private ImageView avatarImageView(Activity local, int sizeDp) {
        ImageView avatarImage = new ImageView(local);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImage.setBackgroundColor(GleapNotificationStyle.shadeOfColor(GleapNotificationStyle.backgroundColor(), GleapNotificationStyle.isDarkTheme() ? 30 : -12));

        final int radiusPx = sender != null && sender.isBot()
                ? GleapNotificationStyle.botAvatarRadiusPx(local, sizeDp)
                : convertDpToPixel(sizeDp, local) / 2;
        avatarImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        avatarImage.setClipToOutline(true);

        GleapImageLoader.load(getSender().getProfileImageUrl(), avatarImage, convertDpToPixel(sizeDp, local), new GleapImageLoaded() {
            @Override
            public void invoke(Bitmap bitmap) {
                avatarBitmap = bitmap;
            }
        });

        return avatarImage;
    }

    private View.OnClickListener cardClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // A collapsed stack expands on the first tap instead of
                // activating the card — same as the web widget on touch devices.
                if (GleapInvisibleActivityManger.getInstance().maybeExpandStackOnTap()) {
                    return;
                }

                try {
                    if (shareToken != null && !shareToken.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("shareToken", getShareToken());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-conversation", message));
                    } else if (newsId != null && !newsId.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("id", getNewsId());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-news-article", message));
                    } else if (checklistId != null && !checklistId.equals("")) {
                        JSONObject message = new JSONObject();
                        message.put("id", getChecklistId());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-checklist", message));
                    }
                    Gleap.getInstance().open();
                } catch (Exception ex) {
                }

                GleapInvisibleActivityManger.getInstance().clearMessages();
            }
        };
    }

    public LinearLayout getNews(Activity local) {
        int contrastColor = GleapNotificationStyle.contrastColor();
        int subTextColor = GleapNotificationStyle.subTextColor();
        int contentPadding = convertDpToPixel(16, local);

        LinearLayout cardContent = new LinearLayout(local);
        cardContent.setOrientation(LinearLayout.VERTICAL);

        // The cover image squares off against the card's rounded top corners
        // through the card's outline clip.
        ImageView topImage = new ImageView(local);
        topImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        topImage.setBackgroundColor(GleapNotificationStyle.shadeOfColor(GleapNotificationStyle.backgroundColor(), GleapNotificationStyle.isDarkTheme() ? 30 : -12));
        cardContent.addView(topImage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, convertDpToPixel(155, local)));
        GleapImageLoader.load(image, topImage, new GleapImageLoaded() {
            @Override
            public void invoke(Bitmap bitmap) {
                topImageBitmap = bitmap;
            }
        });

        LinearLayout bottomPart = new LinearLayout(local);
        bottomPart.setOrientation(LinearLayout.VERTICAL);
        bottomPart.setPadding(contentPadding, contentPadding, contentPadding, contentPadding);

        TextView titleComponent = new TextView(local);
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getText().replace("{{name}}", getName()));
        titleComponent.setTextSize(15);
        titleComponent.setTextColor(contrastColor);
        titleComponent.setSingleLine();
        titleComponent.setEllipsize(TextUtils.TruncateAt.END);
        titleComponent.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bottomPart.addView(titleComponent);

        boolean hasSender = getSender() != null && getSender().getName() != null && !getSender().getName().equals("");
        if (hasSender) {
            LinearLayout userLayout = new LinearLayout(local);
            userLayout.setOrientation(LinearLayout.HORIZONTAL);
            userLayout.setGravity(Gravity.CENTER_VERTICAL);

            boolean hasAvatar = getSender().getProfileImageUrl() != null && !getSender().getProfileImageUrl().equals("");
            if (hasAvatar) {
                ImageView avatarImage = avatarImageView(local, 20);
                LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(convertDpToPixel(20, local), convertDpToPixel(20, local));
                avatarParams.setMarginEnd(convertDpToPixel(8, local));
                userLayout.addView(avatarImage, avatarParams);
            }

            TextView usernameTextView = new TextView(local);
            usernameTextView.setId(View.generateViewId());
            usernameTextView.setText(getSender().getName());
            usernameTextView.setTextColor(subTextColor);
            usernameTextView.setTextSize(14);
            usernameTextView.setSingleLine();
            usernameTextView.setEllipsize(TextUtils.TruncateAt.END);
            userLayout.addView(usernameTextView);

            LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            userParams.setMargins(0, convertDpToPixel(6, local), 0, 0);
            bottomPart.addView(userLayout, userParams);
        }

        cardContent.addView(bottomPart, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        CardView cardView = styledCard(local, cardContent);

        LinearLayout completeMessage = new LinearLayout(local);
        completeMessage.setId(View.generateViewId());
        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.addView(cardView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        completeMessage.setOnClickListener(cardClickListener());
        cardView.setOnClickListener(cardClickListener());

        layout = completeMessage;
        return completeMessage;
    }

    public LinearLayout getChecklistCard(Activity local) {
        int contrastColor = GleapNotificationStyle.contrastColor();
        int subTextColor = GleapNotificationStyle.subTextColor();
        int contentPadding = convertDpToPixel(16, local);

        LinearLayout cardContent = new LinearLayout(local);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(contentPadding, contentPadding, contentPadding, contentPadding);

        TextView titleComponent = new TextView(local);
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getText().replace("{{name}}", getName()));
        titleComponent.setTextSize(16);
        titleComponent.setTextColor(contrastColor);
        titleComponent.setSingleLine();
        titleComponent.setEllipsize(TextUtils.TruncateAt.END);
        titleComponent.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cardContent.addView(titleComponent);

        float cornerRadius = convertDpToPixel(4, local);

        float progress = (float) getCurrentStep() / (float) getTotalSteps();
        if (progress < 1.0) {
            progress += 0.04;
        }

        // Progress Bar Container
        LinearLayout progressBarContainer = new LinearLayout(local);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, convertDpToPixel(8, local));
        containerParams.setMargins(0, convertDpToPixel(12, local), 0, convertDpToPixel(12, local));
        progressBarContainer.setLayoutParams(containerParams);
        int progressTrackColor = Color.argb(38, Color.red(contrastColor), Color.green(contrastColor), Color.blue(contrastColor));
        GradientDrawable progressBarBgDrawable = createRoundedRectangleDrawable(progressTrackColor, cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        progressBarContainer.setBackground(progressBarBgDrawable);
        progressBarContainer.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.addView(progressBarContainer);

        // Progress Bar
        View progressBar = new View(local);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, progress);
        progressBar.setLayoutParams(barParams);
        GradientDrawable progressBarDrawable = createRoundedRectangleDrawable(Color.parseColor(GleapConfig.getInstance().getColor()), cornerRadius, cornerRadius, cornerRadius, cornerRadius);
        progressBar.setBackground(progressBarDrawable);
        progressBarContainer.addView(progressBar);

        // Progress Bar Background View
        View progressBarBg = new View(local);
        LinearLayout.LayoutParams bgParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1 - progress);
        progressBarBg.setLayoutParams(bgParams);
        progressBarContainer.addView(progressBarBg);

        TextView nextStepComponent = new TextView(local);
        nextStepComponent.setId(View.generateViewId());
        nextStepComponent.setText(getNextStepTitle().replace("{{name}}", getName()));
        nextStepComponent.setTextColor(subTextColor);
        nextStepComponent.setTextSize(14);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nextStepComponent.setLayoutParams(messageParams);
        cardContent.addView(nextStepComponent);

        CardView cardView = styledCard(local, cardContent);

        LinearLayout completeMessage = new LinearLayout(local);
        completeMessage.setId(View.generateViewId());
        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.addView(cardView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        completeMessage.setOnClickListener(cardClickListener());
        cardView.setOnClickListener(cardClickListener());

        layout = completeMessage;
        return completeMessage;
    }

    // Standard non-news notification. Avatar and text live inside one card (no
    // speech-bubble tail), with the sender + time as a meta line below the
    // message.
    public LinearLayout getPlainMessage(Activity local) {
        int contrastColor = GleapNotificationStyle.contrastColor();
        int subTextColor = GleapNotificationStyle.subTextColor();
        int contentPadding = convertDpToPixel(16, local);
        int avatarSizeDp = 32;

        LinearLayout cardContent = new LinearLayout(local);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setBaselineAligned(false);
        cardContent.setPadding(contentPadding, contentPadding, contentPadding, contentPadding);

        boolean hasAvatar = getSender() != null && getSender().getProfileImageUrl() != null && !getSender().getProfileImageUrl().equals("");
        if (hasAvatar) {
            ImageView avatarImage = avatarImageView(local, avatarSizeDp);
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(convertDpToPixel(avatarSizeDp, local), convertDpToPixel(avatarSizeDp, local));
            avatarParams.setMarginEnd(convertDpToPixel(10, local));
            cardContent.addView(avatarImage, avatarParams);
        }

        LinearLayout body = new LinearLayout(local);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView messageComponent = new TextView(local);
        messageComponent.setId(View.generateViewId());
        messageComponent.setText(getText().replace("{{name}}", getName()));
        messageComponent.setTextColor(contrastColor);
        messageComponent.setTextSize(15);
        messageComponent.setMaxLines(2);
        messageComponent.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(messageComponent, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // The "Sender · 5 minutes ago" line under the message. Either half may
        // be missing, so the separator only appears when both are present. The
        // sender may truncate; the timestamp never does.
        String senderName = getSender() != null ? getSender().getName() : null;
        boolean hasSenderName = senderName != null && !senderName.equals("");
        String timeLabel = GleapNotificationStyle.relativeTimeLabel(sendAt, createdAt);
        if (hasSenderName || timeLabel != null) {
            LinearLayout metaRow = new LinearLayout(local);
            metaRow.setOrientation(LinearLayout.HORIZONTAL);
            metaRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView timeTextView = null;
            float timeWidth = 0;
            if (timeLabel != null) {
                timeTextView = new TextView(local);
                timeTextView.setText(timeLabel);
                timeTextView.setTextSize(13);
                timeTextView.setTextColor(subTextColor);
                timeTextView.setSingleLine();
                timeWidth = timeTextView.getPaint().measureText(timeLabel);
            }

            if (hasSenderName) {
                TextView senderTextView = new TextView(local);
                senderTextView.setText(senderName);
                senderTextView.setTextSize(13);
                senderTextView.setTextColor(subTextColor);
                senderTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                senderTextView.setSingleLine();
                senderTextView.setEllipsize(TextUtils.TruncateAt.END);

                if (timeTextView != null) {
                    int dotSpace = convertDpToPixel(13, local);
                    int bodyWidth = GleapNotificationStyle.stackWidthPx(local) - (contentPadding * 2) - (hasAvatar ? convertDpToPixel(avatarSizeDp + 10, local) : 0);
                    senderTextView.setMaxWidth(Math.max(0, bodyWidth - dotSpace - (int) Math.ceil(timeWidth)));
                }
                metaRow.addView(senderTextView);

                if (timeTextView != null) {
                    TextView dotTextView = new TextView(local);
                    dotTextView.setText("•");
                    dotTextView.setTextSize(13);
                    dotTextView.setTextColor(Color.argb(153, Color.red(subTextColor), Color.green(subTextColor), Color.blue(subTextColor)));
                    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    dotParams.setMarginStart(convertDpToPixel(5, local));
                    dotParams.setMarginEnd(convertDpToPixel(5, local));
                    metaRow.addView(dotTextView, dotParams);
                }
            }

            if (timeTextView != null) {
                metaRow.addView(timeTextView);
            }

            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            metaParams.setMargins(0, convertDpToPixel(5, local), 0, 0);
            body.addView(metaRow, metaParams);
        }

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cardContent.addView(body, bodyParams);

        CardView cardView = styledCard(local, cardContent);

        LinearLayout completeMessage = new LinearLayout(local);
        completeMessage.setId(View.generateViewId());
        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.addView(cardView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        completeMessage.setOnClickListener(cardClickListener());
        cardView.setOnClickListener(cardClickListener());

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
