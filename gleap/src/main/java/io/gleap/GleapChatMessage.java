package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import gleap.io.gleap.R;

class GleapChatMessage {
    private String type = "comment";
    private String text;
    private String shareToken;
    private String newsId;
    private String image;

    private GleapSender sender;


    public GleapChatMessage(String type, String text, String shareToken, GleapSender sender, String newsId, String image) {
        this.sender = sender;
        this.type = type;
        this.text = text;
        this.shareToken = shareToken;
        this.newsId = newsId;
        this.image = image;
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

    public LinearLayout getComponent(Activity local) {
        //add avatar and bubble
        if (type.equals("news")) {
            return getNews(local);
        }
        return getPlainMessage(local);
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
        GradientDrawable gdDefaultText = new GradientDrawable();

        gdDefaultText.setCornerRadius(1000);
        new GleapImageHandler(image, topImage, new GleapImageLoaded() {
            @Override
            public void invoke() {
                completeMessage.setVisibility(View.VISIBLE);
            }
        }).execute();
        completeMessage.addView(topImage);

        LinearLayout bottomPart = new LinearLayout(local.getApplication().getApplicationContext());
        bottomPart.setOrientation(LinearLayout.VERTICAL);

        ImageView avatarImage = new ImageView(local.getApplication().getApplicationContext());
        avatarImage.setPadding(0, 0, convertDpToPixel(7, local), 0);

        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        avatarParams.setMargins(convertDpToPixel(5, local), convertDpToPixel(7, local), convertDpToPixel(5, local), convertDpToPixel(7, local));

        avatarImage.setLayoutParams(avatarParams);
        new GleapRoundImageHandler(getSender().getProfileImageUrl(), avatarImage).execute();

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        float width = getScreenWidth() * 9/10;
        if(width > convertDpToPixel(320, activity)) {
            width = convertDpToPixel(320, activity);
        }
        params.width = (int)width;

        params.setMargins(convertDpToPixel(0, local), convertDpToPixel(5, local), convertDpToPixel(0, local), convertDpToPixel(5, local));


        TextView titleComponent = new TextView(local.getApplication().getApplicationContext());
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getText().replace("{{name}}", getName()));
        titleComponent.setTextSize(16);
        titleComponent.setTextColor(Color.BLACK);
        titleComponent.setSingleLine();
        titleComponent.setMaxWidth((int)width);
        titleComponent.setEllipsize(TextUtils.TruncateAt.END);

        titleComponent.setTypeface(Typeface.DEFAULT_BOLD);
        titleComponent.setPadding(convertDpToPixel(7, local),convertDpToPixel(7, local),convertDpToPixel(7, local),convertDpToPixel(7, local));
        bottomPart.addView(titleComponent);

        TextView messageComponent = new TextView(local.getApplication().getApplicationContext());
        messageComponent.setId(View.generateViewId());
        messageComponent.setText(getSender().getName());
        messageComponent.setTextSize(13);

        LinearLayout userLayout = new LinearLayout(local.getApplication().getApplicationContext());
        userLayout.setOrientation(LinearLayout.HORIZONTAL);

        userLayout.addView(avatarImage, convertDpToPixel(28, local), convertDpToPixel(28, local));
        userLayout.addView(messageComponent);

        userLayout.setGravity(Gravity.CENTER_VERTICAL);

        userLayout.setPadding(convertDpToPixel(10, activity), 0, convertDpToPixel(10, activity), 0);

        bottomPart.addView(userLayout);

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomParams.setMargins(convertDpToPixel(5, local), convertDpToPixel(7, local), convertDpToPixel(5, local), convertDpToPixel(7, local));

        bottomPart.setLayoutParams(bottomParams);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);

        completeMessage.addView(bottomPart);
        completeMessage.setLayoutParams(params);

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(Color.WHITE);
        gradientDrawable.setCornerRadii(new float[]{20, 20, 20, 20, 20, 20, 20, 20});
        completeMessage.setBackground(gradientDrawable);
        completeMessage.setElevation(3f);


        return completeMessage;
    }


    public LinearLayout getPlainMessage(Activity local) {
        LinearLayout completeMessage = new LinearLayout(local.getApplication().getApplicationContext());
        completeMessage.setId(View.generateViewId());

        TextView titleComponent = new TextView(local.getApplication().getApplicationContext());
        titleComponent.setId(View.generateViewId());
        titleComponent.setText(getSender().getName());
        titleComponent.setTextSize(13);
        titleComponent.setTextColor(Color.BLACK);

        TextView messageComponent = new TextView(local.getApplication().getApplicationContext());
        messageComponent.setId(View.generateViewId());
        messageComponent.setText(getText().replace("{{name}}", getName()));
        messageComponent.setTextSize(13);
        //use half screen max
        messageComponent.setMaxWidth(getScreenWidth() / 2);


        completeMessage.setOrientation(LinearLayout.VERTICAL);
        completeMessage.addView(titleComponent);
        completeMessage.addView(messageComponent);

        LinearLayout.LayoutParams paramsCompleteMessage = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsCompleteMessage.setMargins(convertDpToPixel(5, local), convertDpToPixel(7, local), convertDpToPixel(5, local), convertDpToPixel(7, local));


        completeMessage.setBackgroundResource(R.drawable.chatbubble);
        completeMessage.setElevation(3f);

        completeMessage.setBaselineAligned(true);
        completeMessage.setLayoutParams(paramsCompleteMessage);
        completeMessage.setPadding(convertDpToPixel(15, local), convertDpToPixel(10, local), convertDpToPixel(15, local), convertDpToPixel(10, local));

        ImageView avatarImage = new ImageView(local.getApplication().getApplicationContext());
        avatarImage.setPadding(0, 0, convertDpToPixel(7, local), 0);

        avatarImage.setLayoutParams(paramsCompleteMessage);
        new GleapRoundImageHandler(getSender().getProfileImageUrl(), avatarImage).execute();

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
        params.setMargins(convertDpToPixel(0, local), convertDpToPixel(5, local), convertDpToPixel(0, local), convertDpToPixel(5, local));

        LinearLayout messageContainer = new LinearLayout(local.getApplication().getApplicationContext());
        messageContainer.addView(avatarImage, convertDpToPixel(40, local), convertDpToPixel(40, local));
        messageContainer.addView(completeMessage);

        messageContainer.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        messageContainer.setOrientation(LinearLayout.HORIZONTAL);
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

    private void sendMessage(WebView webView, String message) {
        webView.evaluateJavascript("sendMessage(" + message + ");", null);
    }

    private String generateGleapMessage(String name, JSONObject data) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("name", name);
        message.put("data", data);

        return message.toString();
    }

    public String getNewsId() {
        return newsId;
    }
}
