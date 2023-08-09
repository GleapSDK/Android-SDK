package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.view.Gravity;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.json.JSONObject;

class GleapBanner {
    private JSONObject bannerData;
    private LinearLayout layout;
    private boolean initialized = false;

    public GleapBanner(JSONObject bannerData) {
        this.bannerData = bannerData;

        Activity local = ActivityUtil.getCurrentActivity();
        if (local != null) {
            generateComponent(local);
        }
    }

    private void generateComponent(Activity activity) {
        this.layout = getBannerComponent(activity);
    }

    public LinearLayout getComponent() {
        return this.layout;
    }

    public void clearComponent() {
        this.layout = null;
    }

    public LinearLayout getBannerComponent(Activity local) {
        LinearLayout bannerContainer = new LinearLayout(local.getApplication().getApplicationContext());
        bannerContainer.setOrientation(LinearLayout.VERTICAL);
        bannerContainer.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                convertDpToPixel(200, local.getApplication().getApplicationContext())
        );
        bannerContainer.setLayoutParams(bannerParams);

        // Create the WebView and load a URL
        WebView webView = new WebView(local.getApplication().getApplicationContext());
        LinearLayout.LayoutParams webViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        webView.setLayoutParams(webViewParams);
        webView.loadUrl("https://www.example.com");  // Replace with your desired URL
        bannerContainer.addView(webView);

        return bannerContainer;
    }
}
