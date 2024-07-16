package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

class JsonUtil {

    public static JSONObject mergeJSONObjects(JSONObject jsonObject, JSONObject jsonObject2) {
        JSONObject result = new JSONObject();
        try {
            JSONArray keys = jsonObject.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i); // Here's your key
                    Object value = jsonObject.get(key); // Here's your value
                    result.put(key, value);
                }
            }
            JSONArray keys2 = jsonObject2.names();
            if (keys2 != null) {
                for (int i = 0; i < keys2.length(); i++) {
                    String key = keys2.getString(i); // Here's your key
                    Object value = jsonObject2.get(key); // Here's your value
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
        }
        return result;
    }
}
