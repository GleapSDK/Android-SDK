package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class Gleap implements iGleap {
    private static Gleap instance;
    private static ScreenshotTaker screenshotTaker;
    private static Application application;
    private static boolean isInitialized = false;

    private Gleap() {
    }

    static final Thread.UncaughtExceptionHandler oldHandler =
            Thread.getDefaultUncaughtExceptionHandler();

    /**
     * Init Gleap with the given properties
     */
    private static void initGleap(String sdkKey, GleapActivationMethod[] activationMethods, Application application) {
        try {
            //prepare Gleap
            Gleap.application = application;
            screenshotTaker = new ScreenshotTaker();
            ConsoleUtil.clearConsole();
            //init config and load from the server
            GleapConfig.getInstance().setSdkKey(sdkKey);

            //init Gleap bug
            GleapBug.getInstance().setPhoneMeta(new PhoneMeta(application.getApplicationContext()));

            Gleap.getInstance().enableReplays(GleapConfig.getInstance().isEnableReplays());

            //start activation methods
            List<GleapDetector> detectorList = GleapDetectorUtil.initDetectors(application, activationMethods);

            if (GleapConfig.getInstance().isEnableReplays()) {
                ReplaysDetector replaysDetector = new ReplaysDetector(application);
                replaysDetector.initialize();
                detectorList.add(replaysDetector);
            }

            GleapConfig.getInstance().setGestureDetectors(detectorList);
            GleapDetectorUtil.resumeAllDetectors();

            GleapEventService.getInstance().start();
        } catch (Exception err) {
        }
    }

    /**
     * Get an instance of Gleap
     *
     * @return instance of Gleap
     */
    public static Gleap getInstance() {
        if (instance == null) {
            instance = new Gleap();
        }
        return instance;
    }

    /**
     * Auto-configures the Gleap SDK from the remote config.
     *
     * @param sdkKey      The SDK key, which can be found on dashboard.Gleap.io
     * @param application used to have context and access to take screenshot
     */
    public static void initialize(String sdkKey, Application application) {
        Gleap.application = application;
        GleapConfig.getInstance().setSdkKey(sdkKey);
        if (!isInitialized) {
            isInitialized = true;
            UserSessionController.initialize(application);
            new GleapListener();
        } else {
            if (GleapConfig.getInstance().getConfigLoadedCallback() != null && GleapConfig.getInstance().getPlainConfig() != null) {
                GleapConfig.getInstance().getConfigLoadedCallback().configLoaded(GleapConfig.getInstance().getPlainConfig());
            }
        }
    }

    /**
     * Manually shows the feedback menu or default feedback flow. This is used, when you use the activation method "NONE".
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    @Override
    public void open() {
        if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null &&
                UserSessionController.getInstance().isSessionLoaded() && instance != null) {
            try {
                if(screenshotTaker != null) {
                    screenshotTaker.takeScreenshot();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Manually start the bug reporting workflow. This is used, when you use the activation method "NONE".
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     */
    @Override
    public void startFeedbackFlow(String feedbackFlow) throws GleapNotInitialisedException {
        startFeedbackFlow(feedbackFlow, true);
    }

    @Override
    public void startFeedbackFlow(String feedbackFlow, Boolean showBackButton) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Runnable gleapRunnable = new Runnable() {
            @Override
            public void run() throws RuntimeException {
                if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
                    try {
                        JSONObject message = new JSONObject();
                        message.put("name", "start-feedbackflow");

                        JSONObject data = new JSONObject();
                        if (!feedbackFlow.equals("")) {
                            data.put("flow", feedbackFlow);
                        }

                        data.put("hideBackButton", !showBackButton);
                        message.put("data", data);
                        GleapActionQueueHandler.getInstance().addActionMessage(message);
                        screenshotTaker.takeScreenshot();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        mainHandler.post(gleapRunnable);
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity) {
        SilentBugReportUtil.createSilentBugReport(application, description, severity, new JSONObject(), null);
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity, JSONObject excludeData) {
        SilentBugReportUtil.createSilentBugReport(application, description, severity, excludeData, null);
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity, JSONObject excludeData, FeedbackSentCallback feedbackSentCallback) {
        SilentBugReportUtil.createSilentBugReport(application, description, severity, excludeData, feedbackSentCallback);
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity, FeedbackSentCallback feedbackSentCallback) {
        SilentBugReportUtil.createSilentBugReport(application, description, severity, null, feedbackSentCallback);
    }

    /**
     * Updates a session's user data.
     *
     * @param id Id of the user.
     * @author Gleap
     */
    @Override
    public void identifyUser(String id) {
        GleapUser gleapUser = new GleapUser(id);
        if (UserSessionController.getInstance() != null) {
            UserSessionController.getInstance().setGleapUserSession(gleapUser);
        }
        new GleapIdentifyService().execute();
    }

    /**
     * Updates a session's user data.
     *
     * @param id                  Id of the user.
     * @param gleapUserProperties The updated user data.
     * @author Gleap
     */
    @Override
    public void identifyUser(String id, GleapUserProperties gleapUserProperties) {
        GleapUser gleapUser = new GleapUser(id, gleapUserProperties);
        if (UserSessionController.getInstance() != null) {
            UserSessionController.getInstance().setGleapUserSession(gleapUser);
        }
        new GleapIdentifyService().execute();
    }

    /**
     * Clears a user session.
     *
     * @author Gleap
     */
    @Override
    public void clearIdentity() {
        if (UserSessionController.getInstance() != null) {
            UserSessionController.getInstance().clearUserSession();
        }
        new GleapUserSessionLoader().execute();
    }

    /**
     * Sets the API url to your internal Gleap server. Please make sure that the server is reachable within the network
     * If you use a http url pls add android:usesCleartextTraffic="true" to your main activity to allow cleartext traffic
     *
     * @param apiUrl url of the internal Gleap server
     */
    @Override
    public void setApiUrl(String apiUrl) {
        GleapConfig.getInstance().setApiUrl(apiUrl);
    }

    /**
     * Sets a custom frame url.
     *
     * @param frameUrl The custom widget url.
     * @author Gleap
     */
    @Override
    public void setFrameUrl(String frameUrl) {
        GleapConfig.getInstance().setiFrameUrl(frameUrl);
    }

    /**
     * Set the language for the Gleap Report Flow. Otherwise the default language is used.
     * Supported Languages "en", "es", "fr", "it", "de", "nl", "cz"
     *
     * @param language ISO Country Code eg. "cz," "en", "de", "es", "nl"
     */
    @Override
    public void setLanguage(String language) {
        GleapConfig.getInstance().setLanguage(language);
    }

    @Override
    public void setWidgetOpenedCallback(WidgetOpenedCallback widgetOpenedCallback) {
        GleapConfig.getInstance().setWidgetOpenedCallback(widgetOpenedCallback);
    }

    @Override
    public void setWidgetClosedCallback(WidgetClosedCallback widgetClosedCallback) {
        GleapConfig.getInstance().setWidgetClosedCallback(widgetClosedCallback);
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
        GleapBug.getInstance().setCustomData(key, value);
    }

    /**
     * Attach Data to the request. The Data will be merged into the body sent with the bugreport.
     * !!Existing keys can be overriten
     *
     * @param data Data, which is added
     */
    @Override
    public void attachCustomData(JSONObject data) {
        GleapBug.getInstance().setCustomData(data);
    }

    /**
     * Removes one key from existing custom data.
     *
     * @param key The key of the attribute
     * @author Gleap
     */
    @Override
    public void removeCustomDataForKey(String key) {
        GleapBug.getInstance().removeUserAttribute(key);
    }

    /**
     * Clears all custom data.
     */
    @Override
    public void clearCustomData() {
        GleapBug.getInstance().clearCustomData();
    }

    /**
     * This is called, when the Gleap flow is started
     *
     * @param feedbackWillBeSentCallback is called when BB is opened
     */
    @Override
    public void setFeedbackWillBeSentCallback(FeedbackWillBeSentCallback feedbackWillBeSentCallback) {
        GleapConfig.getInstance().setFeedbackWillBeSentCallback(feedbackWillBeSentCallback);
    }

    /**
     * This method is triggered, when the Gleap flow is closed
     *
     * @param feedbackSentCallback this callback is called when the flow is called
     */
    @Override
    public void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback) {
        GleapConfig.getInstance().setFeedbackSentCallback(feedbackSentCallback);
    }

    @Override
    public void setFeedbackSendingFailedCallback(FeedbackSendingFailedCallback feedbackSendingFailedCallback) {
        GleapConfig.getInstance().setFeedbackSendingFailedCallback(feedbackSendingFailedCallback);
    }


    /**
     * Customize the way, the Bitmap is generated. If this is overritten,
     * only the custom way is used
     *
     * @param getBitmapCallback get the Bitmap
     */
    @Override
    public void setBitmapCallback(GetBitmapCallback getBitmapCallback) {
        GleapConfig.getInstance().setGetBitmapCallback(getBitmapCallback);
    }

    /**
     * This is called, when the config is received from the server;
     *
     * @param configLoadedCallback callback which is called
     */
    @Override
    public void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback) {
        GleapConfig.getInstance().setConfigLoadedCallback(configLoadedCallback);
    }

    @Override
    public void setFeedbackFlowStartedCallback(FeedbackFlowStartedCallback feedbackFlowStartedCallback) {
        GleapConfig.getInstance().setFeedbackFlowStartedCallback(feedbackFlowStartedCallback);
    }

    @Override
    public void setInitializationDoneCallback(InitializationDoneCallback initializationDoneCallback) {
        GleapConfig.getInstance().setInitializationDoneCallback(initializationDoneCallback);
    }

    /**
     * Network
     */

    /**
     * Replace the current network logs.
     */
    public void attachNetworkLogs(Networklog[] networklogs){
        GleapBug.getInstance().getNetworkBuffer().attachNetworkLogs(networklogs);
    }

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
    @Override
    public void logNetwork(String urlConnection, RequestType requestType, int status, int duration, JSONObject request, JSONObject response) {
        GleapHttpInterceptor.log(urlConnection, requestType, status, duration, request, response);
    }

    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection UrlHttpConnection
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information you want and need.
     */
    @Override
    public void logNetwork(HttpsURLConnection urlConnection, JSONObject request, JSONObject response) {
        GleapHttpInterceptor.log(urlConnection, request, response);
    }


    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection UrlHttpConnection
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information you want and need.
     */
    @Override
    public void logNetwork(HttpsURLConnection urlConnection, String request, String response) {
        GleapHttpInterceptor.log(urlConnection, request, response);
    }

    /**
     * Register custom functions. This custom function can be configured in the widget, Form, Details of one step tab on app.Gleap.io
     *
     * @param customAction what is executed when the custom step is pressed
     */
    @Override
    public void registerCustomAction(CustomActionCallback customAction) {
        GleapConfig.getInstance().registerCustomAction(customAction);
    }

    /**
     * Set Application Type
     *
     * @param applicationType "Android", "RN", "Flutter"
     */
    @Override
    public void setApplicationType(APPLICATIONTYPE applicationType) {
        GleapBug.getInstance().setApplicationtype(applicationType);
    }

    /**
     * Severity of the bug. Can be used in the silent bug report.
     */
    public enum SEVERITY {
        LOW, MEDIUM, HIGH
    }

    public static class GleapListener implements OnHttpResponseListener {

        public GleapListener() {
            new ConfigLoader(this).execute(GleapBug.getInstance());
            new GleapUserSessionLoader().execute();

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        if (GleapConfig.getInstance().getAction() != null) {
                            Gleap.getInstance().startFeedbackFlow(GleapConfig.getInstance().getAction().getActionType());
                        }
                    } catch (GleapNotInitialisedException ex) {
                    }
                }
            }, 2000);   //2 seconds
        }

        @Override
        public void onTaskComplete(int httpResponse) {

            GleapConfig config = GleapConfig.getInstance();

            List<GleapActivationMethod> activationMethods = new LinkedList<>();
            if (config.isActivationMethodShake()) {
                activationMethods.add(GleapActivationMethod.SHAKE);
            }

            if (config.isActivationMethodScreenshotGesture()) {
                activationMethods.add(GleapActivationMethod.SCREENSHOT);
            }


            if (instance == null) {
                instance = new Gleap();
            }
            initGleap(GleapConfig.getInstance().getSdkKey(), activationMethods.toArray(new GleapActivationMethod[0]), application);

        }
    }

    /**
     * Logs a custom event
     *
     * @param name Name of the event
     * @author Gleap
     */
    @Override
    public void logEvent(String name) {
        GleapBug.getInstance().logEvent(name);
    }

    /**
     * Logs a custom event with data
     *
     * @param name Name of the event
     * @param data Data passed with the event.
     * @author Gleap
     */
    @Override
    public void logEvent(String name, JSONObject data) {
        GleapBug.getInstance().logEvent(name, data);
    }

    /**
     * Attaches a file to the bug report
     *
     * @param attachment The file to attach to the bug report
     * @author Gleap
     */
    @Override
    public void addAttachment(File attachment) {
        GleapFileHelper.getInstance().addAttachment(attachment);
    }

    /**
     * Removes all attachments
     *
     * @author Gleap
     */
    @Override
    public void removeAllAttachments() {
        GleapFileHelper.getInstance().clearAttachments();
    }

    @Override
    public void setActivationMethods(GleapActivationMethod[] activationMethods) {
        if (application != null) {
            GleapConfig.getInstance().setPriorizedGestureDetectors(Arrays.asList(activationMethods));
            GleapDetectorUtil.clearAllDetectors();
            List<GleapDetector> detectorList = GleapDetectorUtil.initDetectors(application, activationMethods);
            GleapConfig.getInstance().setGestureDetectors(detectorList);
            GleapDetectorUtil.resumeAllDetectors();
        }
    }

    /**
     * Prefills the widget form with data.
     * @author Gleap
     *
     * @param data The data you want to prefill the form with.
     */
    @Override
    public void preFillForm(JSONObject data) {
        PrefillHelper.getInstancen().setPrefillData(data);
    }

    /**
     * Disables the console logging. This must be called BEFORE initializing the SDK.
     * @author Gleap
     *
     */
    @Override
    public boolean isOpened() {
        return GleapDetectorUtil.isIsRunning();
    }

    /**
     * Manually close the feedback.
     * @author Gleap
     *
     */
    @Override
    public void close() {
        if (application != null && GleapConfig.getInstance().getCallCloseCallback() != null && isOpened()) {
            GleapConfig.getInstance().getCallCloseCallback().invoke();
        }
    }

    /**
     * Logs a message to the Gleap activity log
     * @author Gleap
     *
     * @param msg The logged message
     */
    @Override
    public void log(String msg) {
        LogReader.getInstance().log(msg, GleapLogLevel.INFO);
    }

    /**
     * Logs a message to the Gleap activity log
     * @author Gleap
     *
     * @param msg The logged message
     */
    @Override
    public void log(String msg, GleapLogLevel gleapLogLevel) {
        LogReader.getInstance().log(msg, gleapLogLevel);
    }

    /**
     * Disables the console logging. This must be called BEFORE initializing the SDK.
     * @author Gleap
     *
     */
    @Override
    public void disableConsoleLog() {
        GleapConfig.getInstance().setEnableConsoleLogsFromCode(false);
    }

    /**
     * Enable Replay function for BB
     * Use with care, check performance on phone
     */
    private void enableReplays(boolean enable) {
        GleapConfig.getInstance().setEnableReplays(enable);
    }

    /**
     * Pass the current activity manually (Internal usage!)
     *
     * @param getActivityCallback get the current activity
     */
    public void setGetActivityCallback(GetActivityCallback getActivityCallback) {
        GleapConfig.getInstance().setGetActivityCallback(getActivityCallback);
    }

}