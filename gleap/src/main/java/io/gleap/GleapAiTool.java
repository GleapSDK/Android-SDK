package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class GleapAiTool {
    public String name;
    public String toolDescription;
    public String response;
    public GleapAiToolParameter[] parameters;

    public GleapAiTool(String name, String toolDescription, String response, GleapAiToolParameter[] parameters) {
        this.name = name;
        this.toolDescription = toolDescription;
        this.response = response;
        this.parameters = parameters;
    }

    public JSONObject toJSONObject() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            jsonObject.put("description", toolDescription);
            jsonObject.put("response", response);

            JSONArray paramsArray = new JSONArray();
            for (GleapAiToolParameter param : parameters) {
                paramsArray.put(param.toJSONObject());
            }
            jsonObject.put("parameters", paramsArray);
        } catch (Exception exp) {}

        return jsonObject;
    }
}