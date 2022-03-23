package io.gleap;

import org.json.JSONObject;

public interface FeedbackSentWithDataCallback {
    void close(JSONObject jsonObject);
}
