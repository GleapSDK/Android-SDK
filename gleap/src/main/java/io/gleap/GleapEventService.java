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

class GleapEventService {
    private GleapArrayHelper<JSONObject> gleapArrayHelper;
    private static GleapEventService instance;
    private int time = GleapConfig.getInstance().getResceduleEventStreamDurationShort();
    private List<JSONObject> eventsToBeSent = new ArrayList<>();

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

    public void sendInitialMessage() {
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            private long time = 0;

            @Override
            public void run() {
                if (UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded()) {
                    try {
                        new InitialEventHttpHelper().execute();
                    } catch (Exception ex) {
                    }
                } else {
                    h.postDelayed(this, time);
                }
            }
        }, time); // 1 second delay (takes millis)
    }

    public void start() {
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            private long time = 0;

            @Override
            public void run() {
                if (UserSessionController.getInstance() != null && UserSessionController.getInstance().isSessionLoaded()) {
                    time = GleapConfig.getInstance().getResceduleEventStreamDurationLong();
                    new EventHttpHelper().execute();
                } else {
                    time = GleapConfig.getInstance().getResceduleEventStreamDurationShort();
                }
                h.postDelayed(this, time);
            }
        }, time);
    }

    public void addEvent(JSONObject event) {
        if (eventsToBeSent.size() == GleapConfig.getInstance().getMaxEventLength()) {
            eventsToBeSent = gleapArrayHelper.shiftArray(eventsToBeSent);
        }
        eventsToBeSent.add(event);
    }


    private JSONObject generateEvent(JSONObject obj) throws JSONException {
        JSONObject event = new JSONObject();
        event.put("date", dateToString(new Date()));
        event.put("name", "pageView");
        event.put("data", obj);
        return event;
    }

    private class InitialEventHttpHelper extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            try {
                int status = postEvent();
            } catch (Exception exception) {
                //    exception.printStackTrace();
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

            JSONArray jsonArray = new JSONArray();
            JSONObject event = new JSONObject();
            event.put("date", dateToString(new Date()));
            event.put("name", "sessionStarted");
            jsonArray.put(event);

            UserSession userSession = UserSessionController.getInstance().getUserSession();
            if (userSession != null) {
                conn.setRequestProperty("gleap-id", userSession.getId());
                conn.setRequestProperty("gleap-hash", userSession.getHash());
            }

            JSONObject body = new JSONObject();
            body.put("events", jsonArray);
            // body.put("time", PhoneMeta.calculateDurationInDouble());
            body.put("time", 300);
            body.put("opened", false);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.close();
                os.flush();
            }

            conn.getOutputStream().close();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }

                if (result != null) {
                    processData(result);

                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            conn.getInputStream().close();
            int status = conn.getResponseCode();
            conn.disconnect();
            return status;
        }

        private void processData(JSONObject data) throws Exception {
            if (data.has("a")) {
                JSONArray actions = data.getJSONArray("a");
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject currentAction = actions.getJSONObject(i);
                    if (currentAction.has("actionType")) {
                        if (currentAction.getString("actionType").contains("outbound")) {
                            GleapConfig.getInstance().setAction(new GleapAction(currentAction.getString("actionType"), currentAction.getString("outbound")));
                            try {
                                Gleap.getInstance().startFeedbackFlow(GleapConfig.getInstance().getAction().getActionType());
                            } catch (GleapNotInitialisedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (currentAction.getString("actionType").contains("notification")) {

                            //generates comment based on incoming message
                            JSONObject messageData = currentAction.getJSONObject("data");
                            Comment comment = createComment(messageData);
                            GleapInvisibleActivityManger.getInstance().addComment(comment);
                        }
                    }
                }
                GleapInvisibleActivityManger.getInstance().render(null);
            }
            if (data.has("u")) {
                System.out.println("Size" + data.get("u"));
            }
        }
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
            body.put("time", PhoneMeta.calculateDuration());
            body.put("opened", Gleap.getInstance().isOpened());


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
            } catch (Exception e) {
                e.printStackTrace();
            }

            conn.getInputStream().close();
            int status = conn.getResponseCode();
            conn.disconnect();
            return status;


        }

        private JSONArray arrayToJSONArray(List<JSONObject> arrayList) {
            JSONArray result = new JSONArray();
            for (JSONObject jsonObject :
                    arrayList) {
                result.put(jsonObject);
            }

            return result;
        }

        private void processData(JSONObject data) throws Exception {
            if (data.has("a")) {
                System.out.println(data.get("a"));
                JSONArray actions = data.getJSONArray("a");
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject currentAction = actions.getJSONObject(i);
                    if (currentAction.has("actionType")) {
                        if (currentAction.getString("actionType").contains("notification") && currentAction.has("data")) {
                            //generates comment based on incoming message
                            JSONObject messageData = currentAction.getJSONObject("data");
                            Comment comment = createComment(messageData);
                            GleapInvisibleActivityManger.getInstance().addComment(comment);
                        } else {
                            GleapConfig.getInstance().setAction(new GleapAction(currentAction.getString("actionType"), currentAction.getString("outbound")));
                            try {
                                Gleap.getInstance().startFeedbackFlow(GleapConfig.getInstance().getAction().getActionType());
                            } catch (GleapNotInitialisedException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }
                GleapInvisibleActivityManger.getInstance().render(null);
            }

            if (data.has("u")) {
                System.out.println("Size u:" + data.get("u"));
            }
        }
    }

    private Comment createComment(JSONObject messageData) throws Exception {
        String senderName = "";
        String profileImageUrl = "";

        String text = "";
        String type = "";

        String shareToken = "";

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

        GleapSender sender = new GleapSender(senderName, profileImageUrl);
        System.out.println(sender.toString());
        return new Comment(type, text, shareToken, sender);
    }
}
