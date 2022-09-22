package io.gleap;

import org.json.JSONObject;

public interface ConfigLoadedCallback {
    void configLoaded(JSONObject jsonObject);
}

