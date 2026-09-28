package io.gleap;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

import gleap.io.gleap.R;

/**
 * Shows the widget: the Gleap messenger web app in a WebView on a translucent activity above
 * the app. The widget talks to the SDK through {@link GleapWidgetBridge}; the SDK answers with
 * {@link #sendMessage(String)}.
 */
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
    private boolean isSurvey = false;
    private boolean isImeVisible = false;
    private int lockedScrollY = 0;

    private final GleapWebPermissions webPermissions = new GleapWebPermissions(this);
    private final GleapFileChooser fileChooser = new GleapFileChooser();

    // Register the ActivityResultLaunchers at the class level
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> fileChooser.onImagePicked(result));

    private ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult activityResult) {
                    fileChooser.onFilePicked(activityResult);
                }
            });

    static void setUrlToOpenAfterClose(String url) {
        urlToOpenAfterClose = url;
    }

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

                GleapOverlayManager.getInstance().setShowFab(true);

                finish();

                if (GleapMainActivity.urlToOpenAfterClose != null) {
                    Gleap.getInstance().handleLink(GleapMainActivity.urlToOpenAfterClose);
                    GleapMainActivity.urlToOpenAfterClose = null;
                }
            } catch (Exception e) {
                GleapLog.w("Could not return to the app normally", e);
                GleapOverlayManager.getInstance().setShowFab(true);
                finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isActive = true;

        try {
            setUpBackNavigation();
            setUpWindow();

            super.onCreate(savedInstanceState);
            GleapOverlayManager.getInstance().clearMessages();

            setContentView(R.layout.activity_gleap_main);

            if (getPackageManager().hasSystemFeature("android.software.webview")) {
                webView = findViewById(R.id.gleap_webview);
                setUpInsets((FrameLayout) findViewById(R.id.webview_container));

                exitAfterFifteenSeconds = new Runnable() {
                    @Override
                    public void run() {
                        if (webView.getVisibility() == View.INVISIBLE) {
                            closeMainGleapActivity();
                        }
                    }
                };

                isSurvey = getIntent().getBooleanExtra("IS_SURVEY", false);
                setUpLoader(savedInstanceState);

                this.handler = new Handler(Looper.getMainLooper());
                this.handler.postDelayed(exitAfterFifteenSeconds, 15000);

                GleapCallbacks.getInstance().setCallCloseCallback(new CallCloseCallback() {
                    @Override
                    public void invoke() {
                        GleapDetectorUtil.resumeAllDetectors();
                        GleapOverlayManager.getInstance().setShowFab(true);
                        GleapMainActivity.this.closeMainGleapActivity();
                    }
                });

                if (savedInstanceState == null) {
                    url += GleapURLGenerator.generateURL();
                    initBrowser();
                }
            }
        } catch (Exception ex) {
        }
    }

    private void setUpBackNavigation() {
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
    }

    private void setUpWindow() {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                // Android 16+: ADJUST_NOTHING so the system doesn't resize/pan the
                // translucent activity when the keyboard opens – we handle IME insets
                // ourselves via the WindowInsetsListener below.
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            } else {
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        } catch (Exception ex) {
        }

        if (Build.VERSION.SDK_INT >= 36) {
            // Prevent Android 16's DecorView auto-scroll (ViewRootImpl.scrollToRectOrFocus)
            // which pans the translucent activity upward when the keyboard opens,
            // exposing the host app underneath.
            final View decorView = getWindow().getDecorView();
            decorView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY != 0) {
                    v.scrollTo(scrollX, 0);
                }
            });
        }
    }

    /**
     * Pads the WebView container by the system bars and the keyboard; on Android 16+ also keeps
     * the page from scrolling when the keyboard opens.
     */
    private void setUpInsets(FrameLayout webViewContainer) {
        ViewCompat.setOnApplyWindowInsetsListener(webViewContainer,
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

                    int topInset = bars.top;
                    int bottomInset = Math.max(bars.bottom, ime.bottom);

                    view.setPadding(view.getPaddingLeft(), topInset, view.getPaddingRight(), bottomInset);

                    if (Build.VERSION.SDK_INT >= 36) {
                        boolean nowImeVisible = ime.bottom > 0;
                        if (nowImeVisible && !isImeVisible) {
                            isImeVisible = true;
                            try {
                                lockedScrollY = webView.getScrollY();
                                webView.post(() -> webView.scrollTo(webView.getScrollX(), lockedScrollY));
                            } catch (Exception ignore) {
                            }
                        } else if (!nowImeVisible && isImeVisible) {
                            isImeVisible = false;
                        }
                    }

                    return insets;   // don't consume
                });
    }

    private void setUpLoader(Bundle savedInstanceState) {
        int backgroundColor = Color.parseColor(GleapConfig.getInstance().getBackgroundColor());
        View progressHeaderView = findViewById(R.id.gleap_progressBarHeader);
        FrameLayout loaderView = findViewById(R.id.loader);

        // When the Activity is recreated (e.g. config change, process
        // death) hide the spinner but keep the loader background so
        // the translucent window doesn't expose the host app.
        if (savedInstanceState != null) {
            findViewById(R.id.loading_indicator).setVisibility(View.GONE);
        }

        if (isSurvey) {
            progressHeaderView.setVisibility(View.GONE);
            if (savedInstanceState == null) {
                loaderView.setVisibility(View.VISIBLE);
                loaderView.setBackgroundColor(Color.parseColor("#66000000"));
            }
        } else {
            // Widget loader: mirror the messenger's home background so
            // the reveal is seamless (see GleapLoadingBackgroundView).
            // No spinner — the background itself is the loading
            // indicator, matching the web/iOS SDKs. Also added on
            // recreation: it stays behind the webview as the backdrop.
            progressHeaderView.setVisibility(View.GONE);
            findViewById(R.id.loading_indicator).setVisibility(View.GONE);
            loaderView.setVisibility(View.VISIBLE);
            loaderView.setBackgroundColor(backgroundColor);
            loaderView.addView(new GleapLoadingBackgroundView(this), 0,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * The widget answered its first ping: fade it in over the loader.
     */
    void revealWidget() {
        // Hide only the spinner and header — keep the
        // loader FrameLayout visible as an opaque backdrop
        // so the translucent window doesn't expose the host app.
        // Cross-fade the webview in over the loading
        // background (which shows the same colors/image),
        // so the hand-off reads as continuous — the
        // messenger's own home entrance animations then
        // play inside the webview.
        //
        // The reveal waits 500ms after the ping (same as
        // the iOS SDK): the web app pings BEFORE its
        // first paint, so an immediate fade briefly
        // shows an unpainted webview and the content
        // pops in mid-fade — a visible jump.
        findViewById(R.id.loading_indicator).setVisibility(View.GONE);
        webView.setAlpha(0f);
        GleapMainThread.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (webView == null) {
                    return;
                }
                webView.setVisibility(View.VISIBLE);
                // withLayer(): a hardware-rendered WebView
                // ignores view alpha unless it draws into
                // a layer — without it the "fade" pops in
                // as a single-frame swap.
                webView.animate().alpha(1f).setDuration(300).withLayer().start();
            }
        }, 500);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // webView.restoreState removed – the dynamic web-app JS context
        // is not preserved by restoreState(), so initBrowser() always
        // performs a fresh loadUrl() and restoreState would race with it.
    }

    @Override
    protected void onDestroy() {
        try {
            GleapDetectorUtil.resumeAllDetectors();
            GleapAgentToolManager.getInstance().clearExecutionState();
            if (GleapCallbacks.getInstance().getWidgetClosedCallback() != null) {
                GleapCallbacks.getInstance().getWidgetClosedCallback().invoke();
            }

            GleapOverlayManager.getInstance().setShowFab(true);
            GleapOverlayManager.getInstance().clearMessages();
            GleapConfig.getInstance().setFileUploadCallback(null);

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

            GleapCallbacks.getInstance().setCallCloseCallback(null);

            if (this.exitAfterFifteenSeconds != null) {
                this.handler.removeCallbacks(this.exitAfterFifteenSeconds);
                this.exitAfterFifteenSeconds = null;
            }
            this.handler = null;

            if (callerActivity != null && callerActivity.get() != null) {
                callerActivity.clear();
            }

        } catch (Error | Exception ignore) {
        }

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
        webView.addJavascriptInterface(new GleapWidgetBridge(this), "GleapJSBridge");
        try {
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    @Override
                    public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                        if (isImeVisible && scrollY != lockedScrollY) {
                            webView.scrollTo(scrollX, lockedScrollY);
                        }
                    }
                });
            }
        } catch (Exception ignore) {
        }
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                webPermissions.onPermissionRequest(request);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                return fileChooser.show(filePathCallback, imagePickerLauncher, openFileLauncher);
            }
        });
        webView.loadUrl(url);
        webView.setVisibility(View.INVISIBLE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
    }

    public void askForPermission(String origin, String androidPermission, String webkitPermission, int requestCode) {
        webPermissions.ask(androidPermission, webkitPermission, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        webPermissions.onRequestPermissionsResult(permissions, grantResults);
    }

    @Override
    public void onTaskComplete(JSONObject response) {
        try {
            boolean sent = response.has("status") && response.getInt("status") == 201;
            try {
                sendMessage(GleapWidgetMessages.feedbackResult(response));
                if (sent) {
                    GleapDetectorUtil.resumeAllDetectors();
                    GleapBug.getInstance().setScreenshot(null);
                }
            } catch (Exception ex) {
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
            showNoInternetDialog();
        }

        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showNoInternetDialog();
            }
        }

        private void showNoInternetDialog() {
            webView.setVisibility(View.GONE);

            AlertDialog alertDialog = new AlertDialog.Builder(GleapMainActivity.this).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (GleapCallbacks.getInstance().getWidgetClosedCallback() != null) {
                        GleapCallbacks.getInstance().getWidgetClosedCallback().invoke();
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
    }

    /**
     * Send message to JS
     *
     * @param message the message, a JSON object in the widget's message format
     */
    public void sendMessage(String message) {
        if (webView != null) {
            webView.evaluateJavascript("sendMessage(" + message + ");", null);
        }
    }
}
