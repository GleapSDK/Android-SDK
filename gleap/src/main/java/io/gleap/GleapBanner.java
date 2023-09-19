package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import org.json.JSONException;
import org.json.JSONObject;

import gleap.io.gleap.R;

class GleapBanner {
    private JSONObject bannerData;
    private String bannerUrl = "https://outboundmedia.gleap.io";
    private WebView webView = null;
    private LinearLayout layout;
    private Activity parentActivity;
    private boolean initialized = false;

    public GleapBanner(JSONObject bannerData, Activity activity) {
        this.bannerData = bannerData;
        this.parentActivity = activity;
        generateComponent();
    }

    private void generateComponent() {
        this.layout = getBannerComponent();
    }

    public LinearLayout getComponent() {
        return this.layout;
    }

    public void clearComponent() {
        this.layout = null;
        if (this.webView != null) {
            this.webView.clearHistory();
            this.webView.clearCache(true);
            this.webView.onPause();
            this.webView.removeAllViews();
            this.webView.destroyDrawingCache();
            this.webView.destroy();
        }
        this.webView = null;
        this.bannerData = null;
    }

    public LinearLayout getBannerComponent() {
        LinearLayout bannerContainer = new LinearLayout(this.parentActivity.getApplication().getApplicationContext());
        bannerContainer.setOrientation(LinearLayout.VERTICAL);
        bannerContainer.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bannerContainer.setLayoutParams(bannerParams);
        bannerContainer.setId(View.generateViewId());
        bannerContainer.setVisibility(View.GONE);

        this.parentActivity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    // Create the WebView and load a URL
                    webView = new WebView(parentActivity.getApplication().getApplicationContext());
                    LinearLayout.LayoutParams webViewParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            100
                    );
                    webView.setLayoutParams(webViewParams);

                    WebSettings settings = webView.getSettings();
                    settings.setJavaScriptEnabled(true);
                    settings.setLoadWithOverviewMode(false);
                    settings.setUseWideViewPort(false);
                    settings.setBuiltInZoomControls(false);
                    settings.setDisplayZoomControls(false);
                    settings.setSupportZoom(false);
                    settings.setDefaultTextEncodingName("utf-8");
                    webView.setWebContentsDebuggingEnabled(true);
                    webView.addJavascriptInterface(new GleapBanner.GleapBannerJSBridge(), "GleapBannerJSBridge");
                    webView.setWebChromeClient(new GleapBanner.GleapBannerWebChromeClient());
                    webView.setWebViewClient(new GleapBanner.GleapWebViewClient());
                    webView.loadUrl(bannerUrl);

                    boolean isFloating = false;
                    try {
                        if (bannerData.getString("format").equalsIgnoreCase("floating")) {
                            isFloating = true;
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }

                    if (isFloating) {
                        CardView cardView = new CardView(parentActivity.getApplication().getApplicationContext());
                        cardView.setBackgroundResource(R.drawable.rounded_corner);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.setMargins(convertDpToPixel(20, parentActivity), convertDpToPixel(20, parentActivity), convertDpToPixel(20, parentActivity), convertDpToPixel(20, parentActivity));
                        cardView.setLayoutParams(params);
                        cardView.setElevation(20f);
                        cardView.addView(webView);
                        bannerContainer.addView(cardView);
                    } else {
                        bannerContainer.addView(webView);
                    }

                } catch (Exception exp) {
                    System.out.println(exp);
                }
            }
        });

        return bannerContainer;
    }


    private class GleapBannerJSBridge {
        public GleapBannerJSBridge() {}

        @JavascriptInterface
        public void gleapBannerCallback(String object) {
            if (parentActivity != null) {
                parentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject gleapCallback = new JSONObject(object);
                            String command = gleapCallback.getString("name");

                            switch (command) {
                                case "banner-loaded":
                                    sendBannerData();
                                    break;
                                case "banner-data-set":
                                    showWebView();
                                    break;
                                case "banner-close":
                                    GleapInvisibleActivityManger.getInstance().destoryBanner(true);
                                    break;
                                case "start-conversation":
                                    try {
                                        String botId = "";
                                        if (gleapCallback.has("data") && gleapCallback.getJSONObject("data").has("botId")) {
                                            botId = gleapCallback.getJSONObject("data").getString("botId");
                                        }
                                        Gleap.getInstance().startBot(botId);
                                    }catch (Exception exp) {}
                                    break;
                                case "show-form":
                                    try {
                                        String formId = "";
                                        if (gleapCallback.has("data") && gleapCallback.getJSONObject("data").has("formId")) {
                                            formId = gleapCallback.getJSONObject("data").getString("formId");
                                        }
                                        Gleap.getInstance().startFeedbackFlow(formId);
                                    }catch (Exception exp) {}
                                    break;
                                case "open-url":
                                    try {
                                        String url = gleapCallback.getString("data");
                                        if (url != null && url.length() > 0) {
                                            openExternalURL(url);
                                        }
                                    }catch (Exception exp) {}
                                    break;
                                case "start-custom-action":
                                    try {
                                        String action = gleapCallback.getJSONObject("data").getString("action");
                                        if (GleapConfig.getInstance().getCustomActions() != null) {
                                            GleapConfig.getInstance().getCustomActions().invoke(action);
                                        }
                                    }catch (Exception exp) {}
                                    break;
                                case "show-survey":
                                    try {
                                        String formId = "";
                                        if (gleapCallback.has("data") && gleapCallback.getJSONObject("data").has("formId")) {
                                            formId = gleapCallback.getJSONObject("data").getString("formId");
                                        }

                                        SurveyType surveyType = SurveyType.SURVEY_FULL;
                                        if (gleapCallback.has("data") && gleapCallback.getJSONObject("data").has("surveyFormat")) {
                                            String surveyFormat = gleapCallback.getJSONObject("data").getString("surveyFormat");
                                            if (surveyFormat.equalsIgnoreCase("survey")) {
                                                surveyType = SurveyType.SURVEY;
                                            }
                                        }
                                        Gleap.getInstance().showSurvey(formId, surveyType);
                                    }catch (Exception exp) {}
                                    break;
                                case "show-news-article":
                                    try {
                                        String articleId = gleapCallback.getJSONObject("data").getString("articleId");
                                        Gleap.getInstance().openNewsArticle(articleId, true);
                                    }catch (Exception exp) {}
                                    break;
                                case "show-help-article":
                                    try {
                                        String articleId = gleapCallback.getJSONObject("data").getString("articleId");
                                        Gleap.getInstance().openHelpCenterArticle(articleId, true);
                                    }catch (Exception exp) {}
                                    break;
                                case "banner-height":
                                    try {
                                        int minHeight = gleapCallback.getJSONObject("data").getInt("height");
                                        updateMinHeight(minHeight);
                                    } catch (Exception exp) {}
                                    break;
                            }
                        } catch (Exception err) {
                            System.out.println(err);
                        }
                    }
                });
            }
        }

        private void openExternalURL(String url) {
            try {
                Activity local = ActivityUtil.getCurrentActivity();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                local.startActivity(browserIntent);
            } catch (Exception e) {
            }
        }

        private void updateMinHeight(int minHeight) {
            Activity local = ActivityUtil.getCurrentActivity();
            ViewGroup.LayoutParams webViewParams = webView.getLayoutParams();
            webViewParams.height = convertDpToPixel(minHeight, local);
            webView.setLayoutParams(webViewParams);
        }

        private void showWebView() {
            GleapInvisibleActivityManger.animateViewInOut(getComponent(), true);
        }

        private void sendBannerData() {
            try {
                sendMessage(generateGleapMessage("banner-data", bannerData));
            } catch (Exception err) {
            }
        }
    }

    /**
     * Send message to JS
     *
     * @param message
     */
    public void sendMessage(String message) {
        if (webView != null) {
            webView.evaluateJavascript("window.appMessage(" + message + ");", null);
        }
    }

    private String generateGleapMessage(String name, JSONObject data) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("name", name);
        message.put("data", data);

        return message.toString();
    }

    private class GleapWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                if (!url.contains(bannerUrl)) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    if (browserIntent.resolveActivity(parentActivity.getPackageManager()) != null) {
                        parentActivity.startActivity(browserIntent);
                    }
                    return true;
                }
            } catch (Error | Exception ignore) {
            }
            return false;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
        }

        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            if (layout != null) {
                layout.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
        }
    }

    private class GleapBannerWebChromeClient extends WebChromeClient {
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

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return true;
        }
    }
}