package io.gleap;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.FeedbackFlowStartedCallback;
import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.FeedbackWillBeSentCallback;
import io.gleap.callbacks.GetActivityCallback;
import io.gleap.callbacks.GetBitmapCallback;
import io.gleap.callbacks.InitializationDoneCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;
import io.gleap.callbacks.WidgetClosedCallback;
import io.gleap.callbacks.WidgetOpenedCallback;

public class Gleap implements iGleap {
    private static Gleap instance;
    private static ScreenshotTaker screenshotTaker;
    private static Application application;
    private static boolean isInitialized = false;

    private Gleap() {
    }

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

            //start services
            GleapActivityManager.getInstance().start(application);
            GleapEventService.getInstance().start();

            GleapConfig.getInstance().setGestureDetectors(detectorList);
            GleapDetectorUtil.resumeAllDetectors();
        } catch (Exception ignore) {
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
        try {
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
        } catch (Error | Exception ignore) {
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
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null &&
                                        UserSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } catch (Error | Exception ignore) {
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Manually shows the news section
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
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
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     * @author Gleap
     */
    public void openNews(boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null &&
                                        UserSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-news", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } catch (Error | Exception ignore) {
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    public void openNewsArticle(String articleId) {
        openNewsArticle(articleId, false);
    }

    public void openNewsArticle(String articleId, boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null &&
                                        UserSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            message.put("id", articleId);
                                            GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-news-article", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                            } catch (Error | Exception ignore) {
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Manually start the bug reporting workflow. This is used, when you use the activation method "NONE".
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
     */
    @Override
    public void startFeedbackFlow(String feedbackFlow) throws GleapNotInitialisedException {
        try {
            startFeedbackFlow(feedbackFlow, true);
        } catch (Error | Exception ignore) {
        }
    }


    @Override
    public void startFeedbackFlow(String feedbackFlow, Boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
                                try {
                                    JSONObject data = new JSONObject();
                                    if (!feedbackFlow.equals("")) {
                                        data.put("flow", feedbackFlow);
                                    }
                                    data.put("hideBackButton", !showBackButton);
                                    GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("start-feedbackflow", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }


    //survey, survey_full

    @Override
    public void showSurvey(String surveyId) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("isSurvey", true);
            jsonObject.put("hideBackButton", true);
            jsonObject.put("format", "survey");
            jsonObject.put("flow", surveyId);
        } catch (Exception ex) {
        }
        //check if it isopen
        GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("start-survey", jsonObject));
        Gleap.getInstance().open();
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
        }
        //check if it isopen
        GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("start-survey", jsonObject));
        Gleap.getInstance().open();
    }

    @Override
    public void openHelpCenter() {
        openHelpCenter(false);
    }

    @Override
    public void openHelpCenter(Boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-helpcenter", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void openHelpCenterArticle(String articleId) {
        openHelpCenterArticle(articleId, false);
    }

    @Override
    public void openHelpCenterArticle(String articleId, Boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    data.put("articleId", articleId);
                                    GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-help-article", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void openHelpCenterCollection(String collectionId) {
        openHelpCenterArticle(collectionId, false);
    }

    @Override
    public void openHelpCenterCollection(String collectionId, Boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    data.put("collectionId", collectionId);
                                    GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-help-collection", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void searchHelpCenter(String term) {
        searchHelpCenter(term, false);
    }

    @Override
    public void searchHelpCenter(String term, Boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    data.put("term", term);
                                    GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-helpcenter-search", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity) {
        try {
            SilentBugReportUtil.createSilentBugReport(application, description, severity, new JSONObject(), null);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity, JSONObject
            excludeData) {
        try {
            SilentBugReportUtil.createSilentBugReport(application, description, severity, excludeData, null);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity, JSONObject
            excludeData, FeedbackSentCallback feedbackSentCallback) {
        try {
            SilentBugReportUtil.createSilentBugReport(application, description, severity, excludeData, feedbackSentCallback);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY
            severity, FeedbackSentCallback feedbackSentCallback) {
        try {
            SilentBugReportUtil.createSilentBugReport(application, description, severity, null, feedbackSentCallback);
        } catch (Error | Exception ignore) {
        }

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
            GleapUser gleapUser = new GleapUser(id);
            if (UserSessionController.getInstance() != null) {
                UserSessionController.getInstance().setGleapUserSession(gleapUser);
            }
            new GleapIdentifyService().execute();
        } catch (Error | Exception exception) {
        }
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
        try {
            GleapUser gleapUser = new GleapUser(id, gleapUserProperties);
            if (UserSessionController.getInstance() != null) {
                UserSessionController.getInstance().setGleapUserSession(gleapUser);
            }
            new GleapIdentifyService().execute();
        } catch (Error | Exception exception) {
        }
    }

    @Override
    public void identifyUser(String id, GleapUserProperties gleapUserProperties, JSONObject customData) {
        GleapUser gleapUser = new GleapUser(id, gleapUserProperties);
        gleapUserProperties.setCustomData(customData);
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
        try {
            if (UserSessionController.getInstance() != null) {
                UserSessionController.getInstance().clearUserSession();
            }
            try {
                ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GleapInvisibleActivityManger.getInstance().clearMessages();
                    }
                });
            } catch (Exception ignore) {
            }
            new GleapUserSessionLoader().execute();

        } catch (Error | Exception ignore) {
        }
        GleapUserSessionLoader sessionLoader = new GleapUserSessionLoader();
        sessionLoader.execute();

    }

    /**
     * Sets the API url to your internal Gleap server. Please make sure that the server is reachable within the network
     * If you use a http url pls add android:usesCleartextTraffic="true" to your main activity to allow cleartext traffic
     *
     * @param apiUrl url of the internal Gleap server
     */
    @Override
    public void setApiUrl(String apiUrl) {
        try {
            GleapConfig.getInstance().setApiUrl(apiUrl);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Sets a custom frame url.
     *
     * @param frameUrl The custom widget url.
     * @author Gleap
     */
    @Override
    public void setFrameUrl(String frameUrl) {
        try {
            GleapConfig.getInstance().setiFrameUrl(frameUrl);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Set the language for the Gleap Report Flow. Otherwise the default language is used.
     * Supported Languages "en", "es", "fr", "it", "de", "nl", "cz"
     *
     * @param language ISO Country Code eg. "cz," "en", "de", "es", "nl"
     */
    @Override
    public void setLanguage(String language) {
        try {
            GleapConfig.getInstance().setLanguage(language);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void setWidgetOpenedCallback(WidgetOpenedCallback widgetOpenedCallback) {
        try {
            GleapConfig.getInstance().setWidgetOpenedCallback(widgetOpenedCallback);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void setWidgetClosedCallback(WidgetClosedCallback widgetClosedCallback) {
        try {
            GleapConfig.getInstance().setWidgetClosedCallback(widgetClosedCallback);

        } catch (Error | Exception ignore) {
        }

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
        try {
            GleapBug.getInstance().setCustomData(key, value);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Attach Data to the request. The Data will be merged into the body sent with the bugreport.
     * !!Existing keys can be overriten
     *
     * @param data Data, which is added
     */
    @Override
    public void attachCustomData(JSONObject data) {
        try {
            GleapBug.getInstance().setCustomData(data);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Removes one key from existing custom data.
     *
     * @param key The key of the attribute
     * @author Gleap
     */
    @Override
    public void removeCustomDataForKey(String key) {
        try {
            GleapBug.getInstance().removeUserAttribute(key);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Clears all custom data.
     */
    @Override
    public void clearCustomData() {
        try {
            GleapBug.getInstance().clearCustomData();

        } catch (Error | Exception ignore) {
        }
    }

    /**
     * This is called, when the Gleap flow is started
     *
     * @param feedbackWillBeSentCallback is called when BB is opened
     */
    @Override
    public void setFeedbackWillBeSentCallback(FeedbackWillBeSentCallback
                                                      feedbackWillBeSentCallback) {
        try {
            GleapConfig.getInstance().setFeedbackWillBeSentCallback(feedbackWillBeSentCallback);
        } catch (Error | Exception ignore) {
        }

    }

    /**
     * This method is triggered, when the Gleap flow is closed
     *
     * @param feedbackSentCallback this callback is called when the flow is called
     */
    @Override
    public void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback) {
        try {
            GleapConfig.getInstance().setFeedbackSentCallback(feedbackSentCallback);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void setFeedbackSendingFailedCallback(FeedbackSendingFailedCallback
                                                         feedbackSendingFailedCallback) {
        try {
            GleapConfig.getInstance().setFeedbackSendingFailedCallback(feedbackSendingFailedCallback);
        } catch (Error | Exception ignore) {
        }
    }


    /**
     * Customize the way, the Bitmap is generated. If this is overritten,
     * only the custom way is used
     *
     * @param getBitmapCallback get the Bitmap
     */
    @Override
    public void setBitmapCallback(GetBitmapCallback getBitmapCallback) {
        try {
            GleapConfig.getInstance().setGetBitmapCallback(getBitmapCallback);
        } catch (Error | Exception ignore) {
        }

    }

    /**
     * This is called, when the config is received from the server;
     *
     * @param configLoadedCallback callback which is called
     */
    @Override
    public void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback) {
        try {
            GleapConfig.getInstance().setConfigLoadedCallback(configLoadedCallback);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void setFeedbackFlowStartedCallback(FeedbackFlowStartedCallback
                                                       feedbackFlowStartedCallback) {
        try {
            GleapConfig.getInstance().setFeedbackFlowStartedCallback(feedbackFlowStartedCallback);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void setInitializationDoneCallback(InitializationDoneCallback
                                                      initializationDoneCallback) {
        try {
            GleapConfig.getInstance().setInitializationDoneCallback(initializationDoneCallback);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Network
     */

    /**
     * Replace the current network logs.
     */
    public void attachNetworkLogs(Networklog[] networklogs) {
        try {
            GleapBug.getInstance().getNetworkBuffer().attachNetworkLogs(networklogs);
        } catch (Error | Exception ignore) {
        }
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
    public void logNetwork(String urlConnection, RequestType requestType, int status,
                           int duration, JSONObject request, JSONObject response) {
        try {
            GleapHttpInterceptor.log(urlConnection, requestType, status, duration, request, response);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection UrlHttpConnection
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information you want and need.
     */
    @Override
    public void logNetwork(HttpsURLConnection urlConnection, JSONObject request, JSONObject
            response) {
        try {
            GleapHttpInterceptor.log(urlConnection, request, response);
        } catch (Error | Exception ignore) {
        }
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
        try {
            GleapHttpInterceptor.log(urlConnection, request, response);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Register custom functions. This custom function can be configured in the widget, Form, Details of one step tab on app.Gleap.io
     *
     * @param customAction what is executed when the custom step is pressed
     */
    @Override
    public void registerCustomAction(CustomActionCallback customAction) {
        try {
            GleapConfig.getInstance().registerCustomAction(customAction);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Set Application Type
     *
     * @param applicationType "Android", "RN", "Flutter"
     */
    @Override
    public void setApplicationType(APPLICATIONTYPE applicationType) {
        try {
            GleapBug.getInstance().setApplicationtype(applicationType);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Severity of the bug. Can be used in the silent bug report.
     */
    public enum SEVERITY {
        LOW, MEDIUM, HIGH
    }

    public static class GleapListener implements OnHttpResponseListener {

        public GleapListener() {
            try {

                new ConfigLoader(this).execute(GleapBug.getInstance());

                GleapUserSessionLoader sessionLoader = new GleapUserSessionLoader();
                sessionLoader.setCallback(new GleapUserSessionLoader.UserSessionLoadedCallback() {
                    @Override
                    public void invoke() {
                        new GleapIdentifyService().execute();
                    }
                });
                sessionLoader.execute();
            } catch (Error | Exception ignore) {
            }
        }

        @Override
        public void onTaskComplete(JSONObject httpResponse) {
            try {
                GleapConfig config = GleapConfig.getInstance();

                List<GleapActivationMethod> activationMethods = new LinkedList<>();
                if (config.isActivationMethodShake()) {
                    activationMethods.add(GleapActivationMethod.SHAKE);
                }

                if (config.isActivationMethodScreenshotGesture()) {
                    activationMethods.add(GleapActivationMethod.SCREENSHOT);
                }

                if (config.isActivationMethodFeedbackButton()) {
                    activationMethods.add(GleapActivationMethod.FAB);
                }

                if (instance == null) {
                    instance = new Gleap();
                }
                initGleap(GleapConfig.getInstance().getSdkKey(), activationMethods.toArray(new GleapActivationMethod[0]), application);
            } catch (Error | Exception ignore) {
            }
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
        try {
            GleapBug.getInstance().logEvent(name);
        } catch (Error | Exception ignore) {
        }
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
        try {
            GleapBug.getInstance().logEvent(name, data);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Attaches a file to the bug report
     *
     * @param attachment The file to attach to the bug report
     * @author Gleap
     */
    @Override
    public void addAttachment(File attachment) {
        try {
            GleapFileHelper.getInstance().addAttachment(attachment);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Removes all attachments
     *
     * @author Gleap
     */
    @Override
    public void removeAllAttachments() {
        try {
            GleapFileHelper.getInstance().clearAttachments();
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void setActivationMethods(GleapActivationMethod[] activationMethods) {
        try {
            if (application != null) {
                GleapConfig.getInstance().setPriorizedGestureDetectors(Arrays.asList(activationMethods));
                GleapDetectorUtil.clearAllDetectors();
                List<GleapDetector> detectorList = GleapDetectorUtil.initDetectors(application, activationMethods);
                GleapConfig.getInstance().setGestureDetectors(detectorList);
                GleapDetectorUtil.resumeAllDetectors();
            }
        } catch (Error | Exception ignore) {
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
        try {
            PrefillHelper.getInstancen().setPrefillData(data);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Disables the console logging. This must be called BEFORE initializing the SDK.
     *
     * @author Gleap
     */
    @Override
    public boolean isOpened() {
        return GleapDetectorUtil.isIsRunning();
    }

    /**
     * Manually close the feedback.
     *
     * @author Gleap
     */
    @Override
    public void close() {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (application != null && GleapConfig.getInstance().getCallCloseCallback() != null && isOpened()) {
                        GleapConfig.getInstance().getCallCloseCallback().invoke();
                    }
                }
            });
        } catch (Error | Exception ignore) {
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
        try {
            LogReader.getInstance().log(msg, GleapLogLevel.INFO);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Logs a message to the Gleap activity log
     *
     * @param msg The logged message
     * @author Gleap
     */
    @Override
    public void log(String msg, GleapLogLevel gleapLogLevel) {
        try {
            LogReader.getInstance().log(msg, gleapLogLevel);
        } catch (Error | Exception ignored) {
        }
    }

    /**
     * Disables the console logging. This must be called BEFORE initializing the SDK.
     *
     * @author Gleap
     */
    @Override
    public void disableConsoleLog() {
        try {
            GleapConfig.getInstance().setEnableConsoleLogsFromCode(false);
        } catch (Error | Exception ignore) {
        }
    }

    @Override
    public void showFeedbackButton(boolean show) {
        try {
            GleapConfig.getInstance().setHideWidget(!show);
            GleapInvisibleActivityManger.getInstance().setShowFab(show);
        } catch (Exception ignore) {
        }
    }

    /**
     * Enable Replay function for BB
     * Use with care, check performance on phone
     */
    private void enableReplays(boolean enable) {
        try {
            GleapConfig.getInstance().setEnableReplays(enable);
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Pass the current activity manually (Internal usage!)
     *
     * @param getActivityCallback get the current activity
     */
    public void setGetActivityCallback(GetActivityCallback getActivityCallback) {
        try {
            GleapConfig.getInstance().setGetActivityCallback(getActivityCallback);
        } catch (Error | Exception ignore) {
        }

    }

    @Override
    public void openFeatureRequests() {
        openFeatureRequests(false);
    }

    @Override
    public void openFeatureRequests(boolean showBackButton) {
        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                Runnable gleapRunnable = new Runnable() {
                    @Override
                    public void run() throws RuntimeException {
                        if (!GleapDetectorUtil.isIsRunning() && UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded() && Gleap.getInstance() != null) {
                            try {
                                JSONObject data = new JSONObject();
                                data.put("hideBackButton", !showBackButton);
                                GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-feature-requests", data));
                                screenshotTaker.takeScreenshot();
                            } catch (Exception e) {
                            }
                        }
                    }
                };
                mainHandler.post(gleapRunnable);
            }
        });
    }

    @Override
    public GleapUser getIdentity() {
        GleapUser gleapUser = null;
        try {
            gleapUser = UserSessionController.getInstance().getGleapUserSession();
        } catch (Error | Exception ignore) {
        }
        return gleapUser;
    }

    @Override
    public boolean isUserIdentified() {
        try {
            GleapUser gleapUser = UserSessionController.getInstance().getStoredGleapUser();
            if (gleapUser != null && gleapUser.getUserId() != null && !gleapUser.getUserId().equals("")) {
                return true;
            }
        } catch (Exception ex) {
        }
        return false;
    }

    @Override
    public void setRegisterPushMessageGroupCallback(RegisterPushMessageGroupCallback callback) {
        GleapConfig.getInstance().setRegisterPushMessageGroupCallback(callback);
    }

    @Override
    public void setUnRegisterPushMessageGroupCallback(UnRegisterPushMessageGroupCallback callback) {
        GleapConfig.getInstance().setUnRegisterPushMessageGroupCallback(callback);
    }

    public void finishImageUpload(Uri[] uris) {
        GleapConfig.getInstance().finishImageUpload(uris);
    }


}