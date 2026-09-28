package io.gleap;

import android.app.Application;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import io.gleap.callbacks.AiToolExecutedCallback;
import io.gleap.callbacks.GleapAgentToolHandler;
import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.CustomLinkHandlerCallback;
import io.gleap.callbacks.FeedbackFlowStartedCallback;
import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.FeedbackWillBeSentCallback;
import io.gleap.callbacks.GetActivityCallback;
import io.gleap.callbacks.GetBitmapCallback;
import io.gleap.callbacks.InitializationDoneCallback;
import io.gleap.callbacks.InitializedCallback;
import io.gleap.callbacks.NotificationUnreadCountUpdatedCallback;
import io.gleap.callbacks.OutboundSentCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;
import io.gleap.callbacks.WidgetClosedCallback;
import io.gleap.callbacks.WidgetOpenedCallback;
import io.gleap.callbacks.ErrorCallback;

public class Gleap implements iGleap {
    // Created with the class: getInstance() is called from several threads.
    private static final Gleap instance = new Gleap();
    public static JSONArray blacklist = new JSONArray();
    public static JSONArray propsToIgnore = new JSONArray();
    public static boolean internalCloseWidgetOnExternalLinkOpen = false;

    private Gleap() {
    }

    /**
     * Get an instance of Gleap
     *
     * @return instance of Gleap
     */
    public static Gleap getInstance() {
        return instance;
    }

    /**
     * Auto-configures the Gleap SDK from the remote config.
     *
     * @param sdkKey      The SDK key, which can be found on dashboard.Gleap.io
     * @param application used to have context and access to take screenshot
     */
    public static void initialize(String sdkKey, Application application) {
        GleapInitializer.initialize(sdkKey, application);
    }

    public void processOpenPushActions() {
        GleapPushActions.processOpenPushActions();
    }

    @Override
    public void handlePushNotification(JSONObject notificationData) {
        GleapPushActions.handlePushNotification(notificationData);
    }

    @Override
    public void openConversations() {
        openConversations(false);
    }

    @Override
    public void openConversations(boolean hideBackButton) {
        GleapWidgetLauncher.openWithAction("open-conversations",
                () -> new JSONObject().put("hideBackButton", hideBackButton),
                () -> openConversations(hideBackButton),
                "openConversations - inner", "openConversations - middle", "openConversations - outer");
    }

    @Override
    public void openConversation(String shareToken) {
        GleapWidgetLauncher.openWithAction("open-conversation",
                () -> new JSONObject().put("hideBackButton", false).put("shareToken", shareToken),
                () -> openConversation(shareToken));
    }

    /**
     * Manually shows the feedback menu or default feedback flow. This is used, when
     * you use the activation method "NONE".
     *
     * @author Gleap
     */
    @Override
    public void open() {
        open(SurveyType.NONE);
    }

    /**
     * Disable in-app notifications. This is useful, when you want to use your own
     * in-app notifications UI.
     *
     * @author Gleap
     */
    @Override
    public void setDisableInAppNotifications(boolean disableInAppNotifications) {
        GleapEventService.getInstance().setDisableInAppNotifications(disableInAppNotifications);
    }

    protected void open(SurveyType type) {
        GleapWidgetLauncher.openWithScreenshot(type, () -> open(type));
    }

    @Override
    public void openChecklists() {
        openChecklists(true);
    }

    @Override
    public void openChecklists(boolean showBackButton) {
        GleapWidgetLauncher.openWithAction("open-checklists",
                () -> new JSONObject().put("hideBackButton", !showBackButton),
                () -> openChecklists(showBackButton));
    }

    @Override
    public void openChecklist(String checklistId) {
        openChecklist(checklistId, false);
    }

    @Override
    public void openChecklist(String checklistId, boolean showBackButton) {
        GleapWidgetLauncher.openWithAction("open-checklist",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("id", checklistId),
                () -> openChecklist(checklistId, showBackButton));
    }

    @Override
    public void startChecklist(String outboundId) {
        startChecklist(outboundId, false);
    }

    @Override
    public void startChecklist(String outboundId, boolean showBackButton) {
        GleapWidgetLauncher.openWithAction("start-checklist",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("outboundId", outboundId),
                () -> startChecklist(outboundId, showBackButton));
    }

    /**
     * Manually shows the news section
     *
     * @author Gleap
     */
    @Override
    public void openNews() {
        openNews(false);
    }

    /**
     * Manually shows the news section
     *
     * @param showBackButton show back button
     * @author Gleap
     */
    public void openNews(boolean showBackButton) {
        GleapWidgetLauncher.openWithAction("open-news",
                () -> new JSONObject().put("hideBackButton", !showBackButton),
                () -> openNews(showBackButton));
    }

    @Override
    public void startConversation() {
        startBot("", false);
    }

    @Override
    public void startConversation(boolean showBackButton) {
        startBot("", showBackButton);
    }

    @Override
    public void startBot(String botId) {
        startBot(botId, false);
    }

    @Override
    public void startBot(String botId, boolean showBackButton) {
        GleapWidgetLauncher.openWithAction("start-bot",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("botId", botId),
                () -> startBot(botId, showBackButton));
    }

    public void openNewsArticle(String articleId) {
        openNewsArticle(articleId, false);
    }

    public void openNewsArticle(String articleId, boolean showBackButton) {
        GleapWidgetLauncher.openWithAction("open-news-article",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("id", articleId),
                () -> openNewsArticle(articleId, showBackButton));
    }

    /**
     * Start a classic form by formId
     */
    @Override
    public void startClassicForm(String formId) {
        GleapErrors.guard("startClassicForm", () -> startFeedbackFlow(formId, true));
    }

    @Override
    public void startClassicForm(String formId, Boolean showBackButton) {
        GleapErrors.guard("startClassicForm", () -> startFeedbackFlow(formId, showBackButton));
    }

    /**
     * Manually start the bug reporting workflow. This is used, when you use the
     * activation method "NONE".
     */
    @Override
    public void startFeedbackFlow(String feedbackFlow) {
        GleapErrors.guard("startFeedbackFlow", () -> startFeedbackFlow(feedbackFlow, true));
    }

    @Override
    public void startFeedbackFlow(String feedbackFlow, Boolean showBackButton) {
        GleapWidgetLauncher.openWithActionUnchecked("start-feedbackflow",
                () -> {
                    JSONObject data = new JSONObject();
                    if (!feedbackFlow.equals("")) {
                        data.put("flow", feedbackFlow);
                    }
                    data.put("hideBackButton", !showBackButton);
                    return data;
                },
                () -> startFeedbackFlow(feedbackFlow, showBackButton),
                "run");
    }

    // survey, survey_full

    @Override
    public void showSurvey(String surveyId) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("isSurvey", true);
            jsonObject.put("hideBackButton", true);
            jsonObject.put("format", "survey");
            jsonObject.put("flow", surveyId);
        } catch (Exception ex) {
            handleError(ex, "showSurvey");
        }
        // check if it isopen
        GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("start-survey", jsonObject));
        Gleap.getInstance().open(SurveyType.SURVEY);
    }

    @Override
    public void showSurvey(String surveyId, SurveyType surveyType) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("isSurvey", true);
            jsonObject.put("hideBackButton", true);
            jsonObject.put("format", surveyType.name().toLowerCase(Locale.ROOT));
            jsonObject.put("flow", surveyId);
        } catch (Exception ex) {
            handleError(ex, "showSurvey");
        }
        // check if it isopen
        GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("start-survey", jsonObject));
        Gleap.getInstance().open(surveyType);
    }

    @Override
    public void openHelpCenter() {
        openHelpCenter(false);
    }

    @Override
    public void openHelpCenter(Boolean showBackButton) {
        GleapWidgetLauncher.openWithActionUnchecked("open-helpcenter",
                () -> new JSONObject().put("hideBackButton", !showBackButton),
                () -> openHelpCenter(showBackButton),
                "run");
    }

    @Override
    public void askAI(String question) {
        askAI(question, false);
    }

    @Override
    public void askAI(String question, Boolean showBackButton) {
        GleapWidgetLauncher.openWithActionUnchecked("ask-ai",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("question", question),
                () -> askAI(question, showBackButton),
                "run");
    }

    @Override
    public void openHelpCenterArticle(String articleId) {
        openHelpCenterArticle(articleId, false);
    }

    @Override
    public void openHelpCenterArticle(String articleId, Boolean showBackButton) {
        GleapWidgetLauncher.openWithActionUnchecked("open-help-article",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("articleId", articleId),
                () -> openHelpCenterArticle(articleId, showBackButton),
                "run");
    }

    /**
     * Removes these props from the network logs before they are sent, in addition to the ones
     * configured in the dashboard: request and response headers with this name, keys in JSON
     * bodies at any depth (a prop with dots such as {@code user.password} is also a path from the
     * body root), form fields and url query parameters. Names match case-insensitively. The
     * authorization, proxy-authorization, cookie and set-cookie headers are always masked.
     * Each call replaces the previous list, an empty array or null resets it.
     *
     * @param propsToIgnore the prop names to remove
     * @author Gleap
     */
    @Override
    public void setNetworkLogPropsToIgnore(String[] propsToIgnore) {
        Gleap.propsToIgnore = toJSONArray(propsToIgnore);
    }

    /**
     * Sets the env data props to ignore. These keys (e.g. deviceName) are removed from the
     * env data before a ticket is sent. Each call replaces the previous list, an empty array resets it.
     *
     * @param envDataPropsToIgnore the env data keys to ignore
     * @author Gleap
     */
    @Override
    public void setEnvDataPropsToIgnore(String[] envDataPropsToIgnore) {
        PhoneMeta.setEnvDataPropsToIgnore(envDataPropsToIgnore);
    }

    /**
     * Disables the env data. While disabled, no env data is collected at all.
     *
     * @param disableEnvData true to stop collecting env data, false to collect it again
     * @author Gleap
     */
    @Override
    public void setDisableEnvData(boolean disableEnvData) {
        PhoneMeta.setEnvDataDisabled(disableEnvData);
    }

    /**
     * Leaves requests whose url contains one of these strings out of the network logs, in addition
     * to the blacklist configured in the dashboard. Requests to gleap.io and gleap.ai are always
     * left out. Each call replaces the previous list, an empty array or null resets it.
     *
     * @param blacklist url parts to leave out
     * @author Gleap
     */
    @Override
    public void setNetworkLogsBlacklist(String[] blacklist) {
        Gleap.blacklist = toJSONArray(blacklist);
    }

    // Trimmed, without empty entries and duplicates.
    private static JSONArray toJSONArray(String[] items) {
        JSONArray raw = new JSONArray();
        if (items != null) {
            for (String item : items) {
                if (item != null) {
                    raw.put(item);
                }
            }
        }
        JSONArray jsonArray = new JSONArray();
        for (String item : GleapNetworkLogSanitizer.mergeStrings(raw)) {
            jsonArray.put(item);
        }
        return jsonArray;
    }

    @Override
    public void openHelpCenterCollection(String collectionId) {
        openHelpCenterCollection(collectionId, false);
    }

    @Override
    public void openHelpCenterCollection(String collectionId, Boolean showBackButton) {
        GleapWidgetLauncher.openWithActionUnchecked("open-help-collection",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("collectionId", collectionId),
                () -> openHelpCenterCollection(collectionId, showBackButton),
                "run");
    }

    @Override
    public void searchHelpCenter(String term) {
        searchHelpCenter(term, false);
    }

    @Override
    public void searchHelpCenter(String term, Boolean showBackButton) {
        GleapWidgetLauncher.openWithActionUnchecked("open-helpcenter-search",
                () -> new JSONObject().put("hideBackButton", !showBackButton).put("term", term),
                () -> searchHelpCenter(term, showBackButton),
                "run");
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity) {
        GleapErrors.guard("sendSilentCrashReport", () -> SilentBugReportUtil.createSilentBugReport(GleapInitializer.getApplication(), description, severity));
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity, JSONObject excludeData) {
        GleapErrors.guard("sendSilentCrashReport", () -> SilentBugReportUtil.createSilentBugReport(GleapInitializer.getApplication(), description, severity, excludeData));
    }

    /**
     * Updates a session's user data.
     *
     * @param id Id of the user.
     * @author Gleap
     */
    @Override
    public void identifyUser(String id) {
        try {
            if (GleapSessionController.getInstance() != null) {
                GleapSessionProperties gleapSessionProperties = new GleapSessionProperties();
                gleapSessionProperties.setUserId(id);

                GleapSessionController.getInstance().setPendingIdentificationAction(gleapSessionProperties);
                GleapSessionController.getInstance().executePendingUpdates();
            }
        } catch (Error | Exception exception) {
            handleError(exception, "identifyUser");
        }
    }

    /**
     * Updates a session's user data.
     *
     * @param id                     Id of the user.
     * @param gleapSessionProperties The updated user data.
     * @author Gleap
     */
    @Override
    public void identifyUser(String id, GleapSessionProperties gleapSessionProperties) {
        try {
            if (GleapSessionController.getInstance() != null) {
                gleapSessionProperties.setUserId(id);
                GleapSessionController.getInstance().setPendingIdentificationAction(gleapSessionProperties);
                GleapSessionController.getInstance().executePendingUpdates();
            }
        } catch (Error | Exception exception) {
            handleError(exception, "identifyUser");
        }
    }

    @Override
    public void identifyUser(String id, GleapSessionProperties gleapSessionProperties, JSONObject customData) {
        try {
            if (GleapSessionController.getInstance() != null) {
                gleapSessionProperties.setUserId(id);

                if (customData != null) {
                    gleapSessionProperties.setCustomData(customData);
                }

                GleapSessionController.getInstance().setPendingIdentificationAction(gleapSessionProperties);
                GleapSessionController.getInstance().executePendingUpdates();
            }
        } catch (Error | Exception exception) {
            handleError(exception, "identifyUser");
        }
    }

    /**
     * Identifies a contact.
     *
     * @param id Id of the user.
     * @author Gleap
     */
    @Override
    public void identifyContact(String id) {
        try {
            if (GleapSessionController.getInstance() != null) {
                GleapSessionProperties gleapSessionProperties = new GleapSessionProperties();
                gleapSessionProperties.setUserId(id);

                GleapSessionController.getInstance().setPendingIdentificationAction(gleapSessionProperties);
                GleapSessionController.getInstance().executePendingUpdates();
            }
        } catch (Error | Exception exception) {
            handleError(exception, "identifyContact");
        }
    }

    /**
     * Identifies a contact with data.
     *
     * @param id                     Id of the user.
     * @param gleapSessionProperties The updated user data.
     * @author Gleap
     */
    @Override
    public void identifyContact(String id, GleapSessionProperties gleapSessionProperties) {
        try {
            if (GleapSessionController.getInstance() != null) {
                gleapSessionProperties.setUserId(id);
                GleapSessionController.getInstance().setPendingIdentificationAction(gleapSessionProperties);
                GleapSessionController.getInstance().executePendingUpdates();
            }
        } catch (Error | Exception exception) {
            handleError(exception, "identifyContact");
        }
    }

    @Override
    public void updateContact(GleapSessionProperties gleapSessionProperties) {
        try {
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().setPendingUpdateAction(gleapSessionProperties);
                GleapSessionController.getInstance().executePendingUpdates();
            }
        } catch (Error | Exception exception) {
            handleError(exception, "updateContact");
        }
    }

    /**
     * Clears a user session.
     *
     * @author Gleap
     */
    @Override
    public void clearIdentity() {
        try {
            if (GleapSessionController.getInstance() != null) {
                GleapSessionController.getInstance().clearUserSession();
            }

            try {
                Runnable gleapRunnable = new Runnable() {
                    @Override
                    public void run() {
                        GleapOverlayManager.getInstance().destroyBanner(true);
                        GleapOverlayManager.getInstance().destroyModal(true, true);
                        GleapOverlayManager.getInstance().clearMessages();
                    }
                };

                if (ActivityUtil.getCurrentActivity() != null) {
                    GleapMainThread.runOnUiThread(gleapRunnable);
                } else {
                    GleapMainThread.post(gleapRunnable);
                }
            } catch (Exception ignore) {
                handleError(ignore, "clearIdentity - inner");
            }

            GleapEventService.getInstance().stop();
            GleapBaseSessionService sessionLoader = new GleapBaseSessionService();
            sessionLoader.executeOnExecutor(GleapExecutor.SERIAL);
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    /**
     * Sets the data region of your Gleap project. Supported regions: "eu" (default)
     * and "us" (case-insensitive). Sets the API url, the websocket url and the
     * realtime host at once. Unknown regions are ignored.
     * Must be called before Gleap.initialize. A manual setter (setApiUrl,
     * setWSApiUrl, setRealtimeHost) called after setRegion overrides that single host.
     * The static widget hosts (frame, banner, modal) are global and are not changed
     * by the region.
     *
     * @param region "eu" | "us"
     */
    @Override
    public void setRegion(String region) {
        try {
            GleapRegion gleapRegion = GleapRegion.fromString(region);
            if (gleapRegion == null) {
                GleapLog.w("Unknown region '" + region + "'. Supported regions: eu, us. Keeping the current hosts.");
                return;
            }

            GleapConfig.getInstance().setRegion(gleapRegion);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setRegion");
        }
    }

    /**
     * Sets the API url to your internal Gleap server. Please make sure that the
     * server is reachable within the network
     * If you use a http url pls add android:usesCleartextTraffic="true" to your
     * main activity to allow cleartext traffic
     *
     * @param apiUrl url of the internal Gleap server
     */
    @Override
    public void setApiUrl(String apiUrl) {
        GleapErrors.guard("setApiUrl", () -> GleapConfig.getInstance().setApiUrl(apiUrl));
    }

    @Override
    public void setWSApiUrl(String wsApiUrl) {
        GleapErrors.guard("setWSApiUrl", () -> GleapConfig.getInstance().setWsApiUrl(wsApiUrl));
    }

    /**
     * Sets a custom frame url.
     *
     * @param frameUrl The custom widget url.
     * @author Gleap
     */
    @Override
    public void setFrameUrl(String frameUrl) {
        GleapErrors.guard("setFrameUrl", () -> GleapConfig.getInstance().setiFrameUrl(frameUrl));
    }

    /**
     * Sets the realtime hostname used by the widget (without protocol or path).
     *
     * @param realtimeHost The realtime hostname, e.g. "sockets.gleap.io".
     * @author Gleap
     */
    @Override
    public void setRealtimeHost(String realtimeHost) {
        GleapErrors.guard("setRealtimeHost", () -> GleapConfig.getInstance().setRealtimeHost(realtimeHost));
    }

    /**
     * Sets a custom banner url.
     *
     * @param bannerUrl The custom banner url.
     * @author Gleap
     */
    @Override
    public void setBannerUrl(String bannerUrl) {
        GleapErrors.guard("setBannerUrl", () -> GleapConfig.getInstance().setBannerUrl(bannerUrl));
    }

    /**
     * Sets a custom modal url.
     *
     * @param modalUrl The custom modal url.
     * @author Gleap
     */
    @Override
    public void setModalUrl(String modalUrl) {
        GleapErrors.guard("setModalUrl", () -> GleapConfig.getInstance().setModalUrl(modalUrl));
    }

    /**
     * Set the language for the Gleap Report Flow. Otherwise the default language is
     * used.
     * Supported Languages "en", "es", "fr", "it", "de", "nl", "cz"
     * <p>
     * Call this BEFORE {@link #initialize(String, Application)} so the very first
     * config load already uses it. When it is called later, the widget config is
     * fetched again in the new language — an already open widget keeps the previous
     * copy until it is reopened.
     *
     * @param language ISO Country Code eg. "cz," "en", "de", "es", "nl"
     */
    @Override
    public void setLanguage(String language) {
        try {
            GleapConfig config = GleapConfig.getInstance();
            String previousLanguage = config.getLanguage();
            config.setLanguage(language);

            boolean languageChanged = language != null && !language.equalsIgnoreCase(previousLanguage);

            // The widget config is loaded once during initialize() and carries every
            // piece of copy already translated by the server (reply times,
            // out-of-office notice, ...). Without this reload a language set after
            // initialization would leave that copy on the previous language for the
            // rest of the process, while the session reports the new one. Before
            // initialize() there is nothing to reload — the regular config load picks
            // the language up on its own.
            if (languageChanged && config.getPlainConfig() != null) {
                new ConfigLoader(new OnHttpResponseListener() {
                    @Override
                    public void onTaskComplete(JSONObject response) {
                        // Nothing to do: ConfigLoader has already applied the config, and
                        // the widget requests it on open.
                    }
                }, true).executeOnExecutor(GleapExecutor.SERIAL, GleapBug.getInstance());
            }
        } catch (Error | Exception ignore) {
            handleError(ignore, "setLanguage");
        }
    }

    @Override
    public void setAiToolExecutedCallback(AiToolExecutedCallback aiToolExecutedCallback) {
        GleapErrors.guard("setAiToolExecutedCallback", () -> GleapCallbacks.getInstance().setAiToolExecutedCallback(aiToolExecutedCallback));
    }

    @Override
    public void setWidgetOpenedCallback(WidgetOpenedCallback widgetOpenedCallback) {
        GleapErrors.guard("setWidgetOpenedCallback", () -> GleapCallbacks.getInstance().setWidgetOpenedCallback(widgetOpenedCallback));
    }

    @Override
    public void setWidgetClosedCallback(WidgetClosedCallback widgetClosedCallback) {
        GleapErrors.guard("setWidgetClosedCallback", () -> GleapCallbacks.getInstance().setWidgetClosedCallback(widgetClosedCallback));
    }

    @Override
    public void setNotificationUnreadCountUpdatedCallback(
            NotificationUnreadCountUpdatedCallback notificationUnreadCountUpdatedCallback) {
        GleapErrors.guard("setNotificationUnreadCountUpdatedCallback", () -> GleapCallbacks.getInstance().setNotificationUnreadCountUpdatedCallback(notificationUnreadCountUpdatedCallback));
    }

    /**
     * Attach one key value pair to existing custom data.
     *
     * @param value The value you want to add
     * @param key   The key of the attribute
     * @author Gleap
     */
    @Override
    public void setCustomData(String key, String value) {
        GleapErrors.guard("setCustomData", () -> GleapBug.getInstance().setCustomData(key, value));
    }

    @Override
    public void registerAgentTool(String name, GleapAgentToolHandler handler) {
        GleapErrors.guard("registerAgentTool", () -> GleapAgentToolManager.getInstance().registerAgentTool(name, handler));
    }

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    @Override
    public void setTicketAttribute(String key, Object value) {
        GleapErrors.guard("setTicketAttribute", () -> GleapBug.getInstance().setTicketAttribute(key, value));
    }

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    @Override
    public void setTicketAttribute(String key, int value) {
        GleapErrors.guard("setTicketAttribute", () -> GleapBug.getInstance().setTicketAttribute(key, value));
    }

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    @Override
    public void setTicketAttribute(String key, double value) {
        GleapErrors.guard("setTicketAttribute", () -> GleapBug.getInstance().setTicketAttribute(key, value));
    }

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    @Override
    public void setTicketAttribute(String key, long value) {
        GleapErrors.guard("setTicketAttribute", () -> GleapBug.getInstance().setTicketAttribute(key, value));
    }

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    @Override
    public void setTicketAttribute(String key, boolean value) {
        GleapErrors.guard("setTicketAttribute", () -> GleapBug.getInstance().setTicketAttribute(key, value));
    }

    /**
     * Unsets a ticket attribute.
     *
     * @param key The key of the attribute
     * @author Gleap
     */
    @Override
    public void unsetTicketAttribute(String key) {
        GleapErrors.guard("unsetTicketAttribute", () -> GleapBug.getInstance().unsetTicketAttribute(key));
    }

    /**
     * Clears all ticket attributes.
     *
     * @author Gleap
     */
    @Override
    public void clearTicketAttributes() {
        GleapErrors.guard("clearTicketAttributes", () -> GleapBug.getInstance().clearTicketAttributes());
    }

    /**
     * Attaches custom data, which can be viewed in the Gleap dashboard. New data is merged
     * with the existing custom data; keys that already exist are overwritten.
     *
     * @param data Data, which is added
     */
    @Override
    public void attachCustomData(JSONObject data) {
        GleapErrors.guard("attachCustomData", () -> GleapBug.getInstance().setCustomData(data));
    }

    /**
     * Removes one key from existing custom data.
     *
     * @param key The key of the attribute
     * @author Gleap
     */
    @Override
    public void removeCustomDataForKey(String key) {
        GleapErrors.guard("removeCustomDataForKey", () -> GleapBug.getInstance().removeCustomData(key));
    }

    /**
     * Clears all custom data.
     */
    @Override
    public void clearCustomData() {
        GleapErrors.guard("clearCustomData", () -> GleapBug.getInstance().clearCustomData());
    }

    /**
     * Called right before a ticket (from the widget or a silent crash report) is sent, with its
     * form data as JSON text.
     *
     * @param feedbackWillBeSentCallback called before the ticket is sent
     */
    @Override
    public void setFeedbackWillBeSentCallback(FeedbackWillBeSentCallback feedbackWillBeSentCallback) {
        GleapErrors.guard("setFeedbackWillBeSentCallback", () -> GleapCallbacks.getInstance().setFeedbackWillBeSentCallback(feedbackWillBeSentCallback));
    }

    /**
     * Called once a ticket (from the widget or a silent crash report) was created, with its form
     * data.
     *
     * @param feedbackSentCallback called when the ticket was sent
     */
    @Override
    public void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback) {
        GleapErrors.guard("setFeedbackSentCallback", () -> GleapCallbacks.getInstance().setFeedbackSentCallback(feedbackSentCallback));
    }

    /**
     * This method is triggered, when an outbound is sent.
     *
     * @param outboundSentCallback this callback is called when the flow is called
     */
    @Override
    public void setOutboundSentCallback(OutboundSentCallback outboundSentCallback) {
        GleapErrors.guard("setOutboundSentCallback", () -> GleapCallbacks.getInstance().setOutboundSentCallback(outboundSentCallback));
    }

    /**
     * Called when a ticket (from the widget or a silent crash report) could not be sent, with a
     * short description of the failure.
     *
     * @param feedbackSendingFailedCallback called when sending failed
     */
    @Override
    public void setFeedbackSendingFailedCallback(FeedbackSendingFailedCallback feedbackSendingFailedCallback) {
        GleapErrors.guard("setFeedbackSendingFailedCallback", () -> GleapCallbacks.getInstance().setFeedbackSendingFailedCallback(feedbackSendingFailedCallback));
    }

    /**
     * Provides the screenshot for tickets instead of the SDK taking one. When the callback
     * returns null, the SDK takes the screenshot itself.
     *
     * @param getBitmapCallback returns the screenshot
     */
    @Override
    public void setBitmapCallback(GetBitmapCallback getBitmapCallback) {
        GleapErrors.guard("setBitmapCallback", () -> GleapCallbacks.getInstance().setGetBitmapCallback(getBitmapCallback));
    }

    /**
     * This is called, when the config is received from the server;
     *
     * @param configLoadedCallback callback which is called
     */
    @Override
    public void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback) {
        GleapErrors.guard("setConfigLoadedCallback", () -> GleapCallbacks.getInstance().setConfigLoadedCallback(configLoadedCallback));
    }

    /**
     * This is called, when the config is received from the server;
     *
     * @param initializedCallback callback which is called
     */
    @Override
    public void setInitializedCallback(InitializedCallback initializedCallback) {
        GleapErrors.guard("setInitializedCallback", () -> GleapCallbacks.getInstance().setInitializedCallback(initializedCallback));
    }

    @Override
    public void setFeedbackFlowStartedCallback(FeedbackFlowStartedCallback feedbackFlowStartedCallback) {
        GleapErrors.guard("setFeedbackFlowStartedCallback", () -> GleapCallbacks.getInstance().setFeedbackFlowStartedCallback(feedbackFlowStartedCallback));
    }

    @Override
    public void setInitializationDoneCallback(InitializationDoneCallback initializationDoneCallback) {
        GleapErrors.guard("setInitializationDoneCallback", () -> GleapCallbacks.getInstance().setInitializationDoneCallback(initializationDoneCallback));
    }

    /**
     * Network
     */

    /**
     * Replaces the attached network logs (the ones passed with the previous attachNetworkLogs call).
     * The requests recorded by the SDK itself ({@link GleapOkHttpInterceptor}, logNetwork) are kept.
     * null or an empty array removes the attached network logs.
     *
     * @param networklogs the network logs to attach
     */
    @Override
    public void attachNetworkLogs(Networklog[] networklogs) {
        GleapErrors.guard("attachNetworkLogs", () -> GleapBug.getInstance().getNetworkBuffer().attachNetworkLogs(networklogs));
    }

    /**
     * Replaces the attached network logs with entries in the Gleap network log format, e.g. the
     * requests recorded by the React Native, Flutter or Capacitor SDK. Pass the full current list:
     * each call replaces the previous one. The entries are kept as given and sent together with the
     * requests recorded by the SDK itself; the blacklist and the props to ignore are applied when a
     * ticket is sent. null or an empty array removes the attached network logs.
     * <pre>
     * { "date": "2026-09-27T10:00:00.123Z", "type": "POST", "url": "https://...", "duration": 120,
     *   "success": true,
     *   "request":  { "headers": { ... }, "payload": "..." },
     *   "response": { "status": 200, "statusText": "OK", "headers": { ... }, "responseText": "..." } }
     * </pre>
     * Failed requests have {@code "success": false} and {@code "response": { "errorText": "..." }}.
     *
     * @param networkLogs the network log entries
     */
    @Override
    public void attachNetworkLogs(JSONArray networkLogs) {
        GleapErrors.guard("attachNetworkLogs", () -> GleapBug.getInstance().getNetworkBuffer().attachNetworkLogs(networkLogs));
    }

    /**
     * Replaces the attached console logs with entries in the Gleap console log format, e.g. the
     * console output recorded by the React Native, Flutter or Capacitor SDK. Pass the full current
     * list: each call replaces the previous one. The entries are sent together with the SDK's own
     * console logs. null or an empty array removes the attached console logs.
     * <pre>
     * { "date": "2026-09-27T10:00:00.123Z", "priority": "INFO" | "WARNING" | "ERROR", "log": "..." }
     * </pre>
     *
     * @param consoleLogs the console log entries
     */
    @Override
    public void attachConsoleLogs(JSONArray consoleLogs) {
        GleapErrors.guard("attachConsoleLogs", () -> LogReader.getInstance().attachLogs(consoleLogs));
    }

    /**
     * Log network traffic by logging it manually. For OkHttp, add {@link GleapOkHttpInterceptor}
     * to the client instead.
     *
     * @param urlConnection URL where the request is sent to
     * @param requestType   the request method
     * @param status        status of the response (e.g. 200, 404), 0 when no response arrived
     * @param duration      duration of the request in milliseconds
     * @param request       request details, recommended: {@code headers} (object) and
     *                      {@code payload} (string)
     * @param response      response details, recommended: {@code headers} (object),
     *                      {@code statusText} and {@code responseText} (string); {@code errorText}
     *                      when the request failed
     */
    @Override
    public void logNetwork(String urlConnection, RequestType requestType, int status,
                           int duration, JSONObject request, JSONObject response) {
        GleapErrors.guard("logNetwork", () -> GleapHttpInterceptor.log(urlConnection, requestType, status, duration, request, response));
    }

    /**
     * Log network traffic by logging it manually. Call it after the response arrived: the url,
     * method, status and response headers are read from the connection.
     *
     * @param urlConnection the connection of the request
     * @param request       the request body, sent as its JSON text
     * @param response      the response body, sent as its JSON text
     */
    @Override
    public void logNetwork(HttpsURLConnection urlConnection, JSONObject request, JSONObject response) {
        GleapErrors.guard("logNetwork", () -> GleapHttpInterceptor.log(urlConnection, request, response));
    }

    /**
     * Log network traffic by logging it manually. Call it after the response arrived: the url,
     * method, status and response headers are read from the connection.
     *
     * @param urlConnection the connection of the request
     * @param request       the request body
     * @param response      the response body
     */
    @Override
    public void logNetwork(HttpsURLConnection urlConnection, String request, String response) {
        GleapErrors.guard("logNetwork", () -> GleapHttpInterceptor.log(urlConnection, request, response));
    }

    /**
     * Register custom functions. This custom function can be configured in the
     * widget, Form, Details of one step tab on app.Gleap.io
     *
     * @param customAction what is executed when the custom step is pressed
     */
    @Override
    public void registerCustomAction(CustomActionCallback customAction) {
        GleapErrors.guard("registerCustomAction", () -> GleapCallbacks.getInstance().registerCustomAction(customAction));
    }

    @Override
    public void registerCustomLinkHandler(CustomLinkHandlerCallback customLinkHandler) {
        GleapErrors.guard("registerCustomLinkHandler", () -> GleapCallbacks.getInstance().registerCustomLinkHandler(customLinkHandler));
    }

    @Override
    public void handleLink(String url) {
        GleapLinkHandler.handleLink(url);
    }

    public void handleGleapLink(String href) {
        GleapLinkHandler.handleGleapLink(href);
    }

    /**
     * Set Application Type
     *
     * @param applicationType "Android", "RN", "Flutter"
     */
    @Override
    public void setApplicationType(APPLICATIONTYPE applicationType) {
        GleapErrors.guard("setApplicationType", () -> GleapBug.getInstance().setApplicationType(applicationType));
    }

    /**
     * Does nothing. Use {@link #setNotificationUnreadCountUpdatedCallback(NotificationUnreadCountUpdatedCallback)}.
     */
    public void setNotificationUnreadCountUpdatedCallback() {
    }

    /**
     * Severity of the bug. Can be used in the silent bug report.
     */
    public enum SEVERITY {
        LOW, MEDIUM, HIGH
    }

    public static class GleapListener implements OnHttpResponseListener {
        public GleapListener() {
            this(true);
        }

        GleapListener(boolean startLoading) {
            if (startLoading) {
                GleapInitializer.startLoading(this);
            }
        }

        @Override
        public void onTaskComplete(JSONObject httpResponse) {
            GleapInitializer.onConfigLoaded();
        }
    }

    /**
     * Logs a custom event
     *
     * @param name Name of the event
     * @author Gleap
     */
    @Override
    public void trackEvent(String name) {
        GleapErrors.guard("trackEvent", () -> GleapBug.getInstance().logEvent(name));
    }

    /**
     * Logs a custom event with data
     *
     * @param name Name of the event
     * @param data Data passed with the event.
     * @author Gleap
     */
    @Override
    public void trackEvent(String name, JSONObject data) {
        GleapErrors.guard("trackEvent", () -> GleapBug.getInstance().logEvent(name, data));
    }

    /**
     * Attaches a file to the bug report
     *
     * @param attachment The file to attach to the bug report
     * @author Gleap
     */
    @Override
    public void addAttachment(File attachment) {
        GleapErrors.guard("addAttachment", () -> GleapFileHelper.getInstance().addAttachment(attachment));
    }

    /**
     * Removes all attachments
     *
     * @author Gleap
     */
    @Override
    public void removeAllAttachments() {
        GleapErrors.guard("removeAllAttachments", () -> GleapFileHelper.getInstance().clearAttachments());
    }

    @Override
    public void setActivationMethods(GleapActivationMethod[] activationMethods) {
        try {
            Application application = GleapInitializer.getApplication();
            if (application != null) {
                GleapConfig.getInstance().setPrioritizedActivationMethods(Arrays.asList(activationMethods));
                GleapDetectorUtil.clearAllDetectors();
                List<GleapDetector> detectorList = GleapDetectorUtil.initDetectors(application, activationMethods);
                GleapConfig.getInstance().setGestureDetectors(detectorList);
                GleapDetectorUtil.resumeAllDetectors();
            }
        } catch (Error | Exception ignore) {
            handleError(ignore, "setActivationMethods");
        }
    }

    /**
     * Prefills the widget form with data.
     *
     * @param data The data you want to prefill the form with.
     * @author Gleap
     */
    @Override
    public void preFillForm(JSONObject data) {
        GleapErrors.guard("preFillForm", () -> PrefillHelper.getInstancen().setPrefillData(data));
    }

    /**
     * Whether the widget is open (or opening).
     *
     * @return true while the widget is shown
     * @author Gleap
     */
    @Override
    public boolean isOpened() {
        return GleapDetectorUtil.isWidgetOpen();
    }

    /**
     * Manually close the feedback.
     *
     * @author Gleap
     */
    @Override
    public void close() {
        try {
            GleapMainThread.runWithActivity(new Runnable() {
                @Override
                public void run() {
                    if (GleapInitializer.getApplication() != null && GleapCallbacks.getInstance().getCallCloseCallback() != null && isOpened()) {
                        GleapCallbacks.getInstance().getCallCloseCallback().invoke();
                    }
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    /**
     * Logs a message to the Gleap activity log
     *
     * @param msg The logged message
     * @author Gleap
     */
    @Override
    public void log(String msg) {
        GleapErrors.guard("log", () -> LogReader.getInstance().log(msg, GleapLogLevel.INFO));
    }

    /**
     * Logs a message to the Gleap activity log
     *
     * @param msg The logged message
     * @author Gleap
     */
    @Override
    public void log(String msg, GleapLogLevel gleapLogLevel) {
        GleapErrors.guard("log", () -> LogReader.getInstance().log(msg, gleapLogLevel));
    }

    /**
     * Stops sending the app's logcat output with tickets. Messages logged with
     * {@link #log(String)} are still sent.
     *
     * @author Gleap
     */
    @Override
    public void disableConsoleLog() {
        GleapErrors.guard("disableConsoleLog", () -> GleapConfig.getInstance().setEnableConsoleLogsFromCode(false));
    }

    @Override
    public void showFeedbackButton(boolean show) {
        try {
            GleapConfig.getInstance().setHideFeedbackButton(!show);
            GleapConfig.getInstance().setFeedbackButtonManuallySet(true);
            GleapOverlayManager.getInstance().setShowFab(show);
        } catch (Exception ignore) {
            handleError(ignore, "showFeedbackButton");
        }
    }

    @Override
    public void setNotificationContainerOffset(int x, int y) {
        GleapConfig.getInstance().setNotificationContainerOffsetX(x);
        GleapConfig.getInstance().setNotificationContainerOffsetY(y);
    }

    @Override
    public void setTags(String[] tags) {
        GleapBug.getInstance().setTags(tags);
    }

    /**
     * Pass the current activity manually (Internal usage!)
     *
     * @param getActivityCallback get the current activity
     */
    public void setGetActivityCallback(GetActivityCallback getActivityCallback) {
        GleapErrors.guard("setGetActivityCallback", () -> GleapCallbacks.getInstance().setGetActivityCallback(getActivityCallback));
    }

    @Override
    public void closeWidgetOnExternalLinkOpen(boolean closeWidgetOnExternalLinkOpen) {
        Gleap.internalCloseWidgetOnExternalLinkOpen = closeWidgetOnExternalLinkOpen;
    }

    @Override
    public void openFeatureRequests() {
        openFeatureRequests(false);
    }

    @Override
    public void openFeatureRequests(boolean showBackButton) {
        GleapWidgetLauncher.openWithActionUnchecked("open-feature-requests",
                () -> new JSONObject().put("hideBackButton", !showBackButton),
                () -> openFeatureRequests(showBackButton),
                "openFeatureRequests");
    }

    @Override
    public GleapSessionProperties getIdentity() {
        GleapSessionProperties gleapUser = null;
        try {
            gleapUser = GleapSessionController.getInstance().getGleapUserSession();
        } catch (Error | Exception error) {
            handleError(error, "getIdentity");
        }
        return gleapUser;
    }

    @Override
    public boolean isUserIdentified() {
        try {
            GleapSessionProperties gleapUser = GleapSessionController.getInstance().getStoredGleapUser();
            if (gleapUser != null && gleapUser.getUserId() != null && !gleapUser.getUserId().equals("")) {
                return true;
            }
        } catch (Exception ex) {
            handleError(ex, "isUserIdentified");
        }
        return false;
    }

    @Override
    public void setRegisterPushMessageGroupCallback(RegisterPushMessageGroupCallback callback) {
        GleapCallbacks.getInstance().setRegisterPushMessageGroupCallback(callback);
    }

    @Override
    public void setUnRegisterPushMessageGroupCallback(UnRegisterPushMessageGroupCallback callback) {
        GleapCallbacks.getInstance().setUnRegisterPushMessageGroupCallback(callback);
    }

    public void finishImageUpload(Uri[] uris) {
        GleapConfig.getInstance().finishImageUpload(uris);
    }

    /**
     * Helper method to handle errors and exceptions.
     * If an error callback is set, it will be called with the error and context.
     * Otherwise, the error will be silently ignored.
     *
     * @param error   The error or exception that occurred
     * @param context Context information about where the error occurred
     */
    public void handleError(Throwable error, String context) {
        GleapErrors.report(error, context);
    }

    /**
     * Static helper method to handle errors and exceptions in static contexts.
     * If an error callback is set, it will be called with the error and context.
     * Otherwise, the error will be silently ignored.
     *
     * @param error   The error or exception that occurred
     * @param context Context information about where the error occurred
     */

    /**
     * Shows a modal to the user.
     *
     * @param data The modal data
     * @author Gleap
     */
    @Override
    public void showModal(JSONObject data) {
        try {
            GleapMainThread.runWithActivity(new Runnable() {
                @Override
                public void run() {
                    try {
                        GleapOverlayManager.getInstance().showModal(data, null);
                    } catch (Exception exp) {
                        handleError(exp, "showModal - inner");
                    }
                }
            });
        } catch (Exception exp) {
            handleError(exp, "showModal - outer");
        }
    }

    @Override
    public void setErrorCallback(ErrorCallback errorCallback) {
        GleapErrors.guard("setErrorCallback", () -> GleapCallbacks.getInstance().setErrorCallback(errorCallback));
    }

}