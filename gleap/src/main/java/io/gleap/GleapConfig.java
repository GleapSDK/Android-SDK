package io.gleap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Configuration received by the server
 */
class GleapConfig {
    private static GleapConfig instance;

    //bb config
    private String apiUrl = "https://api.gleap.io";
    private String widgetUrl = "https://widget.gleap.io";
    private String sdkKey = "";
    private GleapSentCallback gleapSentCallback;
    private BugWillBeSentCallback bugWillBeSentCallback;
    private GetBitmapCallback getBitmapCallback;
    private List<GleapDetector> gestureDetectors;
    private String privacyPolicyUrl = "";
    private boolean isPrivacyPolicyEnabled = false;

    //user config
    private String color = "#0693E3";
    private boolean enableConsoleLogs = true;
    private boolean enableReplays = false;
    private boolean activationMethodShake = false;
    private boolean activationMethodScreenshotGesture = false;
    private String language = "en";
    private CustomActionCallback customAction;

    private GleapConfig() {
    }

    public static GleapConfig getInstance() {
        if (instance == null) {
            instance = new GleapConfig();
        }
        return instance;
    }

    /**
     * Read Values from the config
     *
     * @param config response from the server with all the configuration data in it
     */
    public void initConfig(JSONObject config) {
        try {
            this.enableConsoleLogs = config.getBoolean("enableConsoleLogs");
            this.enableReplays = config.getBoolean("enableReplays");
            this.activationMethodShake = config.getBoolean("activationMethodShake");
            this.activationMethodScreenshotGesture = config.getBoolean("activationMethodScreenshotGesture");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getSdkKey() {
        return sdkKey;
    }

    public void setSdkKey(String sdkKey) {
        this.sdkKey = sdkKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getColor() {
        return color;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public GleapSentCallback getBugSentCallback() {
        return gleapSentCallback;
    }

    public void setBugSentCallback(GleapSentCallback gleapSentCallback) {
        this.gleapSentCallback = gleapSentCallback;
    }

    public BugWillBeSentCallback getBugWillBeSentCallback() {
        return bugWillBeSentCallback;
    }

    public void setBugWillBeSentCallback(BugWillBeSentCallback bugWillBeSentCallback) {
        this.bugWillBeSentCallback = bugWillBeSentCallback;
    }

    public GetBitmapCallback getGetBitmapCallback() {
        return getBitmapCallback;
    }

    public void setGetBitmapCallback(GetBitmapCallback getBitmapCallback) {
        this.getBitmapCallback = getBitmapCallback;
    }

    public List<GleapDetector> getGestureDetectors() {
        return gestureDetectors;
    }

    public void setGestureDetectors(List<GleapDetector> gestureDetectors) {
        this.gestureDetectors = gestureDetectors;
    }

    public boolean isActivationMethodShake() {
        return activationMethodShake;
    }

    public boolean isActivationMethodScreenshotGesture() {
        return activationMethodScreenshotGesture;
    }

    public boolean isEnableConsoleLogs() {
        return enableConsoleLogs;
    }

    public boolean isEnableReplays() {
        return enableReplays;
    }

    public void setEnableReplays(boolean enableReplays) {
        this.enableReplays = enableReplays;
    }

    public void registerCustomAction(CustomActionCallback customAction) {
        this.customAction = customAction;
    }

    public CustomActionCallback getCustomActions() {
        return customAction;
    }

    public void setPrivacyPolicyUrl(String privacyPolicyUrl) {
        this.privacyPolicyUrl = privacyPolicyUrl;
    }

    public void setPrivacyPolicyEnabled(boolean privacyPolicyEnabled) {
        isPrivacyPolicyEnabled = privacyPolicyEnabled;
    }

    public String getWidgetUrl() {
        return widgetUrl;
    }
}
