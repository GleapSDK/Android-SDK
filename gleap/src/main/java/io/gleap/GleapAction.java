package io.gleap;

import org.json.JSONObject;

class GleapAction {
    private String command;
    private JSONObject data;

    public GleapAction(String command, JSONObject data) {
        this.command = command;
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public JSONObject getData() {
        return data;
    }
}
