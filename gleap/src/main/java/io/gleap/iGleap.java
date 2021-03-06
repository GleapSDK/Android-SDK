package io.gleap;

import org.json.JSONObject;

import java.io.File;

import javax.net.ssl.HttpsURLConnection;

import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.FeedbackFlowClosedCallback;
import io.gleap.callbacks.FeedbackFlowStartedCallback;
import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.FeedbackWillBeSentCallback;
import io.gleap.callbacks.GetBitmapCallback;
import io.gleap.callbacks.InitializationDoneCallback;
import io.gleap.callbacks.WidgetClosedCallback;
import io.gleap.callbacks.WidgetOpenedCallback;

interface iGleap {


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
     * Manually start the bug reporting workflow. This is used, when you use the activation method "NONE".
     *
     * @param feedbackFlow declares what you want to start. For example start directly a bugreport or a user rating.
     *                     use e.g. bugreporting, featurerequests, rating, contact
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     */
    void startFeedbackFlow(String feedbackFlow) throws GleapNotInitialisedException;

    void startFeedbackFlow(String feedbackFlow, Boolean showBackButton) throws GleapNotInitialisedException;

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
     */
    void identifyUser(String id);


    /**
     * Updates a session's user data.
     *
     * @param gleapUserProperties The updated user data.
     * @author Gleap
     */
    void identifyUser(String id, GleapUserProperties gleapUserProperties);


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
     * @param apiUrl url of the internal Gleap server
     */
    void setApiUrl(String apiUrl);

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
    void logEvent(String name);

    /**
     * Logs a custom event with data
     *
     * @param name Name of the event
     * @param data Data passed with the event.
     * @author Gleap
     */
    void logEvent(String name, JSONObject data);

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
     * This is called, when the widget is closed
     *
     * @param widgetClosedCallback
     */
    void setWidgetClosedCallback(WidgetClosedCallback widgetClosedCallback);


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
}


