package io.gleap.callbacks;

import org.json.JSONObject;

public interface GleapAgentToolHandler {
    void execute(JSONObject params, GleapAgentToolResultCallback callback);
}
