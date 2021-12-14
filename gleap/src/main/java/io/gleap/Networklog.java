package io.gleap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

class Networklog {
    private final String url;
    private final RequestType requestType;
    private final JSONObject request;
    private JSONObject response;
    private final int status;
    private int duration = 0;
    private final Date date;

    public Networklog(String url, RequestType requestType, int status, int duration, JSONObject request, JSONObject response) {
        this.url = url;
        this.requestType = requestType;
        this.request = request;
        this.response = response;
        this.status = status;
        this.duration = duration;
        date = new Date();
        try {
            if (this.response == null) {
                this.response = new JSONObject();
            }
            this.response.put("status", status);
        } catch (Exception err) {
        }
    }

    public JSONObject toJSON() {
        JSONObject object = new JSONObject();
        try {
            object.put("date", DateUtil.dateToString(date));
            object.put("type", requestType.name());
            object.put("status", status);
            object.put("url", url);
            if (duration >= 0) {
                object.put("duration", duration);
            }
            object.put("success", true);
            if (request != null) {
                if(request.has("headers") && request.has("payload")) {
                    if(isJSONValid(request.getString("headers"))) {
                        String objString = request.getString("headers");
                        JSONObject obj = new JSONObject(objString);
                        stripObject(obj);
                        request.put("headers", obj);
                    }
                    if(isJSONValid(request.getString("payload"))) {
                        String reStr = request.getString("payload");
                        JSONObject re = new JSONObject(reStr);
                        stripObject(re);
                        request.put("payload", re);
                    }

                    object.put("request", request);
                }else {
                    stripObject(request);
                    object.put("request", request);
                }
            }
            if (response != null) {
                object.put("response", response);
            }
        } catch (Exception err) {
    err.printStackTrace();
        }
        return object;
    }

    private void stripObject(JSONObject object){
        JSONArray stripWords = GleapConfig.getInstance().getNetworkLogPropsToIgnore();
        for (int i = 0; i < stripWords.length(); i++) {
            try {
                String key = stripWords.getString(i);
                object.remove(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            // edited, to include @Arthur's comment
            // e.g. in case JSONArray is valid as well...
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }
}
