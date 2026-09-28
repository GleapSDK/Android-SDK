package io.gleap;

import android.net.Uri;
import android.webkit.ValueCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * The SDK configuration: hosts, SDK key and language set by the app, the values of the remote
 * config (package-private fields, written by {@link GleapRemoteConfig}) and the report
 * exclusions the widget sends.
 */
class GleapConfig {
    // Created with the class: getInstance() is called from several threads.
    private static volatile GleapConfig instance = new GleapConfig();

    // Regional hosts. Defaults to the EU region, see GleapRegion for the region table.
    private volatile String apiUrl = GleapRegion.EU.getApiUrl();
    private volatile String wsApiUrl = GleapRegion.EU.getWsApiUrl();
    // Optional realtime host passed to the widget. null = widget default.
    private volatile String realtimeHost = null;
    // Static widget hosts. These are global and not affected by the region.
    private volatile String iFrameUrl = "https://messenger-app.gleap.io/appnew";
    private volatile String bannerUrl = "https://outboundmedia.gleap.io";
    private volatile String modalUrl = "https://outboundmedia.gleap.io/modal";
    private volatile String sdkKey = "";
    private ValueCallback<Uri[]> fileUploadCallback;

    private JSONObject stripModel = new JSONObject();
    private List<GleapDetector> gestureDetectors = new LinkedList<>();
    private List<GleapActivationMethod> prioritizedActivationMethods = new LinkedList<>();
    int interval = 5;

    // Set from the remote config (flowConfig).
    String buttonLogo = "https://sdk.gleap.io/res/chatbubble.png";
    String buttonColor = "#485bff";
    String color = "#485bff";
    // As configured in the dashboard — getBackgroundColor() applies the color scheme.
    String backgroundColor = "#ffffff";
    int borderRadius = 20;
    String headerColor = "#485bff";
    // Loading-background config (mirrors the web/iOS SDK loaders). headerColor2/3
    // fall back to headerColor via their getters, like the messenger's
    // getHeaderColorSecondary.
    String headerColor2 = "";
    String headerColor3 = "";
    String bgType = "";
    String bgImage = "";
    int homeVersion = 0;
    boolean fadeBg = true;
    boolean bgBlur = true;

    volatile boolean enableConsoleLogs = true;
    private volatile boolean enableConsoleLogsFromCode = true;
    boolean enableReplays = false;
    boolean activationMethodShake = false;
    boolean activationMethodScreenshotGesture = false;
    boolean activationMethodFeedbackButton = false;
    private volatile String language = "en";
    // Read when network logs are recorded and sent (background threads).
    volatile JSONArray networkLogPropsToIgnore = new JSONArray();
    volatile JSONArray blackList = new JSONArray();
    JSONObject plainConfig;

    WidgetPositionType widgetPositionType = WidgetPositionType.NEW;
    WidgetPosition widgetPosition = WidgetPosition.BOTTOM_RIGHT;
    String widgetButtonText = "Feedback";
    boolean hideFeedbackButton = false;
    private boolean feedbackButtonManuallySet = false;

    int buttonX = 20; //horizontal
    int buttonY = 20; //vertical

    /** X/Y offset for the notification container when feedback button is hidden (default 0). */
    private int notificationContainerOffsetX = 0;
    private int notificationContainerOffsetY = 0;

    private LinkedList<GleapWebViewMessage> gleapWebViewMessages = new LinkedList<>();

    private GleapConfig() {
        this.language = deviceLanguage();
    }

    private static String deviceLanguage() {
        return Locale.getDefault().toLanguageTag().toLowerCase();
    }

    public static GleapConfig getInstance() {
        return instance;
    }

    // Tests only.
    static void resetForTesting() {
        instance = new GleapConfig();
    }

    /**
     * Applies the remote config (see {@link GleapRemoteConfig}).
     *
     * @param config response from the server with all the configuration data in it
     */
    public void initConfig(JSONObject config) {
        GleapRemoteConfig.apply(this, config);
    }

    public synchronized String getSdkKey() {
        return sdkKey;
    }

    public synchronized void setSdkKey(String sdkKey) {
        this.sdkKey = sdkKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getWsApiUrl() {
        return wsApiUrl;
    }

    public void setWsApiUrl(String wsApiUrl) {
        this.wsApiUrl = wsApiUrl;
    }

    public String getRealtimeHost() {
        return realtimeHost;
    }

    public void setRealtimeHost(String realtimeHost) {
        this.realtimeHost = realtimeHost;
    }

    /**
     * Applies all regional hosts at once. The static widget hosts stay untouched.
     */
    public synchronized void setRegion(GleapRegion region) {
        if (region == null) {
            return;
        }

        this.apiUrl = region.getApiUrl();
        this.wsApiUrl = region.getWsApiUrl();
        this.realtimeHost = region.getRealtimeHost();
    }

    public String getLanguage() {
        return language;
    }

    /**
     * @param language a language code; null or empty means the device language again (the
     *                 widget url, the config and the session request always need one)
     */
    public void setLanguage(String language) {
        this.language = language != null && !language.trim().isEmpty() ? language : deviceLanguage();
    }

    public List<GleapDetector> getGestureDetectors() {
        return gestureDetectors;
    }

    public void setGestureDetectors(List<GleapDetector> gestureDetectors) {
        this.gestureDetectors = gestureDetectors;
    }

    public List<GleapActivationMethod> getPrioritizedActivationMethods() {
        return prioritizedActivationMethods;
    }

    public void setPrioritizedActivationMethods(List<GleapActivationMethod> prioritizedActivationMethods) {
        this.prioritizedActivationMethods = prioritizedActivationMethods;
    }

    public boolean isActivationMethodShake() {
        return activationMethodShake;
    }

    public boolean isActivationMethodScreenshotGesture() {
        return activationMethodScreenshotGesture;
    }

    public boolean isActivationMethodFeedbackButton() {
        return activationMethodFeedbackButton;
    }

    public boolean isEnableConsoleLogs() {
        return enableConsoleLogs;
    }

    public boolean isEnableReplays() {
        return enableReplays;
    }

    public int getInterval() {
        return interval;
    }

    public JSONObject getStripModel() {
        return stripModel;
    }

    public void setStripModel(JSONObject stripModel) {
        this.stripModel = stripModel;
    }

    public JSONArray getNetworkLogPropsToIgnore() {
        if (networkLogPropsToIgnore == null) {
            return new JSONArray();
        }
        return networkLogPropsToIgnore;
    }

    public JSONObject getPlainConfig() {
        return plainConfig;
    }

    public String getiFrameUrl() {
        return iFrameUrl;
    }

    public void setiFrameUrl(String iFrameUrl) {
        this.iFrameUrl = iFrameUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getModalUrl() {
        return modalUrl;
    }

    public void setModalUrl(String modalUrl) {
        this.modalUrl = modalUrl;
    }

    public JSONArray getBlackList() {
        if (blackList == null) {
            return new JSONArray();
        }
        return blackList;
    }

    public boolean isEnableConsoleLogsFromCode() {
        return enableConsoleLogsFromCode;
    }

    public void setEnableConsoleLogsFromCode(boolean enableConsoleLogsFromCode) {
        this.enableConsoleLogsFromCode = enableConsoleLogsFromCode;
    }

    public String getButtonLogo() {
        return buttonLogo;
    }

    public String getButtonColor() {
        return buttonColor;
    }

    public String getColor() {
        return color;
    }

    public WidgetPosition getWidgetPosition() {
        return widgetPosition;
    }

    public void addGleapWebViewMessage(GleapWebViewMessage gleapWebViewMessage) {
        this.gleapWebViewMessages.push(gleapWebViewMessage);
    }

    public LinkedList<GleapWebViewMessage> getGleapWebViewMessages() {
        return gleapWebViewMessages;
    }

    public void clearGleapWebViewMessages() {
        this.gleapWebViewMessages = new LinkedList<>();
    }

    /**
     * The widget background color with the active color scheme applied (see GleapThemeHelper).
     */
    public String getBackgroundColor() {
        return GleapThemeHelper.getInstance().getBackgroundColor(getFlowConfig());
    }

    /**
     * The flow config to send to the widget: the server's flow config with the
     * active color scheme applied. The cached server config is never modified.
     */
    public JSONObject getThemedFlowConfig() {
        return GleapThemeHelper.getInstance().applyToFlowConfig(getFlowConfig());
    }

    private JSONObject getFlowConfig() {
        JSONObject config = plainConfig;
        return config != null ? config.optJSONObject("flowConfig") : null;
    }

    public int getBorderRadius() {
        return borderRadius;
    }

    public String getHeaderColor() {
        return headerColor;
    }

    // Falls back to headerColor, mirroring the messenger's getHeaderColorSecondary.
    public String getHeaderColor2() {
        return (headerColor2 != null && !headerColor2.isEmpty()) ? headerColor2 : headerColor;
    }

    public String getHeaderColor3() {
        return (headerColor3 != null && !headerColor3.isEmpty()) ? headerColor3 : headerColor;
    }

    public String getBgType() {
        return bgType;
    }

    public String getBgImage() {
        return bgImage;
    }

    public int getHomeVersion() {
        return homeVersion;
    }

    public boolean isFadeBg() {
        return fadeBg;
    }

    public boolean isBgBlur() {
        return bgBlur;
    }

    public int getButtonX() {
        return buttonX;
    }

    public int getButtonY() {
        return buttonY;
    }

    public int getNotificationContainerOffsetX() {
        return notificationContainerOffsetX;
    }

    public void setNotificationContainerOffsetX(int notificationContainerOffsetX) {
        this.notificationContainerOffsetX = notificationContainerOffsetX;
    }

    public int getNotificationContainerOffsetY() {
        return notificationContainerOffsetY;
    }

    public void setNotificationContainerOffsetY(int notificationContainerOffsetY) {
        this.notificationContainerOffsetY = notificationContainerOffsetY;
    }

    public boolean isHideFeedbackButton() {
        return hideFeedbackButton;
    }

    public void setHideFeedbackButton(boolean hideFeedbackButton) {
        this.hideFeedbackButton = hideFeedbackButton;
    }

    public boolean isFeedbackButtonManuallySet() {
        return this.feedbackButtonManuallySet;
    }

    public void setFeedbackButtonManuallySet(boolean feedbackButtonManuallySet) {
        this.feedbackButtonManuallySet = feedbackButtonManuallySet;
    }

    public ValueCallback<Uri[]> getFileUploadCallback() {
        return fileUploadCallback;
    }

    public void setFileUploadCallback(ValueCallback<Uri[]> fileUploadCallback) {
        this.fileUploadCallback = fileUploadCallback;
    }

    public void finishImageUpload(Uri[] uris) {
        // Only while the widget waits for a file: a second call or one without a pending
        // picker has nothing to finish.
        ValueCallback<Uri[]> callback = this.fileUploadCallback;
        if (callback == null) {
            return;
        }
        this.fileUploadCallback = null;
        callback.onReceiveValue(uris);
    }

    public String getWidgetButtonText() {
        return widgetButtonText;
    }

    public WidgetPositionType getWidgetPositionType() {
        return widgetPositionType;
    }

}
