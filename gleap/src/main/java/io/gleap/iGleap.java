package io.gleap;

import org.json.JSONObject;

import java.io.File;

interface iGleap {

    /**
     * Invoke Bug Reporting
     */

    /**
     * Manually start the bug reporting workflow. This is used, when you use the activation method "NONE".
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     */
    void startFeedbackFlow() throws GleapNotInitialisedException;

    /**
     * Send a silent bugreport in the background. Useful for automated ui tests.
     *
     * @param description description of the bug
     * @param severity    Severity of the bug "LOW", "MIDDLE", "HIGH"
     */
    void sendSilentBugReport(String description, Gleap.SEVERITY severity);

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
     * Sets a custom widget url.
     *
     * @param widgetUrl The custom widget url.
     * @author Gleap
     */
    void setWidgetUrl(String widgetUrl);

    /**
     * Set the language for the Gleap Report Flow. Otherwise the default language is used.
     * Supported Languages "en", "es", "fr", "it", "de", "nl", "cz"
     *
     * @param language ISO Country Code eg. "cz," "en", "de", "es", "nl"
     */
    void setLanguage(String language);

    /**
     * Set Application Type
     *
     * @param applicationType "Android", "ReactNative", "Flutter"
     */
    void setApplicationType(APPLICATIONTYPE applicationType);

    /**
     * Custom Data
     */
    @Deprecated
    void appendCustomData(JSONObject customData);

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
     * Callbacks
     */

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
     * Customize the way, the Bitmap is generated. If this is overritten,
     * only the custom way is used
     *
     * @param getBitmapCallback get the Bitmap
     */
    void setBitmapCallback(GetBitmapCallback getBitmapCallback);

    /**
     * This is called, when the config is received from the server;
     * @param configLoadedCallback callback which is called
     */
    void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback);

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
     * Register a custom function, which can be called from the feedback report flow
     *
     * @param customAction implement the callback
     */
    void registerCustomAction(CustomActionCallback customAction);


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
}
