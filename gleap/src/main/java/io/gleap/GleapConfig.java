package io.gleap;

import android.graphics.Color;
import android.webkit.WebView;

import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.FeedbackFlowClosedCallback;
import io.gleap.callbacks.FeedbackFlowStartedCallback;
import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.FeedbackWillBeSentCallback;
import io.gleap.callbacks.GetActivityCallback;
import io.gleap.callbacks.GetBitmapCallback;
import io.gleap.callbacks.InitializationDoneCallback;
import io.gleap.callbacks.WidgetClosedCallback;
import io.gleap.callbacks.WidgetOpenedCallback;

/**
 * Configuration received by the server
 */
class GleapConfig {
    private static GleapConfig instance;

    //bb config
    private String apiUrl = "https://api.gleap.io";
    private String iFrameUrl = "https://messenger.gleap.io/app.html";
    private String sdkKey = "";
    private String feedbackFlow = "";

    private GleapAction action;

    private JSONObject stripModel = new JSONObject();
    private JSONObject crashStripModel = new JSONObject();

    private ConfigLoadedCallback configLoadedCallback;
    private FeedbackSentCallback feedbackSentCallback;
    private FeedbackSentCallback crashFeedbackSentCallback;
    private FeedbackWillBeSentCallback feedbackWillBeSentCallback;
    private FeedbackFlowStartedCallback feedbackFlowStartedCallback;
    private FeedbackFlowClosedCallback feedbackFlowClosedCallback;
    private FeedbackSendingFailedCallback feedbackSendingFailedCallback;
    private CallCloseCallback callCloseCallback;
    private WidgetOpenedCallback widgetOpenedCallback;
    private WidgetClosedCallback widgetClosedCallback;
    private GetActivityCallback getActivityCallback;
    private CustomActionCallback customAction;
    private GetBitmapCallback getBitmapCallback;
    private InitializationDoneCallback initializationDoneCallback;
    private List<GleapDetector> gestureDetectors = new LinkedList<>();
    private List<GleapActivationMethod> priorizedGestureDetectors = new LinkedList<>();
    private int interval = 5;

    //user config
    private String buttonLogo = "https://sdk.gleap.io/res/chatbubble.png";
    private String buttonColor = "#485bff";

    private String backgroundColor = "#ffffff";
    private String headerColor = "#485bff";
    private int loaderColor = Color.BLACK;

    private boolean enableConsoleLogs = true;
    private boolean enableConsoleLogsFromCode = true;
    private boolean enableReplays = false;
    private boolean activationMethodShake = false;
    private boolean activationMethodScreenshotGesture = false;
    private boolean activationMethodFeedbackButton = false;
    private String language = "en";
    private JSONArray networkLogPropsToIgnore;
    private JSONArray blackList = new JSONArray();
    private JSONObject plainConfig;

    private WidgetPosition widgetPosition = WidgetPosition.BOTTOM_RIGHT;
    private int buttonX = 20; //horizontal
    private int buttonY = 20; //vertical

    //Streamedevent
    private int maxEventLength = 500;
    private int resceduleEventStreamDurationShort = 1500;
    private int resceduleEventStreamDurationLong = 7500;

    private LinkedList<GleapWebViewMessage> gleapWebViewMessages = new LinkedList<>();

    private GleapConfig() {
        this.language = Locale.getDefault().toLanguageTag();
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
        if (config != null) {
            this.plainConfig = config;
        }

        JSONObject flowConfigs = new JSONObject();
        if (config.has("flowConfig")) {
            try {
                flowConfigs = config.getJSONObject("flowConfig");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        JSONObject projectActions = new JSONObject();
        if (config.has("projectActions")) {
            try {
                projectActions = config.getJSONObject("projectActions");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            if (flowConfigs.has("enableConsoleLogs")) {
                this.enableConsoleLogs = flowConfigs.getBoolean("enableConsoleLogs");
            }

            if (flowConfigs.has("feedbackButtonPosition")) {
                switch (flowConfigs.getString("feedbackButtonPosition")) {
                    case "BOTTOM_RIGHT":
                        this.widgetPosition = WidgetPosition.BOTTOM_RIGHT;
                        break;
                    case "BOTTOM_LEFT":
                        this.widgetPosition = WidgetPosition.BOTTOM_LEFT;
                        break;
                    default:
                        GleapInvisibleActivityManger.getInstance().setShowFab(false);
                        break;
                }
            }

            if (flowConfigs.has("buttonLogo") && !flowConfigs.getString("buttonLogo").equals("")) {
                this.buttonLogo = flowConfigs.getString("buttonLogo");
            }

            if (flowConfigs.has("buttonColor")) {
                this.buttonColor = flowConfigs.getString("buttonColor");
            }

            if (flowConfigs.has("backgroundColor")) {
                this.backgroundColor = flowConfigs.getString("backgroundColor");
                try {
                    int contrastColor = getContrastColor(Color.parseColor(this.backgroundColor));
                    this.loaderColor = contrastColor;
                } catch (Exception ignore) {
                }
            }

            if (flowConfigs.has("headerColor")) {
                this.headerColor = flowConfigs.getString("headerColor");
            }

            if (flowConfigs.has("enableReplays")) {
                this.enableReplays = flowConfigs.getBoolean("enableReplays");
            }

            if (flowConfigs.has("activationMethodShake")) {
                this.activationMethodShake = flowConfigs.getBoolean("activationMethodShake");
            }

            if (flowConfigs.has("activationMethodScreenshotGesture")) {
                this.activationMethodScreenshotGesture = flowConfigs.getBoolean("activationMethodScreenshotGesture");
            }

            if (flowConfigs.has("activationMethodFeedbackButton")) {
                this.activationMethodFeedbackButton = flowConfigs.getBoolean("activationMethodFeedbackButton");
            }

            if (flowConfigs.has("replaysInterval")) {
                this.interval = flowConfigs.getInt("replaysInterval");
            }

            if(flowConfigs.has("buttonX")) {
                this.buttonX = flowConfigs.getInt("buttonX");
            }

            if(flowConfigs.has("buttonY")) {
                this.buttonY = flowConfigs.getInt("buttonY");
            }

            if (flowConfigs.has("networkLogPropsToIgnore")) {
                this.networkLogPropsToIgnore = flowConfigs.getJSONArray("networkLogPropsToIgnore");
            }

            if (flowConfigs.has("replaysInterval")) {
                this.interval = flowConfigs.getInt("replaysInterval");
                GleapBug.getInstance().setReplay(new Replay(60 / this.interval, 1000 * this.interval));
            }

            if (flowConfigs.has("networkLogBlacklist")) {
                this.blackList = flowConfigs.getJSONArray("networkLogBlacklist");
            }

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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public FeedbackSentCallback getFeedbackSentCallback() {
        return feedbackSentCallback;
    }

    public void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback) {
        this.feedbackSentCallback = feedbackSentCallback;
    }

    public FeedbackWillBeSentCallback getFeedbackWillBeSentCallback() {
        return feedbackWillBeSentCallback;
    }

    public void setFeedbackWillBeSentCallback(FeedbackWillBeSentCallback feedbackWillBeSentCallback) {
        this.feedbackWillBeSentCallback = feedbackWillBeSentCallback;
    }

    public FeedbackFlowStartedCallback getFeedbackFlowStartedCallback() {
        return feedbackFlowStartedCallback;
    }

    public void setFeedbackFlowStartedCallback(FeedbackFlowStartedCallback feedbackFlowStartedCallback) {
        this.feedbackFlowStartedCallback = feedbackFlowStartedCallback;
    }

    public FeedbackFlowClosedCallback getFeedbackFlowClosedCallback() {
        return feedbackFlowClosedCallback;
    }

    public void setFeedbackFlowClosedCallback(FeedbackFlowClosedCallback feedbackFlowClosedCallback) {
        this.feedbackFlowClosedCallback = feedbackFlowClosedCallback;
    }

    public FeedbackSendingFailedCallback getFeedbackSendingFailedCallback() {
        return feedbackSendingFailedCallback;
    }

    public void setFeedbackSendingFailedCallback(FeedbackSendingFailedCallback feedbackSendingFailedCallback) {
        this.feedbackSendingFailedCallback = feedbackSendingFailedCallback;
    }

    public WidgetOpenedCallback getWidgetOpenedCallback() {
        return widgetOpenedCallback;
    }

    public void setWidgetOpenedCallback(WidgetOpenedCallback widgetOpenedCallback) {
        this.widgetOpenedCallback = widgetOpenedCallback;
    }

    public WidgetClosedCallback getWidgetClosedCallback() {
        return widgetClosedCallback;
    }

    public void setWidgetClosedCallback(WidgetClosedCallback widgetClosedCallback) {
        this.widgetClosedCallback = widgetClosedCallback;
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

    public List<GleapActivationMethod> getPriorizedGestureDetectors() {
        return priorizedGestureDetectors;
    }

    public void setPriorizedGestureDetectors(List<GleapActivationMethod> priorizedGestureDetectors) {
        this.priorizedGestureDetectors = priorizedGestureDetectors;
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

    public void setActivationMethodFeedbackButton(boolean activationMethodFeedbackButton) {
        this.activationMethodFeedbackButton = activationMethodFeedbackButton;
    }

    public boolean isEnableConsoleLogs() {
        return enableConsoleLogs;
    }

    public void setEnableConsoleLogs(boolean enableConsoleLogs) {
        this.enableConsoleLogs = enableConsoleLogs;
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

    public JSONArray getNetworkLogPropsToIgnore() {
        return networkLogPropsToIgnore;
    }

    public JSONObject getPlainConfig() {
        return plainConfig;
    }

    public GetActivityCallback getGetActivityCallback() {
        return getActivityCallback;
    }

    public void setGetActivityCallback(GetActivityCallback getActivityCallback) {
        this.getActivityCallback = getActivityCallback;
    }

    public String getiFrameUrl() {
        return iFrameUrl;
    }

    public void setiFrameUrl(String iFrameUrl) {
        this.iFrameUrl = iFrameUrl;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public FeedbackSentCallback getCrashFeedbackSentCallback() {
        return crashFeedbackSentCallback;
    }

    public void setCrashFeedbackSentCallback(FeedbackSentCallback crashFeedbackSentCallback) {
        this.crashFeedbackSentCallback = crashFeedbackSentCallback;
    }

    public JSONObject getCrashStripModel() {
        return crashStripModel;
    }

    public void setCrashStripModel(JSONObject crashStripModel) {
        this.crashStripModel = crashStripModel;
    }

    public JSONArray getBlackList() {
        return blackList;
    }


    public CallCloseCallback getCallCloseCallback() {
        return callCloseCallback;
    }

    public void setCallCloseCallback(CallCloseCallback callCloseCallback) {
        this.callCloseCallback = callCloseCallback;
    }

    public InitializationDoneCallback getInitializationDoneCallback() {
        return initializationDoneCallback;
    }

    public void setInitializationDoneCallback(InitializationDoneCallback initializationDoneCallback) {
        this.initializationDoneCallback = initializationDoneCallback;
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

    public WidgetPosition getWidgetPosition() {
        return widgetPosition;
    }

    public void addGleapWebViewMessage(GleapWebViewMessage gleapWebViewMessage) {
        this.gleapWebViewMessages.push(gleapWebViewMessage);
    }


    public LinkedList<GleapWebViewMessage> getGleapWebViewMessages() {
        return gleapWebViewMessages;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getHeaderColor() {
        return headerColor;
    }

    public int getLoaderColor() {
        return loaderColor;
    }

    public int getButtonX() {
        return buttonX;
    }

    public int getButtonY() {
        return buttonY;
    }

    private int getContrastColor(int color) {
        double y = ColorUtils.calculateContrast(Color.WHITE, color);
        if (y <= 5) {
            return Color.BLACK;
        }
        return Color.WHITE;
    }
}
