package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.view.WindowInsets;
import android.view.WindowMetrics;

import androidx.cardview.widget.CardView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Gleap modal that shows a WebView inside a rounded CardView.
 * <p>
 * **Fix #4 – align WebView & clipper heights**<br>
 * CardViewʼs <code>useCompatPadding</code> adds extra internal padding which
 * made the WebView appear "longer" than the clipped frame.  We disable that
 * padding and rely entirely on the clipperʼs outline for rounded corners.
 */
class GleapModal {

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------
    private final Activity parentActivity;
    private JSONObject modalData;
    private final String modalUrl = "https://outboundmedia.gleap.io/modal";
    private WebView webView;
    private LinearLayout backdrop;
    private CardView cardView;
    private FrameLayout clipper; // container that controls visible height

    private static final int MAX_LANDSCAPE_WIDTH_DP = 400;
    private int maxAllowedHeightPx; // recalculated after every rotation

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------
    GleapModal(JSONObject data, Activity activity) {
        this.modalData = data;
        this.parentActivity = activity;
        this.backdrop = buildBackdrop();
    }

    LinearLayout getComponent() { return backdrop; }

    void clearComponent() {
        if (webView != null) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.onPause();
            webView.removeAllViews();
            webView.destroy();
        }
        backdrop = null;
        webView  = null;
        modalData = null;
    }

    // ------------------------------------------------------------------
    // Construction helpers
    // ------------------------------------------------------------------
    private LinearLayout buildBackdrop() {
        LinearLayout container = new LinearLayout(parentActivity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.TRANSPARENT);
        container.setFitsSystemWindows(true);
        container.setClickable(true);
        container.setId(View.generateViewId());
        container.setVisibility(View.GONE);
        container.setOnClickListener(v -> {
            // Check if we need to restore background and feedback button visibility
            boolean canCloseModal = true;
            
            try {
                if (this.modalData != null) {
                    canCloseModal = modalData.optBoolean("showCloseButton", true);
                }
            } catch (Exception e) {
                // Default to false if there's an error
            }
            
            if (canCloseModal) {
                GleapInvisibleActivityManger.getInstance().destroyModal(true, false);
            }
        });

        parentActivity.runOnUiThread(() -> buildCard(container));
        return container;
    }

    private void buildCard(LinearLayout container) {
        // ---------------- WebView --------------------------------------
        webView = new WebView(parentActivity);
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setDefaultTextEncodingName("utf-8");

        webView.addJavascriptInterface(new GleapModalJSBridge(), "GleapModalJSBridge");
        webView.setWebChromeClient(new GleapModalWebChromeClient());
        webView.setWebViewClient(new GleapModalWebViewClient());
        webView.loadUrl(modalUrl);

        // ---------------- Clipper (FrameLayout) ------------------------
        clipper = new FrameLayout(parentActivity);
        float radiusPx = convertDpToPixel(20, parentActivity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clipper.setClipToOutline(true);
            clipper.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                }
            });
        }

        maxAllowedHeightPx = calcMaxAllowedHeight();

        FrameLayout.LayoutParams clipLP = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxAllowedHeightPx);
        clipper.setLayoutParams(clipLP);

        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        clipper.addView(webView);

        // ---------------- CardView -------------------------------------
        cardView = new CardView(parentActivity);
        cardView.setCardBackgroundColor(Color.WHITE);
        cardView.setRadius(radiusPx);
        cardView.setCardElevation(convertDpToPixel(8, parentActivity));
        cardView.setPreventCornerOverlap(false);   // no extra inner padding
        cardView.setUseCompatPadding(false);       // **FIX** – removes extra padding
        cardView.setClickable(true);
        cardView.addView(clipper);

        boolean landscape = isLandscape();
        LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
                landscape ? convertDpToPixel(MAX_LANDSCAPE_WIDTH_DP, parentActivity) : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = convertDpToPixel(20, parentActivity);
        cardLP.setMargins(m, m, m, m);
        cardLP.gravity = Gravity.CENTER;
        cardView.setLayoutParams(cardLP);

        container.addView(cardView);
    }

    // ------------------------------------------------------------------
    // Orientation adjustments
    // ------------------------------------------------------------------
    void adjustForOrientation() {
        if (cardView == null) return;

        boolean landscape = isLandscape();
        LinearLayout.LayoutParams cardLP = (LinearLayout.LayoutParams) cardView.getLayoutParams();
        cardLP.width = landscape ? convertDpToPixel(MAX_LANDSCAPE_WIDTH_DP, parentActivity) : ViewGroup.LayoutParams.MATCH_PARENT;
        cardView.setLayoutParams(cardLP);

        maxAllowedHeightPx = calcMaxAllowedHeight();
        ViewGroup.LayoutParams clipLP = clipper.getLayoutParams();
        clipLP.height = Math.min(clipLP.height, maxAllowedHeightPx);
        clipper.setLayoutParams(clipLP);
        clipper.invalidateOutline();
    }

    private boolean isLandscape() {
        return parentActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int calcMaxAllowedHeight() {
        // CardView margins we added in buildCard() → 20 dp top + 20 dp bottom
        final int marginPx = convertDpToPixel(20, parentActivity) * 2;

        int screenHeightPx;      // full window height in pixels
        int insetTopPx  = 30;     // status-bar or cut-out
        int insetBotPx  = 30;     // nav-bar / gesture area

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = parentActivity.getWindowManager()
                    .getCurrentWindowMetrics();
            screenHeightPx = metrics.getBounds().height();

            Insets insets = metrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout());
            insetTopPx  = insets.top;
            insetBotPx  = insets.bottom;
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            parentActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
            screenHeightPx = dm.heightPixels;
        }

        int safeAreaAdjusted = screenHeightPx - insetTopPx - insetBotPx - marginPx;

        // Never allow the modal to consume more than 80 % of the *raw* screen height
        int eightyPercentCap = (int) (screenHeightPx * 0.80f);

        return Math.min(safeAreaAdjusted, eightyPercentCap);
    }

    // ------------------------------------------------------------------
    // JS bridge & helpers
    // ------------------------------------------------------------------
    private class GleapModalJSBridge {
        @JavascriptInterface public void gleapModalCallback(String raw) {
            if (parentActivity == null) return;
            parentActivity.runOnUiThread(() -> handleCallback(raw));
        }

        private void handleCallback(String raw) {
            try {
                JSONObject cb = new JSONObject(raw);
                switch (cb.getString("name")) {
                    case "modal-loaded":       sendModalData(); break;
                    case "modal-data-set":     GleapInvisibleActivityManger.animateViewInOut(getComponent(), true); break;
                    case "modal-close":        GleapInvisibleActivityManger.getInstance().destroyModal(true, false); break;
                    case "start-conversation": startConversation(cb); break;
                    case "show-form":          showForm(cb); break;
                    case "open-url":           openUrl(cb.optString("data")); break;
                    case "start-custom-action":startCustomAction(cb); break;
                    case "show-survey":        showSurvey(cb); break;
                    case "show-news-article":  Gleap.getInstance().openNewsArticle(cb.getJSONObject("data").getString("articleId"), true); break;
                    case "show-checklist":     Gleap.getInstance().openChecklist(cb.getJSONObject("data").getString("checklistId"), true); break;
                    case "show-help-article":  Gleap.getInstance().openHelpCenterArticle(cb.getJSONObject("data").getString("articleId"), true); break;
                    case "modal-height":       updateMinHeight(cb.getJSONObject("data").getInt("height")); break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void updateMinHeight(int requestedDp) {
            int requestedPx = convertDpToPixel(requestedDp, parentActivity);
            int newHeight = Math.min(requestedPx, maxAllowedHeightPx);
            ViewGroup.LayoutParams lp = clipper.getLayoutParams();
            lp.height = newHeight;
            clipper.setLayoutParams(lp);
            clipper.invalidateOutline();
        }

        private void sendModalData() {
            try { sendMessage(generateGleapMessage("modal-data", modalData)); } catch (Exception ignored) {}
        }

        // ---- helper methods ------------------------------------------
        private void startConversation(JSONObject cb) throws JSONException {
            String botId = cb.optJSONObject("data") == null ? "" : cb.getJSONObject("data").optString("botId", "");
            Gleap.getInstance().startBot(botId);
        }
        private void showForm(JSONObject cb) throws JSONException {
            String formId = cb.optJSONObject("data") == null ? "" : cb.getJSONObject("data").optString("formId", "");
            Gleap.getInstance().startFeedbackFlow(formId);
        }

        private void openUrl(String url) {
            if (url != null && !url.isEmpty()) Gleap.getInstance().handleLink(url);
        }

        private void startCustomAction(JSONObject cb) throws JSONException {
            if (GleapConfig.getInstance().getCustomActions() == null) return;
            String action = cb.optJSONObject("data") == null ? "" : cb.getJSONObject("data").optString("action", "");
            GleapConfig.getInstance().getCustomActions().invoke(action, null);
        }

        private void showSurvey(JSONObject cb) throws JSONException {
            String formId = cb.optJSONObject("data") == null ? "" : cb.getJSONObject("data").optString("formId", "");
            SurveyType type = SurveyType.SURVEY_FULL;
            if (cb.optJSONObject("data") != null && "survey".equalsIgnoreCase(cb.getJSONObject("data").optString("surveyFormat", ""))) {
                type = SurveyType.SURVEY;
            }
            Gleap.getInstance().showSurvey(formId, type);
        }
    }

    // ------------------------------------------------------------------
    // WebView helpers
    // ------------------------------------------------------------------
    void sendMessage(String message) {
        if (webView != null) {
            webView.evaluateJavascript("window.appMessage(" + message + ");", null);
        }
    }

    private String generateGleapMessage(String name, JSONObject data) throws JSONException {
        return new JSONObject().put("name", name).put("data", data).toString();
    }

    // ------------------------------------------------------------------
    // WebViewClient & WebChromeClient
    // ------------------------------------------------------------------
    private class GleapModalWebViewClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                if (!url.contains(modalUrl)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    if (intent.resolveActivity(parentActivity.getPackageManager()) != null) {
                        parentActivity.startActivity(intent);
                    }
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }

        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
        }

        @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (backdrop != null) backdrop.setVisibility(View.GONE);
        }
    }

    private static class GleapModalWebChromeClient extends WebChromeClient {
        @Override public boolean onJsAlert(WebView v, String u, String m, JsResult r) { return true; }
        @Override public boolean onJsConfirm(WebView v, String u, String m, JsResult r) { return true; }
        @Override public boolean onJsPrompt(WebView v, String u, String m, String d, JsPromptResult r) { return true; }
        @Override public boolean onConsoleMessage(ConsoleMessage m) { return true; }
    }
}
