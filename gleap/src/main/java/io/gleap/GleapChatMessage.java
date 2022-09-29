package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import gleap.io.gleap.R;

class GleapChatMessage {
    private String type = "comment";
    private String text;
    private String shareToken;

    private GleapSender sender;


    public GleapChatMessage(String type, String text, String shareToken, GleapSender sender) {
        this.sender = sender;
        this.type = type;
        this.text = text;
        this.shareToken = shareToken;
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
        new GleapImageHandler(getSender().getProfileImageUrl(), avatarImage).execute();

        completeMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getShareToken().equals("")) {
                    Gleap.getInstance().open();
                } else {
                    try {
                        JSONObject message = new JSONObject();
                        message.put("shareToken", getShareToken());
                        GleapConfig.getInstance().addGleapWebViewMessage(new GleapWebViewMessage("open-conversation", message));
                        Gleap.getInstance().open();
                    } catch (Exception ex) {
                    }
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
}
