package io.gleap;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
    // The open widget, so a color scheme change can reach it.
    private static WeakReference<GleapMainActivity> openInstance;
    private WebView webView;
    private OnBackPressedCallback onBackPressedCallback;
    private String url = GleapConfig.getInstance().getiFrameUrl();
    private static String urlToOpenAfterClose = null;
    public static final int REQUEST_SELECT_FILE = 100;
    // How long the widget may take to load before it gives up and closes.
    private static final long LOAD_TIMEOUT_MS = 30000;
    // The page's own dim behind a card survey (appnew.html: rgba(0,0,0,0.25) on its body).
    private static final int SURVEY_DIM_COLOR = 0x40000000;
    private Runnable exitIfNeverLoaded;
    private Handler handler;
    // A card survey (the floating sheet over the dimmed app).
    private boolean isSurvey = false;
    // A full-screen survey.
    private boolean isFullSurvey = false;
    // A full-screen Surveys 2.0 survey is showing: the page draws behind the system bars and
    // pads itself by the insets it gets (safe-area-update).
    private boolean surveyEdgeToEdge = false;
    // The page reported content (a height or a shown survey): it loads, however slowly.
    private boolean widgetContentSeen = false;
    private FrameLayout webViewContainer;
    private Insets systemBarInsets = Insets.NONE;
    private Insets imeInsets = Insets.NONE;
    private boolean isImeVisible = false;
    private int lockedScrollY = 0;
    // The widget answered its first ping, so it listens for config updates.
    private boolean widgetPinged = false;
    // Closed so the customer can capture the app: to the app the widget stays open (no
    // WidgetClosedCallback), see GleapCaptureCoordinator.
    private boolean closedForCapture = false;

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

    /**
     * Closes the widget. It always finishes, also when the activity that opened it is gone (the
     * app recreated or closed it meanwhile); a singleInstance caller is brought back to the front.
     */
    public void closeMainGleapActivity() {
        Activity mainActivity = GleapMainActivity.callerActivity != null ? GleapMainActivity.callerActivity.get() : null;
        try {
            if (mainActivity != null) {
                PackageManager pm = mainActivity.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(mainActivity.getComponentName(), 0);

                if (info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
                    // The main activity is singleInstance, proceed with navigation
                    Intent intentToMain = new Intent(this, mainActivity.getClass());
                    intentToMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intentToMain);
                }
            }

            GleapOverlayManager.getInstance().setShowFab(true);

            finish();

            if (GleapMainActivity.urlToOpenAfterClose != null) {
                Gleap.getInstance().handleLink(GleapMainActivity.urlToOpenAfterClose);
                GleapMainActivity.urlToOpenAfterClose = null;
            }
        } catch (Exception e) {
            GleapLog.w("Could not return to the app normally", e);
            try {
                GleapOverlayManager.getInstance().setShowFab(true);
            } catch (Exception ignore) {
            }
            finish();
        }
    }

    /**
     * Closes the widget for a capture: like {@link #closeMainGleapActivity()}, without telling
     * the app.
     */
    void closeForCapture() {
        closedForCapture = true;
        closeMainGleapActivity();
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
                webViewContainer = findViewById(R.id.webview_container);
                setUpInsets(webViewContainer);

                // Only a page that never answered and never showed anything is given up on: a
                // slow network may well take longer than a few seconds to load the messenger.
                exitIfNeverLoaded = new Runnable() {
                    @Override
                    public void run() {
                        if (!widgetPinged && !widgetContentSeen && webView != null
                                && webView.getVisibility() != View.VISIBLE) {
                            closeMainGleapActivity();
                        }
                    }
                };

                isSurvey = getIntent().getBooleanExtra("IS_SURVEY", false);
                isFullSurvey = getIntent().getBooleanExtra("IS_SURVEY_FULL", false);
                setUpLoader(savedInstanceState);

                this.handler = new Handler(Looper.getMainLooper());
                this.handler.postDelayed(exitIfNeverLoaded, LOAD_TIMEOUT_MS);

                GleapCallbacks.getInstance().setCallCloseCallback(new CallCloseCallback() {
                    @Override
                    public void invoke() {
                        GleapDetectorUtil.resumeAllDetectors();
                        GleapOverlayManager.getInstance().setShowFab(true);
                        GleapMainActivity.this.closeMainGleapActivity();
                    }
                });

                if (savedInstanceState != null && !GleapWidgetLauncher.isGleapReady()) {
                    // Recreated after the process was restarted: the SDK has not loaded again
                    // yet, so there is no widget to show.
                    finish();
                    return;
                }

                // Also after the activity was recreated (font size, language or window size
                // changed; dark mode is handled in onConfigurationChanged): the widget loads
                // again, a WebView cannot keep its page across the recreation.
                url += GleapURLGenerator.generateURL();
                initBrowser();

                openInstance = new WeakReference<>(this);
            }
        } catch (Exception ex) {
            GleapLog.w("Could not show the widget", ex);
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
                    boolean changed = !bars.equals(systemBarInsets) || !ime.equals(imeInsets);
                    systemBarInsets = bars;
                    imeInsets = ime;

                    if (surveyEdgeToEdge) {
                        // Behind the system bars; only the keyboard takes room away.
                        view.setPadding(view.getPaddingLeft(), 0, view.getPaddingRight(), ime.bottom);
                        if (changed) {
                            sendSafeAreaInsets();
                        }
                    } else {
                        int topInset = bars.top;
                        int bottomInset = Math.max(bars.bottom, ime.bottom);
                        view.setPadding(view.getPaddingLeft(), topInset, view.getPaddingRight(), bottomInset);
                    }
                    // The card survey's dim bands follow the padding.
                    view.invalidate();

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
        // Read the night mode from this activity: the host activity is
        // already paused, and this one reflects the app's night mode too.
        GleapThemeHelper.getInstance().checkNightMode(this);
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
            // Once the survey shows, the page dims the app itself; the container keeps clear of
            // the status bar, the navigation bar and the keyboard, and those bands get the same
            // dim (see revealWidget). The loader's darker dim stayed in them: two tones.
            webViewContainer.setBackground(new InsetBandsDrawable(webViewContainer, SURVEY_DIM_COLOR));
            webViewContainer.getBackground().setAlpha(0);
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
        widgetPinged = true;
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
                // Closed meanwhile (e.g. a survey with nothing to ask closes right after
                // the ping): no reveal of an empty page or the dim while it goes.
                if (webView == null || isFinishing()) {
                    return;
                }
                webView.setVisibility(View.VISIBLE);
                // withLayer(): a hardware-rendered WebView
                // ignores view alpha unless it draws into
                // a layer — without it the "fade" pops in
                // as a single-frame swap.
                webView.animate().alpha(1f).setDuration(300).withLayer().start();
                if (isSurvey) {
                    revealSurveyDim();
                }
            }
        }, 500);
    }

    /**
     * Card surveys: the loader's dim gives way to the page's own, and the bands outside the web
     * view (system bars, keyboard) get the same dim, so the whole screen is dimmed evenly.
     */
    private void revealSurveyDim() {
        final View loaderView = findViewById(R.id.loader);
        if (loaderView != null) {
            loaderView.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override
                public void run() {
                    loaderView.setVisibility(View.GONE);
                }
            }).start();
        }
        if (webViewContainer != null && webViewContainer.getBackground() != null) {
            webViewContainer.getBackground().setAlpha(255);
            webViewContainer.invalidate();
        }
    }

    /** The page reported content: it is loading, so the load timeout no longer applies. */
    void onWidgetContent() {
        widgetContentSeen = true;
    }

    /**
     * A Surveys 2.0 survey is showing. Full screen, it paints its own backdrop: the page goes
     * behind the status and navigation bars and pads its close button, question and bar by the
     * insets (the bars showed the loader's header color over the pale survey page before).
     */
    void onSurveyShown(JSONObject data) {
        if (!isFullSurvey || isSurvey || surveyEdgeToEdge) {
            return;
        }
        if (data == null || !"full".equals(data.optString("format", ""))) {
            return;
        }
        try {
            surveyEdgeToEdge = true;
            Window window = getWindow();
            WindowCompat.setDecorFitsSystemWindows(window, false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            // The survey's backdrop follows the widget's background (light or dark mode).
            boolean light = isLightColor(GleapConfig.getInstance().getBackgroundColor());
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
            controller.setAppearanceLightStatusBars(light);
            controller.setAppearanceLightNavigationBars(light);
            sendSafeAreaInsets();
            ViewCompat.requestApplyInsets(webViewContainer);
        } catch (Error | Exception error) {
            GleapLog.w("Could not show the survey edge to edge", error);
        }
    }

    /** The survey falls back to the legacy flow: back to the layout below the system bars. */
    void onSurveyLegacy() {
        if (!surveyEdgeToEdge) {
            return;
        }
        surveyEdgeToEdge = false;
        try {
            sendSafeAreaInsets();
            WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
            ViewCompat.requestApplyInsets(webViewContainer);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * The insets the page pads itself by while it draws behind the system bars (CSS px; zero
     * otherwise). The bottom one is 0 while the keyboard is up: the web view ends above it.
     */
    private void sendSafeAreaInsets() {
        try {
            float density = getResources().getDisplayMetrics().density;
            Insets bars = surveyEdgeToEdge ? systemBarInsets : Insets.NONE;
            JSONObject data = new JSONObject();
            data.put("top", Math.round(bars.top / density));
            data.put("right", Math.round(bars.right / density));
            data.put("bottom", imeInsets.bottom > 0 ? 0 : Math.round(bars.bottom / density));
            data.put("left", Math.round(bars.left / density));
            sendMessage(GleapWidgetMessages.message("safe-area-update", data));
        } catch (Error | Exception ignore) {
        }
    }

    static boolean isLightColor(String color) {
        try {
            int value = Color.parseColor(color);
            double luminance = (0.299 * Color.red(value) + 0.587 * Color.green(value) + 0.114 * Color.blue(value)) / 255.0;
            return luminance > 0.5;
        } catch (Exception ignore) {
            return true;
        }
    }

    /**
     * Paints only a view's padding (the bands it keeps clear of the system bars and the
     * keyboard), never behind its content.
     */
    static final class InsetBandsDrawable extends Drawable {
        private final View view;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;

        InsetBandsDrawable(View view, int color) {
            this.view = view;
            this.color = color;
            paint.setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            int top = view.getPaddingTop();
            int bottom = view.getPaddingBottom();
            int left = view.getPaddingLeft();
            int right = view.getPaddingRight();
            if (top > 0) {
                canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + top, paint);
            }
            if (bottom > 0) {
                canvas.drawRect(bounds.left, bounds.bottom - bottom, bounds.right, bounds.bottom, paint);
            }
            if (left > 0) {
                canvas.drawRect(bounds.left, bounds.top + top, bounds.left + left, bounds.bottom - bottom, paint);
            }
            if (right > 0) {
                canvas.drawRect(bounds.right - right, bounds.top + top, bounds.right, bounds.bottom - bottom, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setColor(color);
            paint.setAlpha(Color.alpha(color) * alpha / 255);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // uiMode is handled here (see the manifest) instead of recreating the
        // activity, so a night mode switch re-themes the open widget live.
        GleapThemeHelper.getInstance().checkNightMode(this);
    }

    /**
     * Pushes the color scheme to the open widget: sends the themed config and
     * updates the loading background behind it. No-op when no widget is open.
     */
    static void refreshColorScheme() {
        final GleapMainActivity activity = openInstance != null ? openInstance.get() : null;
        if (activity == null || activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    activity.applyLoaderBackground();
                    // Before the first ping the widget isn't listening yet —
                    // the ping sends the config anyway.
                    if (activity.widgetPinged) {
                        GleapWidgetBridge.sendConfigUpdate(activity);
                    }
                } catch (Error | Exception ignore) {
                }
            }
        });
    }

    /**
     * Pushes the session to the open widget (session-update), e.g. a refreshed file session.
     * No-op when no widget is open.
     */
    static void refreshSession() {
        final GleapMainActivity activity = openInstance != null ? openInstance.get() : null;
        if (activity == null || activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Before the first ping the widget isn't listening yet —
                    // the ping sends the session anyway.
                    if (activity.widgetPinged) {
                        GleapWidgetBridge.sendSessionUpdate(activity);
                    }
                } catch (Error | Exception ignore) {
                }
            }
        });
    }

    private void applyLoaderBackground() {
        if (isSurvey) {
            return;
        }

        FrameLayout loaderView = findViewById(R.id.loader);
        if (loaderView == null) {
            return;
        }

        try {
            loaderView.setBackgroundColor(Color.parseColor(GleapConfig.getInstance().getBackgroundColor()));
        } catch (Exception ignore) {
        }

        // The loading background reads its colors and header image once — replace it.
        if (loaderView.getChildCount() > 0 && loaderView.getChildAt(0) instanceof GleapLoadingBackgroundView) {
            loaderView.removeViewAt(0);
            loaderView.addView(new GleapLoadingBackgroundView(this), 0,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // No WebView when the device has none or it failed to load.
        if (webView != null) {
            webView.saveState(outState);
        }
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
        // Recreated for a configuration change: the new instance shows the widget again, so the
        // widget stays open for the app (no WidgetClosed, activation methods stay paused).
        boolean recreating = isChangingConfigurations();
        if (openInstance != null && openInstance.get() == this) {
            openInstance = null;
        }
        try {
            GleapAgentToolManager.getInstance().clearExecutionState();
            GleapConfig.getInstance().setFileUploadCallback(null);
            if (!recreating) {
                GleapDetectorUtil.resumeAllDetectors();
                if (!closedForCapture && GleapCallbacks.getInstance().getWidgetClosedCallback() != null) {
                    GleapCallbacks.getInstance().getWidgetClosedCallback().invoke();
                }

                GleapOverlayManager.getInstance().setShowFab(true);
                GleapOverlayManager.getInstance().clearMessages();
                isActive = false;
                if (!closedForCapture) {
                    // In-app messages held back during a capture show once the widget is closed.
                    GleapMainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            GleapEventService.getInstance().processDeferredActions();
                        }
                    });
                }
            }
        } catch (Error | Exception ignore) {
        }

        try {
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

            if (!recreating) {
                GleapCallbacks.getInstance().setCallCloseCallback(null);
            }

            if (this.exitIfNeverLoaded != null) {
                this.handler.removeCallbacks(this.exitIfNeverLoaded);
                this.exitIfNeverLoaded = null;
            }
            this.handler = null;

            if (!recreating && callerActivity != null && callerActivity.get() != null) {
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
            boolean sent = HttpHelper.isSent(response);
            try {
                sendMessage(GleapWidgetMessages.feedbackResult(response));
                if (sent) {
                    // The widget stays open (it shows the thank-you screen), so it
                    // stays marked open: the detectors resume when the activity
                    // actually closes. Resuming them here made isOpened() false
                    // and Gleap.close() a no-op while the widget was on screen.
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
            return GleapExternalLinks.openOutside(GleapMainActivity.this, url, GleapConfig.getInstance().getiFrameUrl());
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

    /**
     * Whether the WebView shows the Gleap messenger (the widget url's scheme, host and port).
     * Capture commands are only taken from it. Main thread only.
     */
    boolean isTrustedWidgetPage() {
        try {
            return webView != null && !isFinishing()
                    && GleapCapture.sameOrigin(webView.getUrl(), GleapConfig.getInstance().getiFrameUrl());
        } catch (Throwable error) {
            return false;
        }
    }

    /**
     * Sends a message to the open widget, if one is open, answered its ping and shows the
     * messenger. Main thread only.
     *
     * @return whether it was sent
     */
    static boolean deliverToOpenWidget(String message) {
        GleapMainActivity activity = openInstance != null ? openInstance.get() : null;
        if (activity == null || !activity.widgetPinged || !activity.isTrustedWidgetPage()) {
            return false;
        }
        activity.sendMessage(message);
        return true;
    }
}
