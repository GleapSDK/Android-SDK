package io.gleap.callbacks;

import org.json.JSONObject;

public interface FeedbackSentCallback {
    void invoke(JSONObject jsonObject);
}
