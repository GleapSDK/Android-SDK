package io.gleap;

import org.json.JSONObject;

import io.gleap.callbacks.AiToolExecutedCallback;
import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.CustomLinkHandlerCallback;
import io.gleap.callbacks.ErrorCallback;
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

/**
 * The callbacks and handlers the app registered through {@link Gleap}, plus the widget's own
 * close hook.
 */
class GleapCallbacks {
    // Created with the class: getInstance() is called from several threads.
    private static volatile GleapCallbacks instance = new GleapCallbacks();

    private volatile ConfigLoadedCallback configLoadedCallback;
    private volatile InitializedCallback initializedCallback;

    // The config is loaded once per process, and configLoaded / initialized fire only then. What
    // was delivered is kept here, so a callback registered later (or a second initialize) can be
    // handed it too, like on iOS. Guarded by deliveryLock.
    private final Object deliveryLock = new Object();
    private JSONObject deliveredFlowConfig;
    private boolean initializedDelivered;
    // A replay was posted to the main thread and has not run yet: further requests join it.
    private boolean configLoadedReplayPending;
    private boolean initializedReplayPending;
    private InitializationDoneCallback initializationDoneCallback;
    private FeedbackSentCallback feedbackSentCallback;
    private OutboundSentCallback outboundSentCallback;
    private FeedbackWillBeSentCallback feedbackWillBeSentCallback;
    private FeedbackFlowStartedCallback feedbackFlowStartedCallback;
    private FeedbackSendingFailedCallback feedbackSendingFailedCallback;
    private WidgetOpenedCallback widgetOpenedCallback;
    private WidgetClosedCallback widgetClosedCallback;
    private AiToolExecutedCallback aiToolExecutedCallback;
    private NotificationUnreadCountUpdatedCallback notificationUnreadCountUpdatedCallback;
    private RegisterPushMessageGroupCallback registerPushMessageGroupCallback;
    private UnRegisterPushMessageGroupCallback unRegisterPushMessageGroupCallback;
    private GetActivityCallback getActivityCallback;
    private GetBitmapCallback getBitmapCallback;
    private CustomActionCallback customAction;
    private CustomLinkHandlerCallback customLinkHandler;
    private ErrorCallback errorCallback;
    // Set by the open widget: closes it (Gleap.close()).
    private CallCloseCallback callCloseCallback;

    private GleapCallbacks() {
    }

    public static GleapCallbacks getInstance() {
        return instance;
    }

    // Tests only.
    static void resetForTesting() {
        instance = new GleapCallbacks();
    }

    public ConfigLoadedCallback getConfigLoadedCallback() {
        return configLoadedCallback;
    }

    /**
     * Stores the callback. When the config was already delivered, the new callback is handed it
     * once, posted to the main thread.
     */
    public void setConfigLoadedCallback(ConfigLoadedCallback configLoadedCallback) {
        boolean replay;
        synchronized (deliveryLock) {
            this.configLoadedCallback = configLoadedCallback;
            replay = configLoadedCallback != null && deliveredFlowConfig != null;
        }
        if (replay) {
            replayConfigLoaded();
        }
    }

    public InitializedCallback getInitializedCallback() {
        return initializedCallback;
    }

    /**
     * Stores the callback. When initialized was already delivered, the new callback is called
     * once, posted to the main thread.
     */
    public void setInitializedCallback(InitializedCallback initializedCallback) {
        boolean replay;
        synchronized (deliveryLock) {
            this.initializedCallback = initializedCallback;
            replay = initializedCallback != null && initializedDelivered;
        }
        if (replay) {
            replayInitialized();
        }
    }

    /**
     * The config was loaded: remembers the flowConfig and hands it to the registered callback
     * (on the calling thread, as the SDK always did).
     */
    void deliverConfigLoaded(JSONObject flowConfig) {
        ConfigLoadedCallback callback;
        synchronized (deliveryLock) {
            deliveredFlowConfig = flowConfig;
            callback = configLoadedCallback;
        }
        if (callback != null) {
            callback.configLoaded(flowConfig);
        }
    }

    /**
     * The SDK is initialized: remembers it and calls the registered callback (on the calling
     * thread, as the SDK always did).
     */
    void deliverInitialized() {
        InitializedCallback callback;
        synchronized (deliveryLock) {
            initializedDelivered = true;
            callback = initializedCallback;
        }
        if (callback != null) {
            callback.initialized();
        }
    }

    /**
     * Initialize was called again: hands what was already delivered to the registered callbacks
     * again (configLoaded, then initialized), like iOS. Nothing before the first load.
     */
    void replayDelivered() {
        replayConfigLoaded();
        replayInitialized();
    }

    private void replayConfigLoaded() {
        synchronized (deliveryLock) {
            if (deliveredFlowConfig == null || configLoadedReplayPending) {
                return;
            }
            configLoadedReplayPending = true;
        }
        GleapMainThread.post(new Runnable() {
            @Override
            public void run() {
                final ConfigLoadedCallback callback;
                final JSONObject flowConfig;
                synchronized (deliveryLock) {
                    configLoadedReplayPending = false;
                    callback = configLoadedCallback;
                    flowConfig = deliveredFlowConfig;
                }
                if (callback != null) {
                    GleapErrors.guard("configLoaded replay", () -> callback.configLoaded(flowConfig));
                }
            }
        });
    }

    private void replayInitialized() {
        synchronized (deliveryLock) {
            if (!initializedDelivered || initializedReplayPending) {
                return;
            }
            initializedReplayPending = true;
        }
        GleapMainThread.post(new Runnable() {
            @Override
            public void run() {
                final InitializedCallback callback;
                synchronized (deliveryLock) {
                    initializedReplayPending = false;
                    callback = initializedCallback;
                }
                if (callback != null) {
                    GleapErrors.guard("initialized replay", callback::initialized);
                }
            }
        });
    }

    public InitializationDoneCallback getInitializationDoneCallback() {
        return initializationDoneCallback;
    }

    public void setInitializationDoneCallback(InitializationDoneCallback initializationDoneCallback) {
        this.initializationDoneCallback = initializationDoneCallback;
    }

    public FeedbackSentCallback getFeedbackSentCallback() {
        return feedbackSentCallback;
    }

    public void setFeedbackSentCallback(FeedbackSentCallback feedbackSentCallback) {
        this.feedbackSentCallback = feedbackSentCallback;
    }

    public OutboundSentCallback getOutboundSentCallback() {
        return outboundSentCallback;
    }

    public void setOutboundSentCallback(OutboundSentCallback outboundSentCallback) {
        this.outboundSentCallback = outboundSentCallback;
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

    public AiToolExecutedCallback getAiToolExecutedCallback() {
        return aiToolExecutedCallback;
    }

    public void setAiToolExecutedCallback(AiToolExecutedCallback aiToolExecutedCallback) {
        this.aiToolExecutedCallback = aiToolExecutedCallback;
    }

    public NotificationUnreadCountUpdatedCallback getNotificationUnreadCountUpdatedCallback() {
        return notificationUnreadCountUpdatedCallback;
    }

    public void setNotificationUnreadCountUpdatedCallback(NotificationUnreadCountUpdatedCallback notificationUnreadCountUpdatedCallback) {
        this.notificationUnreadCountUpdatedCallback = notificationUnreadCountUpdatedCallback;
    }

    public RegisterPushMessageGroupCallback getRegisterPushMessageGroupCallback() {
        return registerPushMessageGroupCallback;
    }

    public void setRegisterPushMessageGroupCallback(RegisterPushMessageGroupCallback registerPushMessageGroupCallback) {
        this.registerPushMessageGroupCallback = registerPushMessageGroupCallback;
    }

    public UnRegisterPushMessageGroupCallback getUnRegisterPushMessageGroupCallback() {
        return unRegisterPushMessageGroupCallback;
    }

    public void setUnRegisterPushMessageGroupCallback(UnRegisterPushMessageGroupCallback unRegisterPushMessageGroupCallback) {
        this.unRegisterPushMessageGroupCallback = unRegisterPushMessageGroupCallback;
    }

    public GetActivityCallback getGetActivityCallback() {
        return getActivityCallback;
    }

    public void setGetActivityCallback(GetActivityCallback getActivityCallback) {
        this.getActivityCallback = getActivityCallback;
    }

    public GetBitmapCallback getGetBitmapCallback() {
        return getBitmapCallback;
    }

    public void setGetBitmapCallback(GetBitmapCallback getBitmapCallback) {
        this.getBitmapCallback = getBitmapCallback;
    }

    public void registerCustomAction(CustomActionCallback customAction) {
        this.customAction = customAction;
    }

    public CustomActionCallback getCustomActions() {
        return customAction;
    }

    public void registerCustomLinkHandler(CustomLinkHandlerCallback customLinkHandler) {
        this.customLinkHandler = customLinkHandler;
    }

    public CustomLinkHandlerCallback getCustomLinkHandler() {
        return customLinkHandler;
    }

    public ErrorCallback getErrorCallback() {
        return errorCallback;
    }

    public void setErrorCallback(ErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
    }

    public CallCloseCallback getCallCloseCallback() {
        return callCloseCallback;
    }

    public void setCallCloseCallback(CallCloseCallback callCloseCallback) {
        this.callCloseCallback = callCloseCallback;
    }
}
