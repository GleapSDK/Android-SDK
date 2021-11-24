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
    private String feedbackFlow ="";

    private GleapAction action;

    private JSONObject stripModel = new JSONObject();

    private ConfigLoadedCallback configLoadedCallback;
    private FeedbackSentCallback feedbackSentCallback;
    private FeedbackWillBeSentCallback feedbackWillBeSentCallback;
    private CustomActionCallback customAction;
    private GetBitmapCallback getBitmapCallback;
    private List<GleapDetector> gestureDetectors;
    private int interval = 5;

    //user config
    private boolean enableConsoleLogs = true;
    private boolean enableReplays = false;
    private boolean activationMethodShake = false;
    private boolean activationMethodScreenshotGesture = false;
    private String language = "en";

    //Streamedevent
    private int maxEventLength = 500;
    private int resceduleEventStreamDurationShort = 1500;
    private int resceduleEventStreamDurationLong = 3000;

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
            if(config.has("replaysInterval")){
                this.interval = config.getInt("replaysInterval");
            }
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

    public void setWidgetUrl(String widgetUrl) {
        this.widgetUrl = widgetUrl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public FeedbackSentCallback getFeedbackSentCallback() {
        return feedbackSentCallback;
    }

    public void setBugSentCallback(FeedbackSentCallback feedbackSentCallback) {
        this.feedbackSentCallback = feedbackSentCallback;
    }

    public FeedbackWillBeSentCallback getBugWillBeSentCallback() {
        return feedbackWillBeSentCallback;
    }

    public void setBugWillBeSentCallback(FeedbackWillBeSentCallback feedbackWillBeSentCallback) {
        this.feedbackWillBeSentCallback = feedbackWillBeSentCallback;
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

    public String getWidgetUrl() {
        return widgetUrl;
    }

    public ConfigLoadedCallback getConfigLoadedCallback() {
        return configLoadedCallback;
    }

    public void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback) {
        this.configLoadedCallback = configLoadedCallback;
    }

    public int getInterval() {
        return interval;
    }

    public String getFeedbackFlow() {
        return feedbackFlow;
    }

    public void setFeedbackFlow(String feedbackFlow) {
        this.feedbackFlow = feedbackFlow;
    }

    public JSONObject getStripModel() {
        return stripModel;
    }

    public void setStripModel(JSONObject stripModel) {
        this.stripModel = stripModel;
    }

    public GleapAction getAction() {
        return action;
    }

    public void setAction(GleapAction action) {
        this.action = action;
    }

    public int getMaxEventLength() {
        return maxEventLength;
    }

    public int getResceduleEventStreamDurationShort() {
        return resceduleEventStreamDurationShort;
    }

    public int getResceduleEventStreamDurationLong() {
        return resceduleEventStreamDurationLong;
    }
}
