package io.gleap;

import static io.gleap.DateUtil.dateToString;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import gleap.io.gleap.BuildConfig;

class GleapEventService {
    private GleapArrayHelper<JSONObject> gleapArrayHelper;
    private static GleapEventService instance;
    private EventsSentCallback eventsSentCallback;
    private boolean disableInAppNotifications = false;
    private int time = GleapConfig.getInstance().getResceduleEventStreamDurationShort();
    private List<JSONObject> eventsToBeSent = new ArrayList<>();
    private Handler intervalHandler;

    private GleapEventService() {
        gleapArrayHelper = new GleapArrayHelper<>();
        sendInitialMessage();
    }
    
    public static GleapEventService getInstance() {
        if (instance == null) {
            instance = new GleapEventService();
        }
        return instance;
    }

    public void setDisableInAppNotifications(boolean disableInAppNotifications) {
        this.disableInAppNotifications = disableInAppNotifications;
    }

    public void sendInitialMessage() {
        final Handler h = new Handler(Looper.getMainLooper());

        h.postDelayed(new Runnable() {
            private long time = 0;

            @Override
            public void run() {
                if (UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded()) {
                    try {
                        new EventHttpHelper().execute();
                    } catch (Exception ex) {
                    }
                } else {
                    h.postDelayed(this, time);
                }
            }
        }, time);
    }

    public void start() {
        try {
            JSONObject sessiontStarted = new JSONObject();
            sessiontStarted.put("name", "sessionStarted");
            eventsToBeSent.add(sessiontStarted);

            Activity activity = ActivityUtil.getCurrentActivity();
            JSONObject pageView = new JSONObject();
            JSONObject page = new JSONObject();
            page.put("page", activity.getClass().getSimpleName());
            pageView.put("name", "pageView");
            pageView.put("data", page);
            eventsToBeSent.add(pageView);
        }catch (Exception ex) {}

        intervalHandler = new Handler(Looper.getMainLooper());
        intervalHandler.postDelayed(new Runnable() {
            private long time = 0;

            @Override
            public void run() {
                try {
                    if (UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded()) {
                        time = GleapConfig.getInstance().getResceduleEventStreamDurationLong();
                        new EventHttpHelper().execute();
                    } else {
                        time = GleapConfig.getInstance().getResceduleEventStreamDurationShort();
                    }
                    intervalHandler.postDelayed(this, time);
                } catch (Exception ignore) {
                }
            }
        }, time);
    }

    public void stop() {
        stop(true);
    }

    public void stop(Boolean clear) {
        if(clear) {
            eventsToBeSent.clear();
        }
        intervalHandler.removeCallbacksAndMessages(null);
    }

    public void addEvent(JSONObject event) {

        if (eventsToBeSent.size() == GleapConfig.getInstance().getMaxEventLength()) {
            eventsToBeSent = gleapArrayHelper.shiftArray(eventsToBeSent);
        }
        eventsToBeSent.add(event);
    }

    private class EventHttpHelper extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            try {
                int status = postEvent();
                if (status == 200) {
                    eventsToBeSent = new ArrayList<>();
                }
            } catch (Exception exception) {
            }
            return null;
        }

        private int postEvent() throws IOException, JSONException {
            URL url = new URL(GleapConfig.getInstance().getApiUrl() + "/sessions/ping");
            HttpURLConnection conn;
            if (GleapConfig.getInstance().getApiUrl().contains("https")) {
                conn = (HttpsURLConnection) url.openConnection();
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }

            conn.setRequestProperty("api-token", GleapConfig.getInstance().getSdkKey());
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestMethod("POST");

            UserSession userSession = UserSessionController.getInstance().getUserSession();
            if (userSession != null) {
                conn.setRequestProperty("gleap-id", userSession.getId());
                conn.setRequestProperty("gleap-hash", userSession.getHash());
            }

            JSONObject body = new JSONObject();
            body.put("events", arrayToJSONArray(eventsToBeSent));
            body.put("time", PhoneMeta.calculateDurationInDouble());
            body.put("opened", Gleap.getInstance().isOpened());
            body.put("sdkVersion", BuildConfig.VERSION_NAME);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.close();
                os.flush();
            }
            conn.getOutputStream().close();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }
                if (result != null) {
                    processData(result);
                }
            } catch (Error | Exception e) {
                // e.printStackTrace();
            }

            conn.getInputStream().close();
            int status = conn.getResponseCode();
            conn.disconnect();
            if(GleapEventService.getInstance().eventsSentCallback != null) {
                GleapEventService.getInstance().eventsSentCallback.invoked();
            }
            return status;
        }
    }

    private JSONArray arrayToJSONArray(List<JSONObject> arrayList) {
        JSONArray result = new JSONArray();
        for (JSONObject jsonObject :
                arrayList) {
            result.put(jsonObject);
        }

        return result;
    }

    private GleapChatMessage createComment(String outboundId, JSONObject messageData) throws Exception {
        String senderName = "";
        String profileImageUrl = "";
        String text = "";
        String type = "";
        String shareToken = "";
        String newsId = "";
        String coverImageUrl = "";

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

        if (messageData.has("coverImageUrl")) {
            coverImageUrl = messageData.getString("coverImageUrl");
        }

        GleapSender sender = new GleapSender(senderName, profileImageUrl);
        return new GleapChatMessage(outboundId, type, text, shareToken, sender, newsId, coverImageUrl);
    }

    private void processData(JSONObject data) throws Exception {
        if (data.has("u")) {
            GleapInvisibleActivityManger.getInstance().setMessageCounter(data.getInt("u"));
            GleapInvisibleActivityManger.getInstance().addFab(null);
        }

        if (data.has("a") && data.get("a") instanceof JSONArray) {
            JSONArray actions = data.getJSONArray("a");

            for (int i = 0; i < actions.length(); i++) {
                JSONObject currentAction = actions.getJSONObject(i);
                if (currentAction.has("actionType")) {
                    if (currentAction.getString("actionType").contains("notification")) {
                        // In app notification.
                        if (!this.disableInAppNotifications) {
                            String outboundId = "";
                            if(currentAction.has("outbound")){
                                outboundId = currentAction.getString("outbound");
                            }
                            JSONObject messageData = currentAction.getJSONObject("data");
                            GleapChatMessage comment = createComment(outboundId, messageData);
                            GleapInvisibleActivityManger.getInstance().addComment(comment);
                        }
                    } else if (currentAction.getString("format").contains("survey")) {
                        // Survey.
                        SurveyType surveyType = SurveyType.SURVEY;
                        if (currentAction.getString("format").contains("survey_full")) {
                            surveyType = SurveyType.SURVEY_FULL;
                        }

                        JSONObject jsonObject = new JSONObject();
                        try {
                            jsonObject.put("isSurvey", true);
                            jsonObject.put("hideBackButton", true);
                            jsonObject.put("format", currentAction.getString("format"));
                            jsonObject.put("flow", currentAction.getString("actionType"));
                        } catch (Exception ex) {
                        }

                        // Check if it is open
                        GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("start-survey", jsonObject));
                        Gleap.getInstance().open(surveyType);
                    } if (currentAction.getString("actionType").contains("banner")) {
                        GleapBanner banner = new GleapBanner(currentAction);
                        GleapInvisibleActivityManger.getInstance().showBanner(banner);
                    } else {
                        // Unknown action.
                    }
                }
            }
            GleapInvisibleActivityManger.getInstance().render(null, false);
        }
    }

    interface EventsSentCallback {
        void invoked();
    }

    public EventsSentCallback getEventsSentCallback() {
        return eventsSentCallback;
    }

    public void setEventsSentCallback(EventsSentCallback eventsSentCallback) {
        this.eventsSentCallback = eventsSentCallback;
    }
}
