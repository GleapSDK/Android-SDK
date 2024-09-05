package io.gleap.callbacks;

import org.json.JSONObject;

public interface OutboundSentCallback {
    void invoke(JSONObject jsonObject);
}
