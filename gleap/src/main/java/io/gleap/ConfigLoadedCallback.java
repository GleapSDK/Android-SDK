package io.gleap.callbacks;

import org.json.JSONObject;

public interface ConfigLoadedCallback {
    void configLoaded(JSONObject jsonObject);
}

