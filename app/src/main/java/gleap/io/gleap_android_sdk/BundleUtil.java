package gleap.io.gleap_android_sdk;

import android.os.Bundle;
import org.json.JSONObject;

public class BundleUtil {
    public static JSONObject toJsonObject(Bundle bundle) {
        JSONObject jsonObject = new JSONObject();
        try {
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value instanceof Integer || value instanceof Long || value instanceof Boolean || value instanceof Double || value instanceof String) {
                    jsonObject.put(key, value);
                } else if (value != null) { // Ensure value is not null before calling toString to avoid NullPointerException
                    jsonObject.put(key, value.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exception
        }
        return jsonObject;
    }
}