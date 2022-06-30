package io.gleap;

import org.json.JSONObject;

public class PrefillHelper {
    private static PrefillHelper instancen;
    private JSONObject jsonObject;

    public static PrefillHelper getInstancen() {
        if(instancen == null) {
            instancen = new PrefillHelper();
        }
        return instancen;
    }

    public void setPrefillData(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public JSONObject getPreFillData() {
        return jsonObject;
    }
}
