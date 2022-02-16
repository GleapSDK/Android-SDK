package io.gleap;

import static io.gleap.DateUtil.dateToString;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Handler;

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

import javax.net.ssl.HttpsURLConnection;

class GleapEventService {
    private static GleapEventService instance;
    private final int CHECK_PAGE_INTERVAL = 5000;
    private int time = GleapConfig.getInstance().getResceduleEventStreamDurationShort();
    private String currentPage = "";
    private ArrayList<JSONObject> eventsToBeSent = new ArrayList<>();

    private GleapEventService(){
        sendInitialMessage();
        if(GleapBug.getInstance().getApplicationtype() == APPLICATIONTYPE.NATIVE ) {
            checkPage();
        }
    }

    public static GleapEventService getInstance(){
        if(instance == null){
            instance = new GleapEventService();
        }
        return instance;
    }

    public void sendInitialMessage() {
        final Handler h = new Handler();
        h.postDelayed(new Runnable() {
            private long time = 0;

            @Override
            public void run() {
                if (UserSessionController.getInstance().isSessionLoaded()) {
                    new InitialEventHttpHelper().execute();

                } else {
                    h.postDelayed(this, time);
                }

            }
        }, time); // 1 second delay (takes millis)
    }

    public void start() {
        if(GleapBug.getInstance().getApplicationtype() == APPLICATIONTYPE.NATIVE ) {
            final Handler h = new Handler();
            h.postDelayed(new Runnable() {
                private long time = 0;

                @Override
                public void run() {
                    if (eventsToBeSent.size() > 0 && UserSessionController.getInstance().isSessionLoaded()) {
                        time = GleapConfig.getInstance().getResceduleEventStreamDurationLong();
                        new EventHttpHelper().execute();

                    } else {
                        time = GleapConfig.getInstance().getResceduleEventStreamDurationShort();
                    }
                    h.postDelayed(this, time);
                }
            }, time); // 1 second delay (takes millis)
        }
    }
    public void addEvent(JSONObject event) {
        eventsToBeSent.add(event);
    }


    public void checkPage() {
        final Handler h = new Handler();
        h.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                Activity activity = ActivityUtil.getCurrentActivity();
                if(activity != null && !currentPage.equals(activity.getClass().getSimpleName()) && !activity.getClass().getSimpleName().contains("Gleap")){
                    currentPage = activity.getClass().getSimpleName();
                    JSONObject object = new JSONObject();
                    try {
                        object.put("page", activity.getClass().getSimpleName());
                        Gleap.getInstance().logEvent("pageView", object);
                        if(eventsToBeSent.size() == GleapConfig.getInstance().getMaxEventLength()){
                            eventsToBeSent = shiftArray(eventsToBeSent);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
                h.postDelayed(this, CHECK_PAGE_INTERVAL);
            }
        }, this.CHECK_PAGE_INTERVAL);
    }

    private JSONObject generateEvent(JSONObject obj) throws JSONException {
        JSONObject event = new JSONObject();
        event.put("date", dateToString(new Date()));
        event.put("name", "pageView");
        event.put("data", obj);
        return event;
    }

    private ArrayList<JSONObject> shiftArray(ArrayList<JSONObject> arrayList){
        ArrayList<JSONObject> tmp = new ArrayList<>();
        for (int i = 1; i < arrayList.size() - 1; i++) {
            tmp.add(arrayList.get(i));
        }
        return tmp;
    }

    private class InitialEventHttpHelper extends AsyncTask{

        @Override
        protected Object doInBackground(Object[] objects) {
            try {
                int status = postEvent();
            }catch (IOException | JSONException exception){
                exception.printStackTrace();
            }
            return null;
        }

        private int postEvent() throws IOException, JSONException {
            URL url = new URL(GleapConfig.getInstance().getApiUrl() + "/sessions/stream");
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
            if(userSession != null) {
                conn.setRequestProperty("gleap-id", userSession.getId());
                conn.setRequestProperty("gleap-hash", userSession.getHash());
            }
            JSONArray jsonArray = new JSONArray();
            JSONObject event = new JSONObject();
            event.put("date", dateToString(new Date()));
            event.put("name", "sessionStarted");
            jsonArray.put(event);
            JSONObject body = new JSONObject();
            body.put("events", jsonArray);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }

                if (result != null) {
                    if(result.has("actionType") && result.has("outbound")) {
                        GleapConfig.getInstance().setAction(new GleapAction(result.getString("actionType"), result.getString("outbound")));
                        try {
                            Gleap.getInstance().startFeedbackFlow(GleapConfig.getInstance().getAction().getActionType());
                        } catch (GleapNotInitialisedException e) {
                            e.printStackTrace();
                        }
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }


            return conn.getResponseCode();
        }
    }


    private class EventHttpHelper extends AsyncTask{

        @Override
        protected Object doInBackground(Object[] objects) {
            try {
                int status = postEvent();
                if(status == 200){
                    eventsToBeSent = new ArrayList<>();
                }
            }catch (IOException | JSONException exception){
                exception.printStackTrace();
            }
            return null;
        }

        private int postEvent() throws IOException, JSONException {
            URL url = new URL(GleapConfig.getInstance().getApiUrl() + "/sessions/stream");
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
            if(userSession != null) {
                conn.setRequestProperty("gleap-id", userSession.getId());
                conn.setRequestProperty("gleap-hash", userSession.getHash());
            }

            JSONObject body = new JSONObject();
            body.put("events", arrayToJSONArray(eventsToBeSent));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }

                if (result != null) {
                        if(result.has("actionType") && result.has("outbound")) {
                            GleapConfig.getInstance().setAction(new GleapAction(result.getString("actionType"), result.getString("outbound")));
                            try {
                                Gleap.getInstance().startFeedbackFlow(GleapConfig.getInstance().getAction().getActionType());
                            } catch (GleapNotInitialisedException e) {
                                e.printStackTrace();
                            }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return conn.getResponseCode();
        }

        private JSONArray arrayToJSONArray(ArrayList<JSONObject> arrayList){
            JSONArray result = new JSONArray();
            for (JSONObject jsonObject :
                    arrayList) {
                result.put(jsonObject);
            }

            return result;
        }
    }
}
