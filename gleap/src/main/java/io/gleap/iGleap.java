package io.gleap;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

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

interface iGleap {

    /**
     * Open news or conversations by passing the notification
     * @param notificationData the data of the push notification
     */
    void handlePushNotification(JSONObject notificationData);

    /**
     * Open a conversation with the given share token
     * @param shareToken token for the conversation
     */
    void openConversation(String shareToken) throws GleapNotInitialisedException;

    /**
     * Opens the conversation of a protected file from an emailed file link (authenticated
     * conversation files). The email links to your app's URL with a {@code gleapFile} query
     * parameter; pass that URL here when your app handles it (e.g. as an App Link), and identify
     * the user with their user hash as usual. Once the SDK has a file session, it opens the
     * file's conversation if the identified user may see it; otherwise nothing happens.
     *
     * @param url the link that was opened, e.g. {@code https://app.example.com/?gleapFile=<file id>}
     * @return true when the link has a valid {@code gleapFile} parameter (24 hex characters)
     */
    boolean openProtectedFileFromUrl(String url);

    /**
     * Open the conversations tab
     */
    void openConversations() throws GleapNotInitialisedException;

    /**
     * Open the conversations tab
     *
     * @param showBackButton show the back button to the widget's home screen
     */
    void openConversations(boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Invoke Bug Reporting
     */

    /**
     * Manually shows the feedback menu or default feedback flow. This is used, when you use the activation method "NONE".
     *
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
     * @author Gleap
     */
    void openNews() throws GleapNotInitialisedException;

    /**
     * Manually shows the news section
     *
     * @author Gleap
     */
    void openNews(boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Show the checklists overview
     *
     * @author Gleap
     */
    void openChecklists() throws GleapNotInitialisedException;

    /**
     * Show the checklists overview
     *
     * @author Gleap
     */
    void openChecklists(boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Open the checklist with checklistId.
     *
     * @author Gleap
     */
    void openChecklist(String checklistId) throws GleapNotInitialisedException;

    /**
     * Open the checklist with checklistId.
     *
     * @author Gleap
     */
    void openChecklist(String checklistId, boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Start the checklist with outboundId.
     *
     * @author Gleap
     */
    void startChecklist(String outboundId) throws GleapNotInitialisedException;

    /**
     * Start the checklist with outboundId.
     *
     * @author Gleap
     */
    void startChecklist(String outboundId, boolean showBackButton) throws GleapNotInitialisedException;

    /**
     * Manually start the bug reporting workflow. This is used, when you use the activation method "NONE".
     *
     * @param feedbackFlow declares what you want to start. For example start directly a bugreport or a user rating.
     *                     use e.g. bugreporting, featurerequests, rating, contact
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
     * Ask the AI a question
     */
    void askAI(String question);
    /**
     * Asks the AI a question
     */
    void askAI(String question, Boolean showBackButton);
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
     * Leaves requests whose url contains one of these strings out of the network logs, in addition
     * to the blacklist configured in the dashboard. Requests to gleap.io and gleap.ai are always
     * left out. Each call replaces the previous list, an empty array or null resets it.
     *
     * @param blacklist url parts to leave out
     */
    void setNetworkLogsBlacklist(String[] blacklist);

    /**
     * Removes these props from the network logs before they are sent, in addition to the ones
     * configured in the dashboard: request and response headers with this name, keys in JSON
     * bodies at any depth (a prop with dots such as {@code user.password} is also a path from the
     * body root), form fields and url query parameters. Names match case-insensitively. The
     * authorization, proxy-authorization, cookie and set-cookie headers are always masked.
     * Each call replaces the previous list, an empty array or null resets it.
     *
     * @param propsToIgnore the prop names to remove
     */
    void setNetworkLogPropsToIgnore(String[] propsToIgnore);

    /**
     * Sets the env data props to ignore. These keys (e.g. deviceName) are removed from the
     * env data before a ticket is sent. Each call replaces the previous list, an empty array resets it.
     *
     * @param envDataPropsToIgnore the env data keys to ignore
     */
    void setEnvDataPropsToIgnore(String[] envDataPropsToIgnore);

    /**
     * Disables the env data. While disabled, no env data is collected at all.
     *
     * @param disableEnvData true to stop collecting env data, false to collect it again
     */
    void setDisableEnvData(boolean disableEnvData);

    /**
     * Clears a user session.
     *
     * @author Gleap
     */
    void clearIdentity();

    /**
     * Shows a modal to the user.
     *
     * @param data The modal data
     * @author Gleap
     */
    void showModal(JSONObject data);

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
     * Unsets a ticket attribute.
     *
     * @param key The key of the attribute
     * @author Gleap
     */
    void unsetTicketAttribute(String key);

    /**
     * Clears all ticket attributes.
     *
     * @author Gleap
     */
    void clearTicketAttributes();

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
     * Sets the data region of your Gleap project. Supported regions: "eu" (default) and "us" (case-insensitive).
     * Sets the API url, the websocket url and the realtime host at once. Unknown regions are ignored.
     * Must be called before Gleap.initialize. A manual setter (setApiUrl, setWSApiUrl, setRealtimeHost)
     * called after setRegion overrides that single host.
     * The static widget hosts (frame, banner, modal) are global and are not changed by the region.
     *
     * @param region "eu" | "us"
     */
    void setRegion(String region);

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
     * Sets the realtime hostname used by the widget (without protocol or path), e.g. "sockets.gleap.io".
     *
     * @param realtimeHost The realtime hostname.
     * @author Gleap
     */
    void setRealtimeHost(String realtimeHost);

    /**
     * Sets a custom banner url.
     *
     * @param bannerUrl The custom banner url.
     * @author Gleap
     */
    void setBannerUrl(String bannerUrl);

    /**
     * Sets a custom modal url.
     *
     * @param modalUrl The custom modal url.
     * @author Gleap
     */
    void setModalUrl(String modalUrl);

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
     * Called right before a ticket (from the widget or a silent crash report) is sent, with its
     * form data as JSON text.
     *
     * @param feedbackWillBeSentCallback called before the ticket is sent
     */
    void setFeedbackWillBeSentCallback(FeedbackWillBeSentCallback feedbackWillBeSentCallback);

    /**
     * Called once a ticket (from the widget or a silent crash report) was created, with its form
     * data.
     *
     * @param feedbackSentCallback called when the ticket was sent
     */
    void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback);

    /**
     * This method is triggered, when an outbound message got answered
     *
     * @param outboundSentCallback this callback is called when the flow is called
     */
    void setOutboundSentCallback(OutboundSentCallback outboundSentCallback);

    /**
     * Called when a ticket (from the widget or a silent crash report) could not be sent, with a
     * short description of the failure.
     *
     * @param feedbackSendingFailedCallback called when sending failed
     */
    void setFeedbackSendingFailedCallback(FeedbackSendingFailedCallback feedbackSendingFailedCallback);

    /**
     * Provides the screenshot for tickets instead of the SDK taking one. When the callback
     * returns null, the SDK takes the screenshot itself.
     *
     * @param getBitmapCallback get the Bitmap
     */
    void setBitmapCallback(GetBitmapCallback getBitmapCallback);

    /**
     * This is called, when the config is received from the server. The config is loaded once per
     * process: a callback set after it was loaded is called once with the loaded config, posted
     * to the main thread. Calling {@link Gleap#initialize} again with the same SDK key hands the
     * loaded config to the set callback again, like on iOS.
     *
     * @param configLoadedCallback callback which is called
     */
    void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback);

    /**
     * This is called, when Gleap got initialized (the config was received from the server). A
     * callback set after that is called once, posted to the main thread. Calling
     * {@link Gleap#initialize} again with the same SDK key calls the set callback again, like on
     * iOS.
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
     * Replaces the attached network logs (the ones passed with the previous attachNetworkLogs call).
     * The requests recorded by the SDK itself ({@link GleapOkHttpInterceptor}, logNetwork) are kept.
     * null or an empty array removes the attached network logs.
     *
     * @param networklogs the network logs to attach
     */
    void attachNetworkLogs(Networklog[] networklogs);

    /**
     * Replaces the attached network logs with entries in the Gleap network log format, e.g. the
     * requests recorded by the React Native, Flutter or Capacitor SDK. Pass the full current list:
     * each call replaces the previous one. The entries are kept as given and sent together with the
     * requests recorded by the SDK itself; the blacklist and the props to ignore are applied when a
     * ticket is sent. null or an empty array removes the attached network logs.
     *
     * @param networkLogs the network log entries
     */
    void attachNetworkLogs(JSONArray networkLogs);

    /**
     * Replaces the attached console logs with entries in the Gleap console log format
     * ({@code date}, {@code priority} INFO / WARNING / ERROR, {@code log}), e.g. the console output
     * recorded by the React Native, Flutter or Capacitor SDK. Pass the full current list: each call
     * replaces the previous one. null or an empty array removes the attached console logs.
     *
     * @param consoleLogs the console log entries
     */
    void attachConsoleLogs(JSONArray consoleLogs);

    /**
     * Log network traffic by logging it manually. For OkHttp, add {@link GleapOkHttpInterceptor}
     * to the client instead.
     *
     * @param urlConnection URL where the request is sent to
     * @param requestType   the request method
     * @param status        status of the response (e.g. 200, 404), 0 when no response arrived
     * @param duration      duration of the request in milliseconds
     * @param request       request details, recommended: {@code headers} (object) and {@code payload} (string)
     * @param response      response details, recommended: {@code headers} (object), {@code statusText} and
     *                      {@code responseText} (string); {@code errorText} when the request failed
     */
    void logNetwork(String urlConnection, RequestType requestType, int status, int duration, JSONObject request, JSONObject response);


    /**
     * Log network traffic by logging it manually. Call it after the response arrived: the url,
     * method, status and response headers are read from the connection.
     *
     * @param urlConnection the connection of the request
     * @param request       the request body, sent as its JSON text
     * @param response      the response body, sent as its JSON text
     */
    void logNetwork(HttpsURLConnection urlConnection, JSONObject request, JSONObject response);

    /**
     * Log network traffic by logging it manually. Call it after the response arrived: the url,
     * method, status and response headers are read from the connection.
     *
     * @param urlConnection the connection of the request
     * @param request       the request body
     * @param response      the response body
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
     * Stops sending the app's logcat output with tickets. Messages logged with {@link #log(String)}
     * are still sent.
     * @author Gleap
     *
     */
    void disableConsoleLog();

    void showFeedbackButton(boolean show);

    /**
     * Sets the X/Y offset (in dp) for the notification container when the feedback button is hidden.
     * Default is 0, 0.
     */
    void setNotificationContainerOffset(int x, int y);

    /**
     * Sets the widget color scheme. Overrides the color scheme set in the dashboard, which
     * applies until this is called. Only takes effect when "Adapt to dark / light mode" is
     * enabled in the dashboard; while it is disabled the widget always keeps the dashboard colors.
     * "auto" follows the app's dark / light mode, "light" / "dark" force a scheme; any other
     * value is treated as "auto". Dark mode uses the dark colors set in the dashboard and also
     * the dark logo, header image and composer glow set there; without dark colors the widget
     * keeps its normal colors. Can be called before or after initialize.
     *
     * @param colorScheme "auto", "light" or "dark"
     */
    void setColorScheme(String colorScheme);

    /**
     * Sets the widget color scheme and the background colors used for it. Only takes effect
     * when "Adapt to dark / light mode" is enabled in the dashboard. Dark mode uses the
     * dark colors set in the dashboard (header colors, UI color, background); without dark
     * colors the widget keeps its normal colors.
     *
     * @param colorScheme          "auto", "light" or "dark"
     * @param lightBackgroundColor background (#rrggbb) in light mode, null for the dashboard background
     * @param darkBackgroundColor  background (#rrggbb) in dark mode, null for the dashboard's dark background
     */
    void setColorScheme(String colorScheme, @Nullable String lightBackgroundColor, @Nullable String darkBackgroundColor);

    void openFeatureRequests();

    void openFeatureRequests(boolean showBackButton);

    void closeWidgetOnExternalLinkOpen(boolean closeWidgetOnExternalLinkOpen);

    GleapSessionProperties getIdentity();

    boolean isUserIdentified();

    void setRegisterPushMessageGroupCallback(RegisterPushMessageGroupCallback callback);

    void setUnRegisterPushMessageGroupCallback(UnRegisterPushMessageGroupCallback callback);

    void setTags(String[] tags);

    /**
     * Registers the handler for a Frontend tool defined on your AI agent in the
     * Gleap dashboard. The agent calls the handler with the configured parameters
     * and waits for the result passed to the callback (String or JSON object,
     * which gets stringified).
     *
     * @param name    The tool's runtime name as defined on the AI agent.
     * @param handler The handler to execute the tool. Call the callback exactly once with the result.
     */
    void registerAgentTool(String name, GleapAgentToolHandler handler);

    void handleLink(String url);

    /**
     * Sets a callback to handle errors and exceptions that occur within the Gleap SDK.
     * If no callback is set, errors will be silently ignored.
     *
     * @param errorCallback The callback to handle errors
     */
    void setErrorCallback(ErrorCallback errorCallback);
}