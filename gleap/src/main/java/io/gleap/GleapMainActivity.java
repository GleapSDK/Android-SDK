package io.gleap;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
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
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.List;

import gleap.io.gleap.R;

public class GleapMainActivity extends AppCompatActivity implements OnHttpResponseListener {
    public static boolean isActive = false;
    public static WeakReference<Activity> callerActivity;
    private WebView webView;
    private OnBackPressedCallback onBackPressedCallback;
    private String url = GleapConfig.getInstance().getiFrameUrl();
    private static String urlToOpenAfterClose = null;
    public static final int REQUEST_SELECT_FILE = 100;
    private Runnable exitAfterFifteenSeconds;
    private Handler handler;
    private PermissionRequest permissionRequest;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 101;
    private static final int PERMISSIONS_REQUEST_RECORD_VIDEO = 102;
    private ValueCallback<Uri[]> fileChooserCallback;
    
    private java.util.Queue<PermissionQueueItem> permissionQueue = new java.util.LinkedList<>();
    private boolean isProcessingPermission = false;
    private java.util.List<String> grantedWebkitPermissions = new java.util.ArrayList<>();
    private boolean isSurvey = false;
    
    private static class PermissionQueueItem {
        String origin;
        String androidPermission;
        String webkitPermission;
        int requestCode;
        
        PermissionQueueItem(String origin, String androidPermission, String webkitPermission, int requestCode) {
            this.origin = origin;
            this.androidPermission = androidPermission;
            this.webkitPermission = webkitPermission;
            this.requestCode = requestCode;
        }
    }

    // Register the ActivityResultLauncher at the class level
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (fileChooserCallback == null) return;

                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    if (selectedImage != null) {
                        fileChooserCallback.onReceiveValue(new Uri[]{selectedImage});
                    } else {
                        fileChooserCallback.onReceiveValue(null); // No file selected
                    }
                } else {
                    fileChooserCallback.onReceiveValue(null); // Handle cancellation or errors
                }
                fileChooserCallback = null; // Reset callback after use
            }
    );

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
        if (GleapMainActivity.callerActivity == null) {
            return;
        }

        Activity mainActivity = GleapMainActivity.callerActivity.get();
        if (mainActivity != null) {
            try {
                PackageManager pm = mainActivity.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(mainActivity.getComponentName(), 0);

                if (info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                    // The main activity is singleInstance, proceed with navigation
                    Intent intentToMain = new Intent(this, mainActivity.getClass());
                    intentToMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intentToMain);
                }

                GleapInvisibleActivityManger.getInstance().setShowFab(true);

                finish();

                if (GleapMainActivity.urlToOpenAfterClose != null) {
                    Gleap.getInstance().handleLink(GleapMainActivity.urlToOpenAfterClose);
                    GleapMainActivity.urlToOpenAfterClose = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                GleapInvisibleActivityManger.getInstance().setShowFab(true);
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

            setContentView(R.layout.activity_gleap_main);

            if (getPackageManager().hasSystemFeature("android.software.webview")) {
                webView = findViewById(R.id.gleap_webview);

                final FrameLayout webViewContainer = findViewById(R.id.webview_container);

                ViewCompat.setOnApplyWindowInsetsListener(webViewContainer,
                    (view, insets) -> {

                    // status + navigation bars (stable system bars)
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                    // on Android 11+ this is the real keyboard height;
                    // on older versions it’s 0 – but then the IME is reported as a system‑bar inset
                    Insets ime  = insets.getInsets(WindowInsetsCompat.Type.ime());

                    int topInset    = bars.top;                       // keep the status‑bar height
                    int bottomInset = Math.max(bars.bottom, ime.bottom); // choose the larger of nav‑bar or IME

                    view.setPadding(view.getPaddingLeft(),
                                    topInset,
                                    view.getPaddingRight(),
                                    bottomInset);

                    return insets;   // DON’T consume – let child views see the same insets
                });

                int backgroundColor = Color.parseColor(GleapConfig.getInstance().getBackgroundColor());
                int headerColor = Color.parseColor(GleapConfig.getInstance().getHeaderColor());
                int[] gradientColors = new int[]{headerColor, headerColor, headerColor, backgroundColor};
                GradientDrawable gradientDrawable = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        gradientColors
                );

                gradientDrawable.setCornerRadius(0f);

                View progressHeaderView = findViewById(R.id.gleap_progressBarHeader);
                progressHeaderView.setBackground(gradientDrawable);

                exitAfterFifteenSeconds = new Runnable() {
                    @Override
                    public void run() {
                        if (webView.getVisibility() == View.INVISIBLE) {
                            closeMainGleapActivity();
                        }
                    }
                };

                isSurvey = getIntent().getBooleanExtra("IS_SURVEY", false);
                View loaderView = findViewById(R.id.loader);
                loaderView.setVisibility(View.VISIBLE);
                if (isSurvey) {
                    progressHeaderView.setVisibility(View.GONE);
                    loaderView.setBackgroundColor(Color.parseColor("#66000000"));
                } else {
                    progressHeaderView.setVisibility(View.VISIBLE);
                    loaderView.setBackgroundColor(backgroundColor);
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

                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        url += GleapURLGenerator.generateURL();
                        initBrowser();
                        if (savedInstanceState != null) {
                            webView.restoreState(savedInstanceState);
                        }
                    }
                });
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
        try {
            GleapDetectorUtil.resumeAllDetectors();
            GleapConfig.getInstance().setAction(null);
            if (GleapConfig.getInstance().getWidgetClosedCallback() != null) {
                GleapConfig.getInstance().getWidgetClosedCallback().invoke();
            }

            GleapInvisibleActivityManger.getInstance().setShowFab(true);
            GleapInvisibleActivityManger.getInstance().clearMessages();
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

            if (callerActivity != null && callerActivity.get() != null) {
                callerActivity.clear();
            }

        } catch (Error | Exception ignore) {}

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
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                permissionRequest = request;
                grantedWebkitPermissions.clear();

                for (String permission : request.getResources()) {
                    switch (permission) {
                        case "android.webkit.resource.AUDIO_CAPTURE": {
                            askForPermission(request.getOrigin().toString(), Manifest.permission.RECORD_AUDIO, permission, PERMISSIONS_REQUEST_RECORD_AUDIO);
                            break;
                        }
                        case "android.webkit.resource.VIDEO_CAPTURE": {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                askForPermission(request.getOrigin().toString(), Manifest.permission.CAMERA, permission, PERMISSIONS_REQUEST_RECORD_VIDEO);
                            } else {
                                grantedWebkitPermissions.add(permission);
                            }
                            break;
                        }
                        // Grant access to file storage permissions
                        case "android.webkit.resource.PROTECTED_MEDIA_ID":
                        case "android.webkit.resource.MIDIDEVICES":
                            permissionRequest.grant(new String[]{permission});
                            break;
                        default:
                            // We'll allow other permissions by default to enable file access
                            permissionRequest.grant(new String[]{permission});
                    }
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // Save the callback for use after file selection
                fileChooserCallback = filePathCallback;

                // Check for Android 13+ (API 33)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false); // Single selection
                    try {
                        imagePickerLauncher.launch(intent);
                        return true;
                    } catch (ActivityNotFoundException e) {
                        fileChooserCallback = null; // Reset callback on failure
                        return false;
                    }
                } else {
                    // Fallback for older Android versions
                    try {
                        ValueCallback<Uri[]> mUploadMessage = GleapConfig.getInstance().getmUploadMessage();

                        if (mUploadMessage != null) {
                            mUploadMessage.onReceiveValue(null);
                        }

                        GleapConfig.getInstance().setmUploadMessage(filePathCallback);
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*"); // set MIME type to allow all files
                        openFileLauncher.launch(i);
                        return true;
                    } catch (Exception ex) {
                        return false;
                    }
                }
            }
        });
        webView.loadUrl(url);
        webView.setVisibility(View.INVISIBLE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
    }

    public void askForPermission(String origin, String androidPermission, String webkitPermission, int requestCode) {
        permissionQueue.offer(new PermissionQueueItem(origin, androidPermission, webkitPermission, requestCode));
        processNextPermission();
    }
    
    private void processNextPermission() {
        if (isProcessingPermission || permissionQueue.isEmpty()) {
            return;
        }
        
        PermissionQueueItem item = permissionQueue.poll();
        if (item == null) {
            return;
        }
        
        if (ContextCompat.checkSelfPermission(getApplicationContext(), item.androidPermission)
                != PackageManager.PERMISSION_GRANTED) {
            isProcessingPermission = true;
            ActivityCompat.requestPermissions(GleapMainActivity.this,
                    new String[]{item.androidPermission},
                    item.requestCode);
        } else {
            grantedWebkitPermissions.add(item.webkitPermission);
            processNextPermission();
            
            // If queue is now empty and we're not processing, grant immediately
            if (permissionQueue.isEmpty() && !isProcessingPermission) {
                grantPermissionsIfReady();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        isProcessingPermission = false;
        
        if (permissions.length > 0) {
            
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String webkitPermission = getWebkitPermissionForAndroidPermission(permissions[0]);
                if (webkitPermission != null) {
                    grantedWebkitPermissions.add(webkitPermission);
                }
            }
        }
        
        processNextPermission();
        
        if (permissionQueue.isEmpty() && !isProcessingPermission) {
            grantPermissionsIfReady();
        }
    }
    
    private void grantPermissionsIfReady() {
        if (permissionRequest != null) {
            if (!grantedWebkitPermissions.isEmpty()) {
                permissionRequest.grant(grantedWebkitPermissions.toArray(new String[0]));
            } else {
                permissionRequest.deny();
            }
            permissionRequest = null;
            grantedWebkitPermissions.clear();
        }
    }
    
    private String getWebkitPermissionForAndroidPermission(String androidPermission) {
        switch (androidPermission) {
            case Manifest.permission.RECORD_AUDIO:
                return "android.webkit.resource.AUDIO_CAPTURE";
            case Manifest.permission.CAMERA:
                return "android.webkit.resource.VIDEO_CAPTURE";
            default:
                return null;
        }
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
                                findViewById(R.id.loader).setVisibility(View.GONE);
                                webView.setVisibility(View.VISIBLE);

                                break;
                            case "cleanup-drawings":
                                GleapBug.getInstance().setScreenshot(null);
                                break;
                            case "tool-execution":
                                try {
                                    if (GleapConfig.getInstance().getAiToolExecutedCallback() != null) {
                                        GleapConfig.getInstance().getAiToolExecutedCallback().aiToolExecuted(gleapCallback.getJSONObject("data"));
                                    }
                                } catch (Exception exp) {}
                            case "collect-ticket-data":
                                try {
                                    GleapBug gleapBug = GleapBug.getInstance();

                                    JSONObject data = new JSONObject();
                                    data.put("formData", gleapBug.getTicketAttributes());
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

                    String shareToken = null;
                    if (object.has("shareToken")) {
                        shareToken = object.getString("shareToken");
                    }

                    GleapConfig.getInstance().getCustomActions().invoke(data, shareToken);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void openExternalURL(JSONObject object) {
            try {
                String url = object.getString("data");
                if (url != null && url.length() > 0) {
                    if (Gleap.internalCloseWidgetOnExternalLinkOpen) {
                        GleapMainActivity.urlToOpenAfterClose = url;
                        closeGleap();
                    } else {
                        Gleap.getInstance().handleLink(url);
                    }
                }
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
                                gleapBug.setOutboundId(data.getString("outboundId"));
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
                data.put("aiTools", GleapConfig.getInstance().getAiTools());

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
                GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
                GleapSessionProperties gleapSessionProperties = GleapSessionController.getInstance().getGleapUserSession();
                JSONObject sessionData = new JSONObject();
                sessionData.put("gleapId", gleapSession.getId());
                sessionData.put("gleapHash", gleapSession.getHash());
                if (gleapSessionProperties != null) {
                    if (gleapSessionProperties.getUserId() != null) {
                        sessionData.put("userId", gleapSessionProperties.getUserId());
                    }

                    if (gleapSessionProperties.getName() != null) {
                        sessionData.put("name", gleapSessionProperties.getName());
                    }

                    if (gleapSessionProperties.getEmail() != null) {
                        sessionData.put("email", gleapSessionProperties.getEmail());
                    }

                    sessionData.put("value", gleapSessionProperties.getValue());

                    sessionData.put("sla", gleapSessionProperties.getSla());

                    if (gleapSessionProperties.getPhone() != null) {
                        sessionData.put("phone", gleapSessionProperties.getPhone());
                    }

                    if (gleapSessionProperties.getCompanyName() != null) {
                        sessionData.put("companyName", gleapSessionProperties.getCompanyName());
                    }

                    if (gleapSessionProperties.getAvatar() != null) {
                        sessionData.put("avatar", gleapSessionProperties.getAvatar());
                    }

                    if (gleapSessionProperties.getPlan() != null) {
                        sessionData.put("plan", gleapSessionProperties.getPlan());
                    }

                    if (gleapSessionProperties.getCompanyId() != null) {
                        sessionData.put("companyId", gleapSessionProperties.getCompanyId());
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