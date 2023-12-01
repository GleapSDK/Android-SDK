package io.gleap;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

import gleap.io.gleap.R;

public class GleapMainActivity extends AppCompatActivity implements OnHttpResponseListener {
    public static boolean isActive = false;
    public static WeakReference<Activity> callerActivity;
    private WebView webView;
    private OnBackPressedCallback onBackPressedCallback;
    private String url = GleapConfig.getInstance().getiFrameUrl();
    public static final int REQUEST_SELECT_FILE = 100;
    private Runnable exitAfterFifteenSeconds;
    private Handler handler;
    private ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult activityResult) {
                    if (activityResult.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent intent = activityResult.getData();
                        ValueCallback<Uri[]> mUploadMessage = GleapConfig.getInstance().getmUploadMessage();
                        if (mUploadMessage == null || intent == null) {
                            return;
                        }

                        Uri[] result = null;
                        String dataString = intent.getDataString();

                        if (dataString != null) {
                            result = new Uri[]{Uri.parse(dataString)};
                        }

                        mUploadMessage.onReceiveValue(result);
                        GleapConfig.getInstance().setmUploadMessage(null);
                    }
                }
            });

    @Override
    public void onBackPressed() {
        if (onBackPressedCallback == null) {
            GleapDetectorUtil.resumeAllDetectors();
        }
        super.onBackPressed();
    }

    public void closeMainGleapActivity() {
        Activity mainActivity = GleapMainActivity.callerActivity.get();
        if (mainActivity != null) {
            try {
                isActive = false;

                PackageManager pm = mainActivity.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(mainActivity.getComponentName(), 0);

                if (info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                    // The main activity is singleInstance, proceed with navigation
                    Intent intentToMain = new Intent(this, mainActivity.getClass());
                    intentToMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intentToMain);
                    finish();
                } else {
                    finish();
                }
            } catch (Exception e) {
                e.printStackTrace();
                finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isActive = true;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                onBackPressedCallback = new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        GleapDetectorUtil.resumeAllDetectors();
                        closeMainGleapActivity();
                    }
                };

                getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
            }

            this.requestWindowFeature(Window.FEATURE_NO_TITLE);
            try {
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
            } catch (Exception ex) {
            }
            super.onCreate(savedInstanceState);
            GleapInvisibleActivityManger.getInstance().clearMessages();
            GleapBug.getInstance().setLanguage(Locale.getDefault().getLanguage());

            url += GleapURLGenerator.generateURL();

            setContentView(R.layout.activity_gleap_main);

            if (getPackageManager().hasSystemFeature("android.software.webview")) {
                webView = findViewById(R.id.gleap_webview);

                int backgroundColor = Color.parseColor(GleapConfig.getInstance().getBackgroundColor());
                int headerColor = Color.parseColor(GleapConfig.getInstance().getHeaderColor());
                int[] gradientColors = new int[]{headerColor, headerColor, headerColor, backgroundColor};
                GradientDrawable gradientDrawable = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        gradientColors
                );

                gradientDrawable.setCornerRadius(0f);

                findViewById(R.id.gleap_progressBarHeader).setBackground(gradientDrawable);

                exitAfterFifteenSeconds = new Runnable() {
                    @Override
                    public void run() {
                        if (webView.getVisibility() == View.INVISIBLE) {
                            closeMainGleapActivity();
                        }
                    }
                };

                //if it is no survey preload with shadow
                boolean isSurvey = getIntent().getBooleanExtra("IS_SURVEY", false);
                if (isSurvey) {
                    findViewById(R.id.loader).setVisibility(View.INVISIBLE);
                } else {
                    findViewById(R.id.loader).setBackgroundColor(backgroundColor);
                }

                this.handler = new Handler(Looper.getMainLooper());
                this.handler.postDelayed(exitAfterFifteenSeconds, 15000);

                GleapConfig.getInstance().setCallCloseCallback(new CallCloseCallback() {
                    @Override
                    public void invoke() {
                        GleapDetectorUtil.resumeAllDetectors();
                        GleapBug.getInstance().setDisabled(false);
                        GleapInvisibleActivityManger.getInstance().setShowFab(true);
                        GleapMainActivity.this.closeMainGleapActivity();
                    }
                });

                if (savedInstanceState == null) {
                    initBrowser();
                }
            }
        } catch (Exception ex) {
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        GleapDetectorUtil.resumeAllDetectors();
        GleapConfig.getInstance().setAction(null);
        if (GleapConfig.getInstance().getWidgetClosedCallback() != null) {
            GleapConfig.getInstance().getWidgetClosedCallback().invoke();
        }

        GleapInvisibleActivityManger.getInstance().clearMessages();
        GleapInvisibleActivityManger.getInstance().setShowFab(true);
        GleapConfig.getInstance().setmUploadMessage(null);

        isActive = false;
        webView.removeJavascriptInterface("GleapJSBridge");
        webView.stopLoading();
        webView.clearHistory();
        webView.clearCache(true);
        webView.onPause();
        webView.removeAllViews();
        webView.destroyDrawingCache();
        webView.destroy();
        webView = null;

        if (openFileLauncher != null) {
            openFileLauncher.unregister();
            openFileLauncher = null;
        }

        if (onBackPressedCallback != null) {
            onBackPressedCallback.remove();
            onBackPressedCallback = null;
        }

        GleapConfig.getInstance().setCallCloseCallback(null);

        if (this.exitAfterFifteenSeconds != null) {
            this.handler.removeCallbacks(this.exitAfterFifteenSeconds);
            this.exitAfterFifteenSeconds = null;
        }
        this.handler = null;

        callerActivity.clear();

        super.onDestroy();
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
    public void onTaskComplete(JSONObject response) {
        try {
            if (response.has("status") && response.getInt("status") == 201) {
                try {
                    JSONObject message = new JSONObject();
                    String shareToken = getShareToken(response);
                    if (!shareToken.equals("")) {
                        message.put("shareToken", shareToken);

                    }

                    sendMessage(generateGleapMessage("feedback-sent", message));
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
        } catch (Exception ignore) {
        }
    }

    private class GleapWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                if (!url.contains(GleapConfig.getInstance().getiFrameUrl())) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    if (browserIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(browserIntent);
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
            webView.setVisibility(View.GONE);

            AlertDialog alertDialog = new AlertDialog.Builder(GleapMainActivity.this).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (GleapConfig.getInstance().getWidgetClosedCallback() != null) {
                        GleapConfig.getInstance().getWidgetClosedCallback().invoke();
                    }
                    GleapDetectorUtil.resumeAllDetectors();
                    closeMainGleapActivity();

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

        }
    }

    private class GleapWebChromeClient extends WebChromeClient {

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            try {
                ValueCallback<Uri[]> mUploadMessage = GleapConfig.getInstance().getmUploadMessage();

                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }

                GleapConfig.getInstance().setmUploadMessage(filePathCallback);
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*"); // set MIME type to filter
                openFileLauncher.launch(i);

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

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return true;
        }
    }

    private class GleapJSBridge {
        private final WeakReference<AppCompatActivity> mContextRef;

        public GleapJSBridge(AppCompatActivity c) {
            mContextRef = new WeakReference<>(c);
        }

        @JavascriptInterface
        public void gleapCallback(String object) {
            if (this.mContextRef.get() == null) {
                return;
            }

            this.mContextRef.get().runOnUiThread(new Runnable() {
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
                                    }
                                }, 100);
                                webView.setVisibility(View.VISIBLE);

                                break;
                            case "cleanup-drawings":
                                GleapBug.getInstance().setScreenshot(null);
                                break;
                            case "collect-ticket-data":
                                try {
                                    GleapBug gleapBug = GleapBug.getInstance();

                                    JSONObject data = new JSONObject();
                                    data.put("customData", gleapBug.getCustomData());
                                    data.put("networkLogs", gleapBug.getNetworklogs());
                                    data.put("customEventLog", gleapBug.getCustomEventLog());

                                    PhoneMeta phoneMeta = gleapBug.getPhoneMeta();
                                    if (phoneMeta != null) {
                                        data.put("metaData", phoneMeta.getJSONObj());
                                    }

                                    if (GleapConfig.getInstance().isEnableConsoleLogs()) {
                                        data.put("consoleLog", gleapBug.getLogs());
                                    }

                                    try {
                                        data.put("tags", new JSONArray(gleapBug.getTags()));
                                    } catch (Exception ex) {
                                    }

                                    sendMessage(generateGleapMessage("collect-ticket-data", data));
                                } catch (Error | Exception ignore) {

                                }
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
            List<GleapAction> queue = GleapActionQueueHandler.getInstance().getActionQueue();
            for (GleapAction action :
                    queue) {
                try {
                    sendMessage(generateGleapMessage(action.getCommand(), action.getData()));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            List<GleapWebViewMessage> messages = GleapConfig.getInstance().getGleapWebViewMessages();
            for (GleapWebViewMessage message :
                    messages) {
                sendMessage(message.getMessage());
            }
            GleapActionQueueHandler.getInstance().clearActionMessageQueue();
            GleapConfig.getInstance().clearGleapWebViewMessages();
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
            if (this.mContextRef.get() == null) {
                return;
            }

            this.mContextRef.get().runOnUiThread(new Runnable() {
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

                        if (gleapUserProperties.getPhone() != null) {
                            sessionData.put("phone", gleapUserProperties.getPhone());
                        }

                        if (gleapUserProperties.getCompanyName() != null) {
                            sessionData.put("companyName", gleapUserProperties.getCompanyName());
                        }

                        if (gleapUserProperties.getPlan() != null) {
                            sessionData.put("plan", gleapUserProperties.getPlan());
                        }

                        if (gleapUserProperties.getCompanyId() != null) {
                            sessionData.put("companyId", gleapUserProperties.getCompanyId());
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
            closeMainGleapActivity();
        }
    }

    /**
     * Send message to JS
     *
     * @param message
     */
    public void sendMessage(String message) {
        if (webView != null) {
            webView.evaluateJavascript("sendMessage(" + message + ");", null);
        }
    }

    private String generateGleapMessage(String name, JSONObject data) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("name", name);
        message.put("data", data);

        return message.toString();
    }

    private String getShareToken(JSONObject httpResponse) {
        try {

            if (httpResponse.has("response")) {
                JSONObject response = httpResponse.getJSONObject("response");
                if (response.has("shareToken")) {
                    return response.getString("shareToken");
                }
            }
        } catch (Exception ignore) {
        }


        return "";
    }
}