package io.gleap;

import org.json.JSONException;
import org.json.JSONObject;

public class GleapWebViewMessage {
    private String message;

    public GleapWebViewMessage(String name, JSONObject objMessage) {
        try {
            this.message = generateGleapMessage(name, objMessage);
        }catch (Exception ex) {}
    }

    private String generateGleapMessage(String name, JSONObject data) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("name", name);
        message.put("data", data);

        return message.toString();
    }

    public String getMessage() {
        return message;
    }
}
