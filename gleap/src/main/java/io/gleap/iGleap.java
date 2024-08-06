package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import javax.net.ssl.HttpsURLConnection;

import io.gleap.callbacks.AiToolExecutedCallback;
import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.CustomLinkHandlerCallback;
import io.gleap.callbacks.FeedbackFlowStartedCallback;
import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.FeedbackWillBeSentCallback;
import io.gleap.callbacks.GetBitmapCallback;
import io.gleap.callbacks.InitializationDoneCallback;
import io.gleap.callbacks.InitializedCallback;
import io.gleap.callbacks.NotificationUnreadCountUpdatedCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;
import io.gleap.callbacks.WidgetClosedCallback;
import io.gleap.callbacks.WidgetOpenedCallback;

interface iGleap {

    /**
     * Open news or conversations by passing the notification
     * @param notificationData push notitification
     */
    void handlePushNotification(JSONObject notificationData);

    /**
     * Open a conversation with the given sharetoken
     * @param shareToken token for the conversation
     */
    void openConversation(String shareToken) throws GleapNotInitialisedException;

    /**
     * Open the conversations tab
     */
    void openConversations() throws GleapNotInitialisedException;

    /**
     * Open the conversations tab
     */
    void openConversations(boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Invoke Bug Reporting
     */

    /**
     * Manually shows the feedback menu or default feedback flow. This is used, when you use the activation method "NONE".
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void open() throws GleapNotInitialisedException;

    /**
     * Disable in-app notifications. This is useful, when you want to use your own in-app notifications UI.
     *
     * @author Gleap
     */
    void setDisableInAppNotifications(boolean disableInAppNotifications);

    /**
     * Manually shows the news section
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void openNews() throws GleapNotInitialisedException;

    /**
     * Manually shows the news section
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void openNews(boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Show the checklists overview
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void openChecklists() throws GleapNotInitialisedException;

    /**
     * Show the checklists overview
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void openChecklists(boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Open the checklist with checklistId.
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void openChecklist(String checklistId) throws GleapNotInitialisedException;

    /**
     * Open the checklist with checklistId.
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void openChecklist(String checklistId, boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Start the checklist with outboundId.
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void startChecklist(String outboundId) throws GleapNotInitialisedException;

    /**
     * Start the checklist with outboundId.
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    void startChecklist(String outboundId, boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Manually start the bug reporting workflow. This is used, when you use the activation method "NONE".
     *
     * @param feedbackFlow declares what you want to start. For example start directly a bugreport or a user rating.
     *                     use e.g. bugreporting, featurerequests, rating, contact
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     */
    void startFeedbackFlow(String feedbackFlow);

    void startFeedbackFlow(String feedbackFlow, Boolean showBackButton);

    void startClassicForm(String formId);

    void startClassicForm(String formId, Boolean showBackButton);

    void startConversation();

    void startConversation(boolean showBackButton);

    void startBot(String botId);

    void startBot(String botId, boolean showBackButton);

    void showSurvey(String surveyId);

    void showSurvey(String surveyId, SurveyType surveyType);

    /**
     * Opens the help center.
     */
    void openHelpCenter();
    /**
     * Opens the help center.
     */
    void openHelpCenter(Boolean showBackButton);
    /**
     * Opens a help article
     */
    void openHelpCenterArticle(String articleId);
    /**
     * Opens a help article
     */
    void openHelpCenterArticle(String articleId, Boolean showBackButton);
    /**
     * Opens a help article
     */
    void openHelpCenterCollection(String collectionId);
    /**
     * Opens a help article
     */
    void openHelpCenterCollection(String collectionId, Boolean showBackButton);
    /**
     * Search for news articles in the help center
     */
    void searchHelpCenter(String term);
    /**
     * Search for news articles in the help center
     */
    void searchHelpCenter(String term, Boolean showBackButton);

    /**
     * Send a silent bugreport in the background. Useful for automated ui tests.
     *
     * @param description description of the bug
     * @param severity    Severity of the bug "LOW", "MIDDLE", "HIGH"
     */
    void sendSilentCrashReport(String description, Gleap.SEVERITY severity);

    void sendSilentCrashReport(String description, Gleap.SEVERITY severity, JSONObject excludeData);

    void sendSilentCrashReport(String description, Gleap.SEVERITY severity, FeedbackSentCallback feedbackSentCallback);

    void sendSilentCrashReport(String description, Gleap.SEVERITY severity, JSONObject excludeData, FeedbackSentCallback feedbackSentCallback);

    /**
     * Updates a session's user data.
     *
     * @param id The updated user data.
     * @author Gleap
     * @deprecated use {@link #identifyContact(String)} instead.
     */
    void identifyUser(String id);

    /**
     * Updates a session's user data.
     *
     * @param gleapSessionProperties The updated user data.
     * @author Gleap
     * @deprecated use {@link #identifyContact(String, GleapSessionProperties)} instead.
     */
    void identifyUser(String id, GleapSessionProperties gleapSessionProperties);

    /**
     * Updates a session's user data.
     *
     * @param gleapSessionProperties The updated user data.
     * @author Gleap
     * @deprecated use {@link #identifyContact(String, GleapSessionProperties)} instead.
     */
    void identifyUser(String id, GleapSessionProperties gleapSessionProperties, JSONObject customData);

    /**
     * Identifies a contact.
     *
     * @param id The updated user data.
     * @author Gleap
     */
    void identifyContact(String id);

    /**
     * Identifies a contact with data.
     *
     * @param gleapSessionProperties The updated user data.
     * @author Gleap
     */
    void identifyContact(String id, GleapSessionProperties gleapSessionProperties);

    /**
     * Updates session data.
     *
     * @param gleapSessionProperties The updated user data.
     * @author Gleap
     */
    void updateContact(GleapSessionProperties gleapSessionProperties);

    /**
     * Sets the network log blacklist.
     * @param blacklist
     */
    void setNetworkLogsBlacklist(String[] blacklist);

    /**
     * Sets the network log props to ignore.
     * @param propsToIgnore
     */
    void setNetworkLogPropsToIgnore(String[] propsToIgnore);

    /**
     * Clears a user session.
     *
     * @author Gleap
     */
    void clearIdentity();

    /**
     * Attaches custom data, which can be viewed in the Gleap dashboard. New data will be merged with existing custom data.
     *
     * @param customData The data to attach to a bug report.
     * @author Gleap
     */
    void attachCustomData(JSONObject customData);

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    void setTicketAttribute(String key, Object value);

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    void setTicketAttribute(String key, int value);

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    void setTicketAttribute(String key, double value);

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    void setTicketAttribute(String key, long value);

    /**
     * Sets the value of a ticket attribute.
     *
     * @param key   The key of the attribute
     * @param value The value you want to add
     * @author Gleap
     */
    void setTicketAttribute(String key, boolean value);

    /**
     * Attach one key value pair to existing custom data.
     *
     * @param value The value you want to add
     * @param key   The key of the attribute
     * @author Gleap
     */
    void setCustomData(String key, String value);

    /**
     * Removes one key from existing custom data.
     *
     * @param key The key of the attribute
     * @author Gleap
     */
    void removeCustomDataForKey(String key);

    /**
     * Clears all custom data.
     *
     * @author Gleap
     */
    void clearCustomData();

    /**
     * Configure Gleap
     */
    /**
     * Sets the API url to your internal Gleap server. Please make sure that the server is reachable within the network
     * If you use a http url pls add android:usesCleartextTraffic="true" to your main activity to allow cleartext traffic
     *
     * @param apiUrl url of the Gleap api server
     */
    void setApiUrl(String apiUrl);

    /**
     * Sets the ws server url to your internal Gleap server. Please make sure that the server is reachable within the network
     * The ws url must start with the wss:// protocol, for a secure websocket server connection.
     *
     * @param wsApiUrl url of the Gleap websocket server
     */
    void setWSApiUrl(String wsApiUrl);

    /**
     * Sets a custom frame url.
     *
     * @param frameUrl The custom frame url.
     * @author Gleap
     */
    void setFrameUrl(String frameUrl);

    /**
     * Set the language for the Gleap Report Flow. Otherwise the default language is used.
     * Supported Languages "en", "es", "fr", "it", "de", "nl", "cz"
     *
     * @param language ISO Country Code eg. "cz," "en", "de", "es", "nl"
     */
    void setLanguage(String language);

    /**
     * Logs a custom event
     *
     * @param name Name of the event
     * @author Gleap
     */
    void trackEvent(String name);

    /**
     * Logs a custom event with data
     *
     * @param name Name of the event
     * @param data Data passed with the event.
     * @author Gleap
     */
    void trackEvent(String name, JSONObject data);

    /**
     * Attaches a file to the feedback
     *
     * @param file The file to attach to the feedback report
     * @author Gleap
     */
    void addAttachment(File file);

    /**
     * Removes all attachments
     *
     * @author Gleap
     */
    void removeAllAttachments();

    /**
     * Set Application Type
     *
     * @param applicationType "Android", "ReactNative", "Flutter"
     */
    void setApplicationType(APPLICATIONTYPE applicationType);

    /**
     * Callbacks
     */

    /**
     * This is called, when the widget is opened
     *
     * @param widgetOpenedCallback
     */
    void setWidgetOpenedCallback(WidgetOpenedCallback widgetOpenedCallback);

    /**
     * This is called, when an ai tool gets executed.
     *
     * @param aiToolExecutedCallback
     */
    void setAiToolExecutedCallback(AiToolExecutedCallback aiToolExecutedCallback);

    /**
     * This is called, when the widget is closed
     *
     * @param widgetClosedCallback
     */
    void setWidgetClosedCallback(WidgetClosedCallback widgetClosedCallback);

    /**
     * This is called, when the widget is opened
     *
     * @param notificationUnreadCountUpdatedCallback
     */
    void setNotificationUnreadCountUpdatedCallback(NotificationUnreadCountUpdatedCallback notificationUnreadCountUpdatedCallback);

    /**
     * This is called, when the Gleap flow is started
     *
     * @param feedbackWillBeSentCallback is called when BB is opened
     */
    void setFeedbackWillBeSentCallback(FeedbackWillBeSentCallback feedbackWillBeSentCallback);

    /**
     * This method is triggered, when the Gleap flow is closed
     *
     * @param feedbackSentCallback this callback is called when the flow is called
     */
    void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback);

    /**
     * This is called if the sending has failed
     *
     * @param feedbackSendingFailedCallback
     */
    void setFeedbackSendingFailedCallback(FeedbackSendingFailedCallback feedbackSendingFailedCallback);

    /**
     * Customize the way, the Bitmap is generated. If this is overritten,
     * only the custom way is used
     *
     * @param getBitmapCallback get the Bitmap
     */
    void setBitmapCallback(GetBitmapCallback getBitmapCallback);

    /**
     * This is called, when the config is received from the server;
     *
     * @param configLoadedCallback callback which is called
     */
    void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback);

    /**
     * This is called, when Gleap got initialized;
     *
     * @param initializedCallback callback which is called
     */
    void setInitializedCallback(InitializedCallback initializedCallback);

    /**
     * Called if actually a user is starting a flow, not only the widget opens
     *
     * @param feedbackFlowStartedCallback
     */
    void setFeedbackFlowStartedCallback(FeedbackFlowStartedCallback feedbackFlowStartedCallback);

    /**
     * Called if the initialization is done.
     * @param initializationDoneCallback
     */
    void setInitializationDoneCallback(InitializationDoneCallback initializationDoneCallback);

    /**
     * Network
     */

    /**
     * Replace the current network logs.
     */
    void attachNetworkLogs(Networklog[] networklogs);
    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection URL where the request is sent to
     * @param requestType   GET, POST, PUT, DELETE
     * @param status        status of the response (e.g. 200, 404)
     * @param duration      duration of the request
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information you want and need.
     */
    void logNetwork(String urlConnection, RequestType requestType, int status, int duration, JSONObject request, JSONObject response);


    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection UrlHttpConnection
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information you want and need.
     */
    void logNetwork(HttpsURLConnection urlConnection, JSONObject request, JSONObject response);

    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection UrlHttpConnection
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information you want and need.
     */
    void logNetwork(HttpsURLConnection urlConnection, String request, String response);

    /**
     * Register a custom function, which can be called from the feedback report flow
     *
     * @param customAction implement the callback
     */
    void registerCustomAction(CustomActionCallback customAction);

    /**
     * Register a custom function, that handles links.
     *
     * @param customLinkHandler implement the callback
     */
    void registerCustomLinkHandler(CustomLinkHandlerCallback customLinkHandler);

    /**
     * Set the activation Methods manually
     *
     * @param activationMethods set of activation methods
     */
    void setActivationMethods(GleapActivationMethod[] activationMethods);


    /**
     * Prefills the widget form with data.
     *
     * @param data The data you want to prefill the form with.
     * @author Gleap
     */
    void preFillForm(JSONObject data);

    /**
     * Returns the widget state
     * @author Gleap
     */
    boolean isOpened();


    /**
     * Manually close the feedback.
     * @author Gleap
     *
     */
    void close();

    /**
     * Logs a message to the Gleap activity log
     * @author Gleap
     *
     * @param msg The logged message
     */
    void log(String msg);

    /**
     * Logs a message to the Gleap activity log
     * @author Gleap
     *
     * @param msg The logged message
     * @param gleapLogLevel loglevel INFO, WARNING, ERROR
     */
    void log(String msg, GleapLogLevel gleapLogLevel);

    /**
     * Disables the console logging. This must be called BEFORE initializing the SDK.
     * @author Gleap
     *
     */
    void disableConsoleLog();

    void showFeedbackButton(boolean show);

    void openFeatureRequests();

    void openFeatureRequests(boolean showBackButton);

    GleapSessionProperties getIdentity();

    boolean isUserIdentified();

    void setRegisterPushMessageGroupCallback(RegisterPushMessageGroupCallback callback);

    void setUnRegisterPushMessageGroupCallback(UnRegisterPushMessageGroupCallback callback);

    void setTags(String[] tags);

    void setAiTools(GleapAiTool[] aiTools);

    void handleLink(String url);
}