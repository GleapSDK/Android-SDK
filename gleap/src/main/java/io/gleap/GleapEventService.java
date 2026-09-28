package io.gleap;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;


import gleap.io.gleap.BuildConfig;
import java.util.Date;

import static io.gleap.DateUtil.dateToString;

class GleapEventService {
    // Created with the class: getInstance() is called from several threads.
    private static volatile GleapEventService instance = new GleapEventService();
    private static GleapWebSocketListener webSocketListener;
    private boolean disableInAppNotifications = false;
    private final GleapEventQueue eventQueue = new GleapEventQueue();
    private Handler intervalHandler;

    interface WebSocketFactory {
        GleapWebSocketListener create();
    }

    private static final WebSocketFactory OKHTTP_WEBSOCKETS = new WebSocketFactory() {
        @Override
        public GleapWebSocketListener create() {
            return new GleapWebSocketListener();
        }
    };

    private static volatile WebSocketFactory webSocketFactory = OKHTTP_WEBSOCKETS;

    private GleapEventService() {
    }

    // Tests only; null restores the real WebSocket.
    static void setWebSocketFactoryForTesting(WebSocketFactory factory) {
        webSocketFactory = factory != null ? factory : OKHTTP_WEBSOCKETS;
    }

    public static GleapEventService getInstance() {
        return instance;
    }

    // Tests only.
    static void resetForTesting() {
        instance = new GleapEventService();
        webSocketListener = null;
    }

    public void setDisableInAppNotifications(boolean disableInAppNotifications) {
        this.disableInAppNotifications = disableInAppNotifications;
    }

    public void startWebSocketListener() {
        clearWebsocketListener();

        webSocketListener = webSocketFactory.create();
        webSocketListener.connect();
    }

    public void start() {
        if (intervalHandler != null) {
            intervalHandler.removeCallbacksAndMessages(null);
        }

        try {
            JSONObject sessionStarted = new JSONObject();
            sessionStarted.put("name", "sessionStarted");
            sessionStarted.put("date", dateToString(new Date()));
            eventQueue.addUncapped(sessionStarted);

            Activity activity = ActivityUtil.getCurrentActivity();
            JSONObject pageView = new JSONObject();
            JSONObject page = new JSONObject();
            page.put("page", activity.getClass().getSimpleName());
            pageView.put("name", "pageView");
            pageView.put("data", page);
            pageView.put("date", dateToString(new Date()));
            eventQueue.addUncapped(pageView);
        } catch (Exception ex) {
        }

        intervalHandler = new Handler(Looper.getMainLooper());
        intervalHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (GleapSessionController.getInstance() != null
                            && GleapSessionController.getInstance().isSessionLoaded()) {
                        if (!eventQueue.isEmpty()) {
                            new EventHttpHelper().executeOnExecutor(GleapExecutor.SERIAL);
                        }
                    }

                    intervalHandler.postDelayed(this, 3000);
                } catch (Exception ignore) {
                }
            }
        }, 0);
    }

    public void stop() {
        stop(true);
    }

    public void stop(Boolean clear) {
        if (clear) {
            eventQueue.clear();
        }
        clearWebsocketListener();

        if (intervalHandler != null) {
            intervalHandler.removeCallbacksAndMessages(null);
        }
    }

    private void clearWebsocketListener() {
        if (webSocketListener != null) {
            webSocketListener.destroy();
            webSocketListener = null;
        }
    }

    public void addEvent(JSONObject event) {
        eventQueue.add(event);
    }

    GleapEventQueue getEventQueue() {
        return eventQueue;
    }

    private class EventHttpHelper extends AsyncTask {
        @Override
        protected Object doInBackground(Object[] objects) {
            sendQueuedEvents();
            return null;
        }
    }

    /**
     * Sends the queued events; they are removed once the ping went through.
     */
    void sendQueuedEvents() {
        try {
            int status = postEvents(eventQueue.toJSONArray());
            if (status == 200) {
                eventQueue.clear();
            }
        } catch (Exception exception) {
        }
    }

    /**
     * Sends the events (POST /sessions/ping).
     *
     * @return the HTTP status; an error status throws, the events stay queued then
     */
    static int postEvents(JSONArray events) throws IOException, JSONException {
        HttpURLConnection conn = GleapHttp.openReportPost("/sessions/ping",
                GleapSessionController.getInstance().getUserSession(), GleapHttp.READ_TIMEOUT_MS);

        JSONObject body = new JSONObject();
        body.put("events", events);
        body.put("time", PhoneMeta.calculateDurationInDouble());
        body.put("opened", Gleap.getInstance().isOpened());
        body.put("ws", true);
        body.put("sdkVersion", BuildConfig.VERSION_NAME);
        GleapHttp.writeJson(conn, body);

        // Throws for an error status.
        conn.getInputStream().close();
        int status = conn.getResponseCode();
        conn.disconnect();
        return status;
    }


    private GleapChatMessage createComment(String outboundId, JSONObject messageData, String sendAt, String createdAt) throws Exception {
        String senderName = "";
        String profileImageUrl = "";
        String text = "";
        String type = "";
        String shareToken = "";
        String newsId = "";
        String checklistId = "";
        String coverImageUrl = "";
        String nextStepTitle = "";
        int currentStep = 0;
        int totalSteps = 0;
        boolean senderIsBot = false;

        if (messageData.has("type")) {
            type = messageData.getString("type");
        }

        if (messageData.has("text")) {
            text = messageData.getString("text");
        }

        if (messageData.has("sender")) {
            JSONObject sender = messageData.getJSONObject("sender");

            if (sender.has("name")) {
                senderName = sender.getString("name");
            }

            if (sender.has("profileImageUrl")) {
                profileImageUrl = sender.getString("profileImageUrl");
            }

            if (sender.has("isBot")) {
                senderIsBot = sender.optBoolean("isBot", false);
            }
        }

        if (messageData.has("conversation")) {
            JSONObject conversation = messageData.getJSONObject("conversation");
            if (conversation.has("shareToken")) {
                shareToken = conversation.getString("shareToken");
            }
        }

        if (messageData.has("news")) {
            JSONObject conversation = messageData.getJSONObject("news");
            if (conversation.has("id")) {
                newsId = conversation.getString("id");
            }
        }

        if (messageData.has("checklist")) {
            JSONObject conversation = messageData.getJSONObject("checklist");
            if (conversation.has("id")) {
                checklistId = conversation.getString("id");
            }
        }

        if (messageData.has("coverImageUrl")) {
            coverImageUrl = messageData.getString("coverImageUrl");
        }

        if (messageData.has("currentStep")) {
            currentStep = messageData.getInt("currentStep");
        }

        if (messageData.has("totalSteps")) {
            totalSteps = messageData.getInt("totalSteps");
        }

        if (messageData.has("nextStepTitle")) {
            nextStepTitle = messageData.getString("nextStepTitle");
        }

        GleapSender sender = new GleapSender(senderName, profileImageUrl, senderIsBot);
        return new GleapChatMessage(outboundId, type, text, shareToken, sender, newsId, coverImageUrl, currentStep,
                totalSteps, nextStepTitle, checklistId, sendAt, createdAt);
    }

    public void processEventData(JSONObject data) throws Exception {
        if (data == null) {
            return;
        }

        if (data.has("u")) {
            GleapMainThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        GleapOverlayManager.getInstance().setMessageCounter(data.getInt("u"));
                    } catch (JSONException e) {
                    }
                }
            });
        }

        if (data.has("a") && data.get("a") instanceof JSONArray) {
            if (Gleap.getInstance().isOpened()) {
                return;
            }

            JSONArray actions = data.getJSONArray("a");

            for (int i = 0; i < actions.length(); i++) {
                JSONObject currentAction = actions.getJSONObject(i);
                if (currentAction.has("actionType")) {
                    if (currentAction.getString("actionType").contains("notification")) {
                        // In app notification.
                        JSONObject messageData = null;

                        try {
                            messageData = currentAction.getJSONObject("data");
                        } catch (JSONException e) {

                        } catch (Exception e) {

                        }

                        // Check if the notification is part of a checklist and has popupType "widget".
                        if (messageData != null && messageData.has("checklist") &&
                                messageData.getJSONObject("checklist").getString("popupType").equals("widget")) {

                            // Open the checklist if the popupType is "widget".
                            Gleap.getInstance().openChecklist(messageData.getJSONObject("checklist").getString("id"),
                                    true);
                        } else {
                            // In app notification.
                            if (!this.disableInAppNotifications) {
                                GleapMainThread.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            String outboundId = "";
                                            if (currentAction.has("outbound")) {
                                                outboundId = currentAction.getString("outbound");
                                            }
                                            JSONObject data = currentAction.getJSONObject("data");
                                            GleapChatMessage comment = createComment(outboundId, data, currentAction.optString("sendAt", ""), currentAction.optString("createdAt", ""));
                                            GleapOverlayManager.getInstance().addNotification(comment, null);
                                        } catch (JSONException e) {

                                        } catch (Exception e) {

                                        }
                                    }
                                });
                            }
                        }
                    } else if (currentAction.getString("format").contains("survey")) {
                        JSONObject jsonObject = new JSONObject();
                        try {
                            jsonObject.put("isSurvey", true);
                            jsonObject.put("hideBackButton", true);
                            jsonObject.put("format", currentAction.getString("format"));
                            jsonObject.put("flow", currentAction.getString("actionType"));
                        } catch (Exception ex) {
                        }

                        GleapMainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                SurveyType surveyType = SurveyType.SURVEY;
                                try {
                                    if (currentAction.getString("format").contains("survey_full")) {
                                        surveyType = SurveyType.SURVEY_FULL;
                                    }
                                } catch (JSONException e) {

                                }

                                // Check if it is open
                                GleapActionQueueHandler.getInstance()
                                        .addActionMessage(new GleapAction("start-survey", jsonObject));
                                Gleap.getInstance().open(surveyType);
                            }
                        });
                    }
                    if (currentAction.getString("actionType").contains("banner")) {
                        GleapMainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                GleapOverlayManager.getInstance().showBanner(currentAction, null);
                            }
                        });
                    } else if (currentAction.getString("actionType").contains("modal")) {
                        GleapMainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                // Get config from current action.
                                try {
                                    JSONObject config = currentAction.getJSONObject("config");
                                    GleapOverlayManager.getInstance().showModal(config, null);
                                } catch (Exception e) {
                                    // Do nothing.
                                }
                            }
                        });
                    } else {
                        // Unknown action.
                    }
                }
            }
        }
    }
}
