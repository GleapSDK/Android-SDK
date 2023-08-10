package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private String shareToken;
    private String newsId;
    private String image;
    private GleapSender sender;
    private Bitmap avatarBitmap = null;
    private Bitmap topImageBitmap = null;
    private LinearLayout layout;

    public GleapChatMessage(String outboundId, String type, String text, String shareToken, GleapSender sender, String newsId, String image) {
        this.outboundId = outboundId;
        this.sender = sender;
        this.type = type;
        this.text = text;
        this.shareToken = shareToken;
        this.newsId = newsId;
        this.image = image;
    }

    private void generateComponent(Activity activity) {
        if (type.equals("news")) {
            this.layout = getNews(activity);
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
        LinearLayout completeMessage = new LinearLayout(local.getApplication().getApplicationContext());
        completeMessage.setId(View.generateViewId());
        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.setVisibility(View.GONE);

        ImageView topImage = new ImageView(local.getApplication().getApplicationContext());
        topImage.setMaxHeight(convertDpToPixel(155, activity));
        topImage.setAdjustViewBounds(true);
        new GleapImageHandler(image, topImage, new GleapImageLoaded() {
            @Override
            public void invoke(Bitmap bitmap) {
                topImageBitmap = bitmap;
                completeMessage.setVisibility(View.VISIBLE);
            }
        }).execute();

        completeMessage.addView(topImage);

        /** Bottom Component **/
        LinearLayout bottomPart = new LinearLayout(local.getApplication().getApplicationContext());
        bottomPart.setOrientation(LinearLayout.VERTICAL);
        ImageView avatarImage = new ImageView(local.getApplication().getApplicationContext());
        avatarImage.setPadding(0, 0, convertDpToPixel(7, local), 0);

        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        avatarParams.setMargins(convertDpToPixel(5, local), convertDpToPixel(7, local), convertDpToPixel(5, local), convertDpToPixel(7, local));

        if (avatarBitmap == null) {
            avatarImage.setLayoutParams(avatarParams);
            new GleapRoundImageHandler(getSender().getProfileImageUrl(), avatarImage, new GleapImageLoaded() {
                @Override
                public void invoke(Bitmap bitmap) {
                    avatarBitmap = bitmap;
                    completeMessage.setVisibility(View.VISIBLE);
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

        TextView titleComponent = new TextView(local.getApplication().getApplicationContext());
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getText().replace("{{name}}", getName()));
        titleComponent.setTextSize(16);
        titleComponent.setTextColor(Color.BLACK);
        titleComponent.setSingleLine();
        titleComponent.setMaxWidth((int) width);
        titleComponent.setWidth((int) width);
        titleComponent.setEllipsize(TextUtils.TruncateAt.END);

        titleComponent.setTypeface(Typeface.DEFAULT_BOLD);
        titleComponent.setPadding(convertDpToPixel(7, local), convertDpToPixel(7, local), convertDpToPixel(7, local), convertDpToPixel(7, local));
        bottomPart.addView(titleComponent);

        TextView messageComponent = new TextView(local.getApplication().getApplicationContext());
        messageComponent.setId(View.generateViewId());
        messageComponent.setText(getSender().getName());
        messageComponent.setTextSize(13);

        LinearLayout userLayout = new LinearLayout(local.getApplication().getApplicationContext());
        userLayout.addView(avatarImage, convertDpToPixel(28, local), convertDpToPixel(28, local));
        userLayout.addView(messageComponent);
        userLayout.setGravity(Gravity.CENTER_VERTICAL);
        userLayout.setPadding(convertDpToPixel(10, activity), 0, convertDpToPixel(10, activity), 0);

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomParams.setMargins(convertDpToPixel(5, local), convertDpToPixel(7, local), convertDpToPixel(5, local), convertDpToPixel(7, local));
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
        LinearLayout messageContainer = new LinearLayout(local.getApplication().getApplicationContext());
        LinearLayout completeMessage = new LinearLayout(local.getApplication().getApplicationContext());

        TextView titleComponent = new TextView(local.getApplication().getApplicationContext());
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getSender().getName());
        titleComponent.setTextSize(13);
        titleComponent.setTextColor(Color.BLACK);
        messageContainer.setVisibility(View.GONE);

        TextView messageComponent = new TextView(local.getApplication().getApplicationContext());
        messageComponent.setId(View.generateViewId());
        messageComponent.setText(getText().replace("{{name}}", getName()));
        messageComponent.setTextSize(14);

        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.addView(titleComponent);
        completeMessage.addView(messageComponent);

        completeMessage.setBackgroundResource(R.drawable.chatbubble);

        completeMessage.setBaselineAligned(true);
        completeMessage.setPadding(convertDpToPixel(17, local), convertDpToPixel(13, local), convertDpToPixel(17, local), convertDpToPixel(13, local));

        CardView cardView = new CardView(local.getApplication().getApplicationContext());

        cardView.setBackgroundResource(R.drawable.rounded_corner);
        LinearLayout.LayoutParams paramsBubble = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsBubble.setMargins(convertDpToPixel(5, local), convertDpToPixel(9, local), convertDpToPixel(15, local), convertDpToPixel(4, local));
        cardView.setLayoutParams(paramsBubble);

        cardView.setElevation(4f);
        cardView.addView(completeMessage);

        ImageView avatarImage = new ImageView(local.getApplication().getApplicationContext());

        avatarImage.setAdjustViewBounds(true);
        new GleapImageHandler(getSender().getProfileImageUrl(), avatarImage, new GleapImageLoaded() {
            @Override
            public void invoke(Bitmap bitmap) {
                avatarBitmap = bitmap;
                GleapInvisibleActivityManger.animateViewInOut(messageContainer, true);
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

        CardView rounded = new CardView(local.getApplication().getApplicationContext());
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
            GleapUser gleapUser = UserSessionController.getInstance().getGleapUserSession();
            if (gleapUser != null) {
                GleapUserProperties userProperties = gleapUser.getGleapUserProperties();
                if (userProperties != null) {
                    return userProperties.getName().split(" ")[0].split("@")[0].split("\\.")[0].split("\\+")[0];
                }
                return "";
            }
            return "";
        } catch (Exception exp) {
            return "";
        }
    }

    public String getNewsId() {
        return newsId;
    }

    public String getOutboundId() {
        return outboundId;
    }

    public void setOutboundId(String outboundId) {
        this.outboundId = outboundId;
    }
}
