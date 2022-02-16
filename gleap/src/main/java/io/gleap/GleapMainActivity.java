package io.gleap;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import gleap.io.gleap.R;

public class GleapMainActivity extends AppCompatActivity implements OnHttpResponseListener {
    private WebView webView;
    private String url = GleapConfig.getInstance().getWidgetUrl() + "/appwidget/" + GleapConfig.getInstance().getSdkKey();
    private ValueCallback<Uri[]> mUploadMessage;

    @Override
    public void onBackPressed() {
        GleapDetectorUtil.resumeAllDetectors();
        super.onBackPressed();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            if(getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        super.onCreate(savedInstanceState);

        GleapBug.getInstance().setLanguage(Locale.getDefault().getLanguage());

        url += GleapURLGenerator.generateURL();

        setContentView(R.layout.activity_gleap_main);
        webView = findViewById(R.id.gleap_webview);
        webView.setVisibility(View.VISIBLE);

        initBrowser();
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
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
    }

    @Override
    public void onTaskComplete(int httpResponse) {
        if (httpResponse == 201) {
            GleapDetectorUtil.resumeAllDetectors();
            GleapBug.getInstance().setDisabled(false);
            webView.evaluateJavascript("Gleap.getInstance().showSuccessAndClose()",null);
        } else {
            GleapDetectorUtil.resumeAllDetectors();
            finish();
        }
    }

    private class GleapWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (!url.contains(GleapConfig.getInstance().getWidgetUrl())) {
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
                    finish();
                    GleapDetectorUtil.resumeAllDetectors();
                }
            }).create();

            alertDialog.setTitle(getString(R.string.gleap_alert_no_internet_title));
            alertDialog.setMessage(getString(R.string.gleap_alert_no_internet_subtitle));

            alertDialog.show();

        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
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

        // maneja la accion de seleccionar archivos
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

            // asegurar que no existan callbacks
            if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(null);
            }

            mUploadMessage = filePathCallback;

            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*"); // set MIME type to filter

            ActivityUtil.getCurrentActivity().startActivityForResult(Intent.createChooser(i, "File Chooser"),1);

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
    }

    private class GleapJSBridge {
        private final AppCompatActivity mContext;

        public GleapJSBridge(AppCompatActivity c) {
            mContext = c;
        }

        @JavascriptInterface
        public void closeGleap(String object){
            this.mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GleapDetectorUtil.resumeAllDetectors();
                    GleapConfig.getInstance().setAction(null);
                    finish();
                }
            });
        }

        @JavascriptInterface
        public void sessionReady(String object) {
            this.mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.setVisibility(View.VISIBLE);
                }
            });
        }

        @JavascriptInterface
        public void requestScreenshot(String option){
            this.mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String image = "data:image/png;base64," + ScreenshotUtil.bitmapToBase64(GleapBug.getInstance().getScreenshot());
                    webView.evaluateJavascript("Gleap.setScreenshot('" + image + "', true)", null);
                }
            });
        }

        @JavascriptInterface
        public void customActionCalled(String object) {
            try {
                JSONObject jsonObject = new JSONObject(object);
                String method = jsonObject.getString("name");
                GleapConfig.getInstance().getCustomActions().invoke(method);
                finish();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void openExternalURL(String object) {
            try {
                JSONObject jsonObject = new JSONObject(object);
                String url = jsonObject.getString("url");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
                finish();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @JavascriptInterface
        public void sendFeedback(String object) {
            this.mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GleapBug gleapBug = GleapBug.getInstance();
                    try {
                        JSONObject jsonObject = new JSONObject(object);
                        if(jsonObject.has("screenshot")) {
                            String base64String = jsonObject.get("screenshot").toString();
                            if(!base64String.equals("null")) {
                                String base64Image = base64String.split(",")[1];
                                byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                                gleapBug.setScreenshot(decodedByte);
                            }
                        }

                        gleapBug.setType(jsonObject.getString("type"));
                        if(jsonObject.has("formData")) {
                            JSONObject formData = jsonObject.getJSONObject("formData");
                            gleapBug.setData(formData);
                        }
                        if(jsonObject.has("excludeData")) {
                            GleapConfig.getInstance().setStripModel(jsonObject.getJSONObject("excludeData"));
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    new HttpHelper(GleapMainActivity.this, getApplicationContext()).execute(gleapBug);

                }
            });
        }
    }
}