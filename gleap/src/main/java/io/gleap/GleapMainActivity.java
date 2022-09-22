package io.gleap;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import gleap.io.gleap.R;
import io.gleap.CallCloseCallback;

public class GleapMainActivity extends AppCompatActivity implements OnHttpResponseListener {
    private WebView webView;
    private String url = GleapConfig.getInstance().getiFrameUrl();
    private ValueCallback<Uri[]> mUploadMessage;

    @Override
    public void onBackPressed() {
        GleapDetectorUtil.resumeAllDetectors();
        super.onBackPressed();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            this.requestWindowFeature(Window.FEATURE_NO_TITLE);
            try {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
            } catch (Exception ex) {
            }
            super.onCreate(savedInstanceState);

            GleapBug.getInstance().setLanguage(Locale.getDefault().getLanguage());

            url += GleapURLGenerator.generateURL();

            setContentView(R.layout.activity_gleap_main);

            WebView.setWebContentsDebuggingEnabled(true);

            if (getPackageManager().hasSystemFeature("android.software.webview")) {
                webView = findViewById(R.id.gleap_webview);


                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (webView.getVisibility() == View.INVISIBLE) {
                            finish();
                        }
                    }
                }, 15000);

                GleapConfig.getInstance().setCallCloseCallback(new CallCloseCallback() {
                    @Override
                    public void invoke() {
                        GleapDetectorUtil.resumeAllDetectors();
                        GleapBug.getInstance().setDisabled(false);
                        GleapMainActivity.this.finish();
                    }
                });
                initBrowser();
            }
        } catch (Exception ex) {
        }
    }


    private void initBrowser() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setDefaultTextEncodingName("utf-8");
        webView.setWebViewClient(new GleapWebViewClient());
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.addJavascriptInterface(new GleapJSBridge(this), "GleapJSBridge");
        webView.setWebChromeClient(new GleapWebChromeClient());
        webView.loadUrl(url);
        webView.setVisibility(View.INVISIBLE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
    }

    @Override
    public void onTaskComplete(int httpResponse) {
        if (httpResponse == 201) {
            try {
                sendMessage(generateGleapMessage("feedback-sent", null));
                GleapDetectorUtil.resumeAllDetectors();
                GleapBug.getInstance().setScreenshot(null);
                GleapBug.getInstance().setDisabled(false);
            } catch (Exception ex) {
            }
        } else {
            try {
                JSONObject message = new JSONObject();
                message.put("data", "Something went wrong, please try again.");
                message.put("name", "feedback-sending-failed");
                sendMessage(message.toString());
            } catch (Exception ex) {
            }
        }
    }

    private class GleapWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (!url.contains(GleapConfig.getInstance().getiFrameUrl())) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                return true;
            }
            return false;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
        }

        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            webView.setVisibility(View.GONE);

            AlertDialog alertDialog = new AlertDialog.Builder(GleapMainActivity.this).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (GleapConfig.getInstance().getWidgetClosedCallback() != null) {
                        GleapConfig.getInstance().getWidgetClosedCallback().invoke();
                    }
                    GleapDetectorUtil.resumeAllDetectors();
                    finish();

                }
            }).create();

            alertDialog.setTitle(getString(R.string.gleap_alert_no_internet_title));
            alertDialog.setMessage(getString(R.string.gleap_alert_no_internet_subtitle));
            try {
                alertDialog.show();
            } catch (Exception ex) {
            }

        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            JSONObject data = new JSONObject();
            try {
                data.put("isWidgetOpen", true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                sendMessage(generateGleapMessage("widget-status-update", data));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // GleapConfig.getInstance().setWebView(webView);
            LinkedList<GleapWebViewMessage> webViewMessages = GleapConfig.getInstance().getGleapWebViewMessages();

            for (GleapWebViewMessage gleapWVMessage :
                    webViewMessages) {
                try {
                    sendMessage(gleapWVMessage.getMessage());

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            GleapConfig.getInstance().getGleapWebViewMessages().clear();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // manejo de seleccion de archivo
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == 1) {

            if (null == mUploadMessage || intent == null || resultCode != RESULT_OK) {
                return;
            }

            Uri[] result = null;
            String dataString = intent.getDataString();

            if (dataString != null) {
                result = new Uri[]{Uri.parse(dataString)};
            }

            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
    }


    private class GleapWebChromeClient extends WebChromeClient {

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            try {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }

                mUploadMessage = filePathCallback;
                sendSessionUpdate();


                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*"); // set MIME type to filter

                ActivityUtil.getCurrentActivity().startActivityForResult(Intent.createChooser(i, "File Chooser"), 1);
            } catch (Exception ex) {
            }
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                  final JsPromptResult result) {
            return true;
        }

        private void sendSessionUpdate() {
            try {
                UserSession userSession = UserSessionController.getInstance().getUserSession();
                GleapUser gleapUser = UserSessionController.getInstance().getGleapUserSession();
                JSONObject sessionData = new JSONObject();
                sessionData.put("gleapId", userSession.getId());
                sessionData.put("gleapHash", userSession.getHash());

                sessionData.put("userId", gleapUser.getUserId());
                GleapUserProperties gleapUserProperties = gleapUser.getGleapUserProperties();
                if (gleapUserProperties != null) {
                    sessionData.put("name", gleapUserProperties.getName());
                    sessionData.put("email", gleapUserProperties.getEmail());

                    sessionData.put("value", gleapUserProperties.getValue());

                    if (gleapUserProperties.getPhoneNumber() != null) {
                        sessionData.put("phone", gleapUserProperties.getPhoneNumber());
                    }
                }

                JSONObject data = new JSONObject();
                data.put("sessionData", sessionData);
                data.put("apiUrl", GleapConfig.getInstance().getApiUrl());
                data.put("sdkKey", GleapConfig.getInstance().getSdkKey());

                sendMessage(generateGleapMessage("session-update", data));
            } catch (Exception exception) {

            }
        }
    }

    private class GleapJSBridge {
        private final AppCompatActivity mContext;

        public GleapJSBridge(AppCompatActivity c) {
            mContext = c;
        }

        @JavascriptInterface
        public void gleapCallback(String object) {
            this.mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject gleapCallback = new JSONObject(object);
                        String command = gleapCallback.getString("name");
                        switch (command) {
                            case "ping":
                                sendConfigUpdate();
                                sendSessionUpdate();
                                sendPrefillData();
                                sendScreenshotUpdate();
                                sendPendingActions();
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        webView.setVisibility(View.VISIBLE);
                                    }
                                }, 500);

                                break;
                            case "cleanup-drawings":
                                GleapBug.getInstance().setScreenshot(null);
                                break;
                            case "close-widget":
                                closeGleap();
                                break;
                            case "screenshot-updated":
                                updateScreenshot(gleapCallback);
                                break;
                            case "run-custom-action":
                                customActionCalled(gleapCallback);
                                break;
                            case "open-url":
                                openExternalURL(gleapCallback);
                                break;
                            case "notify-event":
                                notifyEvent(gleapCallback);
                                break;
                            case "send-feedback":
                                sendFeedback(gleapCallback);
                                break;
                        }
                    } catch (Exception err) {
                        System.out.println(err);
                    }
                }
            });
        }

        private void customActionCalled(JSONObject object) {
            try {
                String data = object.getString("data");

                if (GleapConfig.getInstance().getCustomActions() != null) {

                    GleapConfig.getInstance().getCustomActions().invoke(data);
                }
                finish();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        private void openExternalURL(JSONObject object) {
            try {
                String url = object.getString("data");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            } catch (Exception e) {
            }
        }

        private void notifyEvent(JSONObject object) {
            try {
                JSONObject data = object.getJSONObject("data");
                String eventType = data.getString("type");
                JSONObject eventData = data.getJSONObject("data");

                if (eventType.equals("flow-started")) {
                    if (GleapConfig.getInstance().getFeedbackFlowStartedCallback() != null) {
                        GleapConfig.getInstance().getFeedbackFlowStartedCallback().invoke(eventData.toString());
                    }
                }
            } catch (Exception ex) {
            }
        }

        private void sendPendingActions() {
            List<JSONObject> queue = GleapActionQueueHandler.getInstance().getActionQueue();
            for (JSONObject action :
                    queue) {
                try {
                    if (action.has("data")) {
                        JSONObject data = action.getJSONObject("data");
                        data.put("actionOutboundId", GleapBug.getInstance().getOutboubdId());
                    }
                    sendMessage(action.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            GleapActionQueueHandler.getInstance().clearActionMessageQueue();
        }

        private void updateScreenshot(JSONObject object) {
            if (object.has("data")) {
                String base64String = null;
                try {
                    base64String = object.getString("data");

                    if (base64String != null) {
                        String base64Image = base64String.split(",")[1];
                        byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        GleapBug.getInstance().setScreenshot(decodedByte);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void sendFeedback(JSONObject jsonObject) {
            this.mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject data = jsonObject.getJSONObject("data");
                        GleapBug gleapBug = GleapBug.getInstance();
                        try {
                            JSONObject action = data.getJSONObject("action");
                            if (action.has("feedbackType")) {
                                gleapBug.setType(action.getString("feedbackType"));
                            }

                            if (action.has("excludeData")) {
                                GleapConfig.getInstance().setStripModel(action.getJSONObject("excludeData"));
                            }

                            if (data.has("outboundId")) {
                                gleapBug.setOutboubdId(data.getString("outboundId"));
                            }

                            if (data.has("spamToken")) {
                                gleapBug.setSpamToken(data.getString("spamToken"));
                            }

                            if (data.has("formData")) {
                                JSONObject formData = data.getJSONObject("formData");
                                gleapBug.setData(formData);
                            }


                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        new HttpHelper(GleapMainActivity.this, getApplicationContext()).execute(gleapBug);
                    } catch (Exception ex) {
                    }
                }
            });
        }

        private void sendConfigUpdate() {
            try {
                JSONObject jsonObject = GleapConfig.getInstance().getPlainConfig();
                JSONObject data = new JSONObject();
                data.put("config", jsonObject.getJSONObject("flowConfig"));
                data.put("actions", jsonObject.getJSONObject("projectActions"));
                data.put("overrideLanguage", GleapConfig.getInstance().getLanguage());
                data.put("isApp", true);

                sendMessage(generateGleapMessage("config-update", data));
            } catch (Exception err) {
            }
        }

        private void sendPrefillData() {
            try {
                JSONObject data = PrefillHelper.getInstancen().getPreFillData();

                if (data != null) {
                    String message = generateGleapMessage("prefill-form-data", data);
                    sendMessage(message);
                }
            } catch (Exception err) {
                err.printStackTrace();
            }
        }

        private void sendSessionUpdate() {
            try {
                UserSession userSession = UserSessionController.getInstance().getUserSession();
                GleapUser gleapUser = UserSessionController.getInstance().getGleapUserSession();
                JSONObject sessionData = new JSONObject();
                sessionData.put("gleapId", userSession.getId());
                sessionData.put("gleapHash", userSession.getHash());
                if (gleapUser != null) {
                    sessionData.put("userId", gleapUser.getUserId());

                    GleapUserProperties gleapUserProperties = gleapUser.getGleapUserProperties();
                    if (gleapUserProperties != null) {
                        sessionData.put("name", gleapUserProperties.getName());
                        sessionData.put("email", gleapUserProperties.getEmail());

                        sessionData.put("value", gleapUserProperties.getValue());

                        if (gleapUserProperties.getPhoneNumber() != null) {
                            sessionData.put("phone", gleapUserProperties.getPhoneNumber());
                        }
                    }
                }
                JSONObject data = new JSONObject();
                data.put("sessionData", sessionData);
                data.put("apiUrl", GleapConfig.getInstance().getApiUrl());
                data.put("sdkKey", GleapConfig.getInstance().getSdkKey());

                sendMessage(generateGleapMessage("session-update", data));
            } catch (Exception exception) {
            }
        }

        private void sendScreenshotUpdate() {
            try {
                JSONObject message = new JSONObject();
                String image = ScreenshotUtil.bitmapToBase64(GleapBug.getInstance().getScreenshot());
                byte[] decodedString = Base64.decode(image, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                GleapBug.getInstance().setScreenshot(decodedByte);

                message.put("name", "screenshot-update");
                message.put("data", "data:image/png;base64," + image);
                sendMessage(message.toString());
            } catch (Exception err) {

            }
        }

        private void closeGleap() {
            this.mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GleapDetectorUtil.resumeAllDetectors();
                    GleapConfig.getInstance().setAction(null);
                    if (GleapConfig.getInstance().getWidgetClosedCallback() != null) {
                        GleapConfig.getInstance().getWidgetClosedCallback().invoke();
                    }

                    finish();
                    GleapInvisibleActivityManger.getInstance().setVisible();
                }
            });
        }
    }

    /**
     * Send message to JS
     *
     * @param message
     */
    public void sendMessage(String message) {
        webView.evaluateJavascript("sendMessage(" + message + ");", null);
    }

    private String generateGleapMessage(String name, JSONObject data) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("name", name);
        message.put("data", data);

        return message.toString();
    }
}