package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import io.gleap.callbacks.AiToolExecutedCallback;
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
    private static Gleap instance;
    private static ScreenshotTaker screenshotTaker;
    private static Application application;
    public static JSONArray blacklist = new JSONArray();
    public static JSONArray propsToIgnore = new JSONArray();
    public static boolean internalCloseWidgetOnExternalLinkOpen = false;
    private static boolean isInitialized = false;
    private static OpenPushAction openPushAction;

    private Gleap() {
    }

    /**
     * Init Gleap with the given properties
     */
    private static void initGleap(String sdkKey, GleapActivationMethod[] activationMethods, Application application) {
        try {
            // prepare Gleap
            Gleap.application = application;
            screenshotTaker = new ScreenshotTaker();
            ConsoleUtil.clearConsole();
            // init config and load from the server
            GleapConfig.getInstance().setSdkKey(sdkKey);

            // init Gleap bug
            GleapBug.getInstance().setPhoneMeta(new PhoneMeta(application.getApplicationContext()));

            Gleap.getInstance().enableReplays(GleapConfig.getInstance().isEnableReplays());

            // start activation methods
            List<GleapDetector> detectorList = GleapDetectorUtil.initDetectors(application, activationMethods);

            if (GleapConfig.getInstance().isEnableReplays()) {
                ReplaysDetector replaysDetector = new ReplaysDetector(application);
                replaysDetector.initialize();
                detectorList.add(replaysDetector);
            }

            // Start services
            GleapActivityManager.getInstance().start(application);

            GleapConfig.getInstance().setGestureDetectors(detectorList);
            GleapDetectorUtil.resumeAllDetectors();
        } catch (Exception ignore) {
            handleErrorStatic(ignore, "initGleap");
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
                GleapSessionController.initialize(application);
                new GleapListener();
            } else {
                if (GleapConfig.getInstance().getConfigLoadedCallback() != null && GleapConfig.getInstance().getPlainConfig() != null) {
                    GleapConfig.getInstance().getConfigLoadedCallback().configLoaded(GleapConfig.getInstance().getPlainConfig());
                }
            }
        } catch (Error | Exception error) {
            handleErrorStatic(error, "initialize");
        }
    }

    public void processOpenPushActions() {
        try {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            Runnable gleapRunnable = new Runnable() {
                @Override
                public void run() throws RuntimeException {
                    try {
                        // Check if activity is null.
                        if (ActivityUtil.getCurrentActivity() == null) {
                            return;
                        }

                        // Check if config got loaded.
                        if (GleapConfig.getInstance().getPlainConfig() == null) {
                            return;
                        }

                        // Check if we have a session.
                        if (GleapSessionController.getInstance() == null
                                || !GleapSessionController.getInstance().isSessionLoaded()) {
                            return;
                        }

                        if (instance == null) {
                            return;
                        }

                        if (GleapDetectorUtil.isIsRunning()) {
                            return;
                        }

                        try {
                            if (instance.openPushAction != null) {
                                switch (instance.openPushAction.getType()) {
                                    case "news":
                                        instance.openNewsArticle(instance.openPushAction.getId(), true);
                                        break;
                                    case "checklist":
                                        instance.openChecklist(instance.openPushAction.getId(), true);
                                        break;
                                    case "conversation":
                                        instance.openConversation(instance.openPushAction.getId());
                                        break;
                                }

                                instance.openPushAction = null;
                            }
                        } catch (Error | Exception ignore) {
                            handleError(ignore, "processOpenPushActions - inner");
                        }
                    } catch (Error | Exception ignore) {
                        handleError(ignore, "processOpenPushActions - outer");
                    }
                }
            };
            mainHandler.postDelayed(gleapRunnable, 1500);
        } catch (Error | Exception ignore) {
            handleError(ignore, "processOpenPushActions");
        }
    }

    @Override
    public void handlePushNotification(JSONObject notificationData) {
        try {
            String type = "";
            String id = "";
            if (notificationData.has("type")) {
                type = notificationData.getString("type");
            }
            if (notificationData.has("id")) {
                id = notificationData.getString("id");
            }

            if (!type.isEmpty()) {
                this.openPushAction = new OpenPushAction(type, id);
                this.processOpenPushActions();
            }
        } catch (Exception ex) {
            handleError(ex, "handlePushNotification");
        }
    }

    @Override
    public void openConversations() {
        openConversations(false);
    }

    @Override
    public void openConversations(boolean hideBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", hideBackButton);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("open-conversations", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "openConversations - inner");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "openConversations - middle");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "openConversations - outer");
        }
    }

    @Override
    public void openConversation(String shareToken) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", false);
                                            message.put("shareToken", shareToken);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("open-conversation", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    /**
     * Manually shows the feedback menu or default feedback flow. This is used, when
     * you use the activation method "NONE".
     *
     * @throws GleapNotInitialisedException thrown when Gleap is not initialised
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
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            screenshotTaker.takeScreenshot(type);
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    @Override
    public void openChecklists() {
        openChecklists(true);
    }

    @Override
    public void openChecklists(boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("open-checklists", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    @Override
    public void openChecklist(String checklistId) {
        openChecklist(checklistId, false);
    }

    @Override
    public void openChecklist(String checklistId, boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            message.put("id", checklistId);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("open-checklist", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    @Override
    public void startChecklist(String outboundId) {
        startChecklist(outboundId, false);
    }

    @Override
    public void startChecklist(String outboundId, boolean showBackButton) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            message.put("outboundId", outboundId);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("start-checklist", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
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
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("open-news", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
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
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            try {
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            message.put("botId", botId);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("start-bot", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
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
                                if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null &&
                                        GleapSessionController.getInstance().isSessionLoaded() && instance != null) {
                                    try {
                                        if (screenshotTaker != null) {
                                            JSONObject message = new JSONObject();
                                            message.put("hideBackButton", !showBackButton);
                                            message.put("id", articleId);
                                            GleapActionQueueHandler.getInstance()
                                                    .addActionMessage(new GleapAction("open-news-article", message));
                                            screenshotTaker.takeScreenshot();
                                        }
                                    } catch (Exception e) {
                                        handleError(e, "run");
                                    }
                                }
                            } catch (Error | Exception ignore) {
                                handleError(ignore, "run");
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    /**
     * Start a classic form by formId
     */
    @Override
    public void startClassicForm(String formId) {
        try {
            startFeedbackFlow(formId, true);
        } catch (Error | Exception ignore) {
            handleError(ignore, "startClassicForm");
        }
    }

    @Override
    public void startClassicForm(String formId, Boolean showBackButton) {
        try {
            startFeedbackFlow(formId, showBackButton);
        } catch (Error | Exception ignore) {
            handleError(ignore, "startClassicForm");
        }
    }

    /**
     * Manually start the bug reporting workflow. This is used, when you use the
     * activation method "NONE".
     */
    @Override
    public void startFeedbackFlow(String feedbackFlow) {
        try {
            startFeedbackFlow(feedbackFlow, true);
        } catch (Error | Exception ignore) {
            handleError(ignore, "startFeedbackFlow");
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
                            if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null
                                    && GleapSessionController.getInstance().isSessionLoaded()
                                    && Gleap.getInstance() != null) {
                                try {
                                    JSONObject data = new JSONObject();
                                    if (!feedbackFlow.equals("")) {
                                        data.put("flow", feedbackFlow);
                                    }
                                    data.put("hideBackButton", !showBackButton);
                                    GleapActionQueueHandler.getInstance()
                                            .addActionMessage(new GleapAction("start-feedbackflow", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                    handleError(e, "run");
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
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
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null
                                    && GleapSessionController.getInstance().isSessionLoaded()
                                    && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    GleapActionQueueHandler.getInstance()
                                            .addActionMessage(new GleapAction("open-helpcenter", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                    handleError(e, "run");
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
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
                            if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null
                                    && GleapSessionController.getInstance().isSessionLoaded()
                                    && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    data.put("articleId", articleId);
                                    GleapActionQueueHandler.getInstance()
                                            .addActionMessage(new GleapAction("open-help-article", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                    handleError(e, "run");
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    @Override
    public void setNetworkLogPropsToIgnore(String[] propsToIgnore) {
        JSONArray jsonArray = new JSONArray();

        for (String item : propsToIgnore) {
            jsonArray.put(item);
        }

        Gleap.propsToIgnore = jsonArray;
    }

    @Override
    public void setNetworkLogsBlacklist(String[] blacklist) {
        JSONArray jsonArray = new JSONArray();

        for (String item : blacklist) {
            jsonArray.put(item);
        }

        Gleap.blacklist = jsonArray;
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
                            if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null
                                    && GleapSessionController.getInstance().isSessionLoaded()
                                    && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    data.put("collectionId", collectionId);
                                    GleapActionQueueHandler.getInstance()
                                            .addActionMessage(new GleapAction("open-help-collection", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                    handleError(e, "run");
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
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
                            if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null
                                    && GleapSessionController.getInstance().isSessionLoaded()
                                    && Gleap.getInstance() != null) {
                                try {

                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    data.put("term", term);
                                    GleapActionQueueHandler.getInstance()
                                            .addActionMessage(new GleapAction("open-helpcenter-search", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                    handleError(e, "run");
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
        }
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity) {
        try {
            SilentBugReportUtil.createSilentBugReport(application, description, severity);
        } catch (Error | Exception ignore) {
            handleError(ignore, "sendSilentCrashReport");
        }
    }

    @Override
    public void sendSilentCrashReport(String description, SEVERITY severity, JSONObject excludeData) {
        try {
            SilentBugReportUtil.createSilentBugReport(application, description, severity, excludeData);
        } catch (Error | Exception ignore) {
            handleError(ignore, "sendSilentCrashReport");
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
                ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GleapInvisibleActivityManger.getInstance().destroyBanner(true);
                        GleapInvisibleActivityManger.getInstance().destroyModal(true, true);
                        GleapInvisibleActivityManger.getInstance().clearMessages();
                    }
                });
            } catch (Exception ignore) {
                handleError(ignore, "clearIdentity - inner");
            }

            GleapEventService.getInstance().stop();
            GleapBaseSessionService sessionLoader = new GleapBaseSessionService();
            sessionLoader.execute();
        } catch (Error | Exception ignore) {
            handleError(ignore, "run");
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
        try {
            GleapConfig.getInstance().setApiUrl(apiUrl);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setApiUrl");
        }
    }

    @Override
    public void setWSApiUrl(String wsApiUrl) {
        try {
            GleapConfig.getInstance().setWsApiUrl(wsApiUrl);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setWSApiUrl");
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
            handleError(ignore, "setFrameUrl");
        }
    }

    /**
     * Set the language for the Gleap Report Flow. Otherwise the default language is
     * used.
     * Supported Languages "en", "es", "fr", "it", "de", "nl", "cz"
     *
     * @param language ISO Country Code eg. "cz," "en", "de", "es", "nl"
     */
    @Override
    public void setLanguage(String language) {
        try {
            GleapConfig.getInstance().setLanguage(language);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setLanguage");
        }
    }

    @Override
    public void setAiToolExecutedCallback(AiToolExecutedCallback aiToolExecutedCallback) {
        try {
            GleapConfig.getInstance().setAiToolExecutedCallback(aiToolExecutedCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setAiToolExecutedCallback");
        }
    }

    @Override
    public void setWidgetOpenedCallback(WidgetOpenedCallback widgetOpenedCallback) {
        try {
            GleapConfig.getInstance().setWidgetOpenedCallback(widgetOpenedCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setWidgetOpenedCallback");
        }
    }

    @Override
    public void setWidgetClosedCallback(WidgetClosedCallback widgetClosedCallback) {
        try {
            GleapConfig.getInstance().setWidgetClosedCallback(widgetClosedCallback);

        } catch (Error | Exception ignore) {
            handleError(ignore, "setWidgetClosedCallback");
        }

    }

    @Override
    public void setNotificationUnreadCountUpdatedCallback(
            NotificationUnreadCountUpdatedCallback notificationUnreadCountUpdatedCallback) {
        try {
            GleapConfig.getInstance().setNotificationUnreadCountUpdatedCallback(notificationUnreadCountUpdatedCallback);

        } catch (Error | Exception ignore) {
            handleError(ignore, "setNotificationUnreadCountUpdatedCallback");
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
            handleError(ignore, "setCustomData");
        }
    }

    @Override
    public void setAiTools(GleapAiTool[] aiTools) {
        GleapConfig.getInstance().setAiTools(aiTools);
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
        try {
            GleapBug.getInstance().setTicketAttribute(key, value);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setTicketAttribute");
        }
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
        try {
            GleapBug.getInstance().setTicketAttribute(key, value);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setTicketAttribute");
        }
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
        try {
            GleapBug.getInstance().setTicketAttribute(key, value);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setTicketAttribute");
        }
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
        try {
            GleapBug.getInstance().setTicketAttribute(key, value);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setTicketAttribute");
        }
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
        try {
            GleapBug.getInstance().setTicketAttribute(key, value);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setTicketAttribute");
        }
    }

    /**
     * Unsets a ticket attribute.
     *
     * @param key The key of the attribute
     * @author Gleap
     */
    @Override
    public void unsetTicketAttribute(String key) {
        try {
            GleapBug.getInstance().unsetTicketAttribute(key);
        } catch (Error | Exception ignore) {
            handleError(ignore, "unsetTicketAttribute");
        }
    }

    /**
     * Clears all ticket attributes.
     *
     * @author Gleap
     */
    @Override
    public void clearTicketAttributes() {
        try {
            GleapBug.getInstance().clearTicketAttributes();
        } catch (Error | Exception ignore) {
            handleError(ignore, "clearTicketAttributes");
        }
    }

    /**
     * Attach Data to the request. The Data will be merged into the body sent with
     * the bugreport.
     * !!Existing keys can be overriten
     *
     * @param data Data, which is added
     */
    @Override
    public void attachCustomData(JSONObject data) {
        try {
            GleapBug.getInstance().setCustomData(data);
        } catch (Error | Exception ignore) {
            handleError(ignore, "attachCustomData");
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
            handleError(ignore, "removeCustomDataForKey");
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
            handleError(ignore, "clearCustomData");
        }
    }

    /**
     * This is called, when the Gleap flow is started
     *
     * @param feedbackWillBeSentCallback is called when BB is opened
     */
    @Override
    public void setFeedbackWillBeSentCallback(FeedbackWillBeSentCallback feedbackWillBeSentCallback) {
        try {
            GleapConfig.getInstance().setFeedbackWillBeSentCallback(feedbackWillBeSentCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setFeedbackWillBeSentCallback");
        }

    }

    /**
     * This method is triggered, when feedback is sent
     *
     * @param feedbackSentCallback this callback is called when the flow is called
     */
    @Override
    public void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback) {
        try {
            GleapConfig.getInstance().setFeedbackSentCallback(feedbackSentCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setFeedbackSentCallback");
        }
    }

    /**
     * This method is triggered, when an outbound is sent.
     *
     * @param outboundSentCallback this callback is called when the flow is called
     */
    @Override
    public void setOutboundSentCallback(OutboundSentCallback outboundSentCallback) {
        try {
            GleapConfig.getInstance().setOutboundSentCallback(outboundSentCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setOutboundSentCallback");
        }
    }

    @Override
    public void setFeedbackSendingFailedCallback(FeedbackSendingFailedCallback feedbackSendingFailedCallback) {
        try {
            GleapConfig.getInstance().setFeedbackSendingFailedCallback(feedbackSendingFailedCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setFeedbackSendingFailedCallback");
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
            handleError(ignore, "setBitmapCallback");
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
            handleError(ignore, "setConfigLoadedCallback");
        }
    }

    /**
     * This is called, when the config is received from the server;
     *
     * @param initializedCallback callback which is called
     */
    @Override
    public void setInitializedCallback(InitializedCallback initializedCallback) {
        try {
            GleapConfig.getInstance().setInitializedCallback(initializedCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setInitializedCallback");
        }
    }

    @Override
    public void setFeedbackFlowStartedCallback(FeedbackFlowStartedCallback feedbackFlowStartedCallback) {
        try {
            GleapConfig.getInstance().setFeedbackFlowStartedCallback(feedbackFlowStartedCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setFeedbackFlowStartedCallback");
        }
    }

    @Override
    public void setInitializationDoneCallback(InitializationDoneCallback initializationDoneCallback) {
        try {
            GleapConfig.getInstance().setInitializationDoneCallback(initializationDoneCallback);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setInitializationDoneCallback");
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
            handleError(ignore, "attachNetworkLogs");
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
     * @param response      Response of the call. You can add just the information
     *                      you want and need.
     */
    @Override
    public void logNetwork(String urlConnection, RequestType requestType, int status,
            int duration, JSONObject request, JSONObject response) {
        try {
            GleapHttpInterceptor.log(urlConnection, requestType, status, duration, request, response);
        } catch (Error | Exception ignore) {
            handleError(ignore, "logNetwork");
        }
    }

    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection UrlHttpConnection
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information
     *                      you want and need.
     */
    @Override
    public void logNetwork(HttpsURLConnection urlConnection, JSONObject request, JSONObject response) {
        try {
            GleapHttpInterceptor.log(urlConnection, request, response);
        } catch (Error | Exception ignore) {
            handleError(ignore, "logNetwork");
        }
    }

    /**
     * Log network traffic by logging it manually.
     *
     * @param urlConnection UrlHttpConnection
     * @param request       Add the data you want. e.g the body sent in the request
     * @param response      Response of the call. You can add just the information
     *                      you want and need.
     */
    @Override
    public void logNetwork(HttpsURLConnection urlConnection, String request, String response) {
        try {
            GleapHttpInterceptor.log(urlConnection, request, response);
        } catch (Error | Exception ignore) {
            handleError(ignore, "logNetwork");
        }
    }

    /**
     * Register custom functions. This custom function can be configured in the
     * widget, Form, Details of one step tab on app.Gleap.io
     *
     * @param customAction what is executed when the custom step is pressed
     */
    @Override
    public void registerCustomAction(CustomActionCallback customAction) {
        try {
            GleapConfig.getInstance().registerCustomAction(customAction);
        } catch (Error | Exception ignore) {
            handleError(ignore, "registerCustomAction");
        }
    }

    @Override
    public void registerCustomLinkHandler(CustomLinkHandlerCallback customLinkHandler) {
        try {
            GleapConfig.getInstance().registerCustomLinkHandler(customLinkHandler);
        } catch (Error | Exception ignore) {
            handleError(ignore, "registerCustomLinkHandler");
        }
    }

    @Override
    public void handleLink(String url) {
        if (url == null || (url != null && url.length() == 0)) {
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // Smartlink, handle internally.
                if (url.contains("gleap:")) {
                    if (GleapDetectorUtil.isIsRunning()) {
                        // Try again later.
                        Gleap.getInstance().handleLink(url);
                    } else {
                        Gleap.getInstance().handleGleapLink(url);
                    }
                    return;
                }

                // Use custom link handler.
                if (GleapConfig.getInstance().getCustomLinkHandler() != null) {
                    GleapConfig.getInstance().getCustomLinkHandler().invoke(url);
                    return;
                }

                // If URL doesn't start with http or https, mailto or tel, close the widget.
                if (!url.startsWith("http") && !url.startsWith("https") && !url.startsWith("mailto")
                        && !url.startsWith("tel")) {
                    Gleap.getInstance().close();
                }

                // Open externally.
                Gleap.getInstance().openUrlExternally(url);
            }
        }, 250);
    }

    public void handleGleapLink(String href) {
        try {
            String[] urlParts = href.split("/");
            String type = urlParts[2];

            switch (type) {
                case "article":
                    String articleId = urlParts[3];
                    this.openHelpCenterArticle(articleId, true);
                    break;
                case "collection":
                    String collectionId = urlParts[3];
                    this.openHelpCenterCollection(collectionId, true);
                    break;
                case "survey":
                    String surveyId = urlParts[3];
                    this.showSurvey(surveyId);
                    break;
                case "bot":
                    String botId = urlParts[3];
                    this.startBot(botId, true);
                    break;
                case "news":
                    String newsId = urlParts[3];
                    this.openNewsArticle(newsId, true);
                    break;
                case "flow":
                    String flowId = urlParts[3];
                    this.startFeedbackFlow(flowId, true);
                    break;
                case "checklist":
                    String checklistId = urlParts[3];
                    this.startChecklist(checklistId, true);
                    break;
                case "tour":
                    System.out.println("Product tours are not supported on mobile.");
                    break;
                default:
                    System.out.println("Invalid type provided in href: " + href);
                    break;
            }
        } catch (Exception e) {
            handleError(e, "handleGleapLink");
        }
    }

    private void openUrlExternally(String url) {
        try {
            Activity local = ActivityUtil.getCurrentActivity();
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            local.startActivity(browserIntent);
        } catch (Exception e) {
            handleError(e, "openUrlExternally");
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
            handleError(ignore, "setApplicationType");
        }
    }

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
            try {
                new ConfigLoader(this).execute(GleapBug.getInstance());

                GleapBaseSessionService sessionLoader = new GleapBaseSessionService();
                sessionLoader.execute();
            } catch (Error | Exception ignore) {
                handleErrorStatic(ignore, "GleapListener constructor");
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
                initGleap(GleapConfig.getInstance().getSdkKey(),
                        activationMethods.toArray(new GleapActivationMethod[0]), application);
            } catch (Error | Exception ignore) {
                handleErrorStatic(ignore, "GleapListener onTaskComplete");
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
            handleError(ignore, "trackEvent");
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
            handleError(ignore, "trackEvent");
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
            handleError(ignore, "addAttachment");
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
            handleError(ignore, "removeAllAttachments");
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
        try {
            PrefillHelper.getInstancen().setPrefillData(data);
        } catch (Error | Exception ignore) {
            handleError(ignore, "preFillForm");
        }
    }

    /**
     * Disables the console logging. This must be called BEFORE initializing the
     * SDK.
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
        try {
            LogReader.getInstance().log(msg, GleapLogLevel.INFO);
        } catch (Error | Exception ignore) {
            handleError(ignore, "log");
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
            handleError(ignored, "log");
        }
    }

    /**
     * Disables the console logging. This must be called BEFORE initializing the
     * SDK.
     *
     * @author Gleap
     */
    @Override
    public void disableConsoleLog() {
        try {
            GleapConfig.getInstance().setEnableConsoleLogsFromCode(false);
        } catch (Error | Exception ignore) {
            handleError(ignore, "disableConsoleLog");
        }
    }

    @Override
    public void showFeedbackButton(boolean show) {
        try {
            GleapConfig.getInstance().setHideFeedbackButton(!show);
            GleapConfig.getInstance().setFeedbackButtonManuallySet(true);
            GleapInvisibleActivityManger.getInstance().setShowFab(show);
        } catch (Exception ignore) {
            handleError(ignore, "showFeedbackButton");
        }
    }

    @Override
    public void setTags(String[] tags) {
        GleapBug.getInstance().setTags(tags);
    }

    /**
     * Enable Replay function for BB
     * Use with care, check performance on phone
     */
    private void enableReplays(boolean enable) {
        try {
            GleapConfig.getInstance().setEnableReplays(enable);
        } catch (Error | Exception ignore) {
            handleError(ignore, "setTags");
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
            handleError(ignore, "setGetActivityCallback");
        }

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
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable gleapRunnable = new Runnable() {
                        @Override
                        public void run() throws RuntimeException {
                            if (!GleapDetectorUtil.isIsRunning() && GleapSessionController.getInstance() != null
                                    && GleapSessionController.getInstance().isSessionLoaded()
                                    && Gleap.getInstance() != null) {
                                try {
                                    JSONObject data = new JSONObject();
                                    data.put("hideBackButton", !showBackButton);
                                    GleapActionQueueHandler.getInstance()
                                            .addActionMessage(new GleapAction("open-feature-requests", data));
                                    screenshotTaker.takeScreenshot();
                                } catch (Exception e) {
                                    handleError(e, "run");
                                }
                            }
                        }
                    };
                    mainHandler.post(gleapRunnable);
                }
            });
        } catch (Exception exp) {
            handleError(exp, "openFeatureRequests");
        }
    }

    @Override
    public GleapSessionProperties getIdentity() {
        GleapSessionProperties gleapUser = null;
        try {
            gleapUser = GleapSessionController.getInstance().getGleapUserSession();
        } catch (Error | Exception ignore) {
            handleError(ignore, "getIdentity");
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
        GleapConfig.getInstance().setRegisterPushMessageGroupCallback(callback);
    }

    @Override
    public void setUnRegisterPushMessageGroupCallback(UnRegisterPushMessageGroupCallback callback) {
        GleapConfig.getInstance().setUnRegisterPushMessageGroupCallback(callback);
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
        try {
            ErrorCallback errorCallback = GleapConfig.getInstance().getErrorCallback();
            if (errorCallback != null) {
                errorCallback.onError(error, context);
            }
        } catch (Exception ignore) {
            // If the error callback itself throws an exception, we ignore it to prevent
            // infinite loops
        }
    }

    /**
     * Static helper method to handle errors and exceptions in static contexts.
     * If an error callback is set, it will be called with the error and context.
     * Otherwise, the error will be silently ignored.
     *
     * @param error   The error or exception that occurred
     * @param context Context information about where the error occurred
     */
    private static void handleErrorStatic(Throwable error, String context) {
        try {
            ErrorCallback errorCallback = GleapConfig.getInstance().getErrorCallback();
            if (errorCallback != null) {
                errorCallback.onError(error, context);
            }
        } catch (Exception ignore) {
            // If the error callback itself throws an exception, we ignore it to prevent
            // infinite loops
        }
    }

    /**
     * Shows a modal to the user.
     *
     * @param data The modal data
     * @author Gleap
     */
    @Override
    public void showModal(JSONObject data) {
        try {
            ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        GleapInvisibleActivityManger.getInstance().showModal(data, null);
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
        try {
            GleapConfig.getInstance().setErrorCallback(errorCallback);
        } catch (Error | Exception error) {
            handleError(error, "setErrorCallback");
        }
    }

}