package io.gleap;

import org.json.JSONObject;

public class PrefillHelper {
    // Created with the class: getInstancen() is called from several threads.
    private static final PrefillHelper instancen = new PrefillHelper();
    private volatile JSONObject jsonObject;

    public static PrefillHelper getInstancen() {
        return instancen;
    }

    public void setPrefillData(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public JSONObject getPreFillData() {
        return jsonObject;
    }
}
