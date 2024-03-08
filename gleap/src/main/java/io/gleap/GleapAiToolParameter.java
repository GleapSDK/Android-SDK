package io.gleap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

public class GleapAiToolParameter {
    public String name;
    public String parameterDescription;
    public String type;
    public boolean required;
    public String[] enums;

    public GleapAiToolParameter(String name, String parameterDescription, String type, boolean required) {
        this.name = name;
        this.parameterDescription = parameterDescription;
        this.type = type;
        this.required = required;
    }

    public GleapAiToolParameter(String name, String parameterDescription, String type, boolean required, String[] enums) {
        this(name, parameterDescription, type, required); // Call the other constructor
        this.enums = enums;
    }

    public JSONObject toJSONObject() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("description", parameterDescription);
            jsonObject.put("type", type);
            jsonObject.put("required", required);

            if (enums != null && enums.length > 0) {
                jsonObject.put("enums", new JSONArray(enums));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return jsonObject;
    }
}