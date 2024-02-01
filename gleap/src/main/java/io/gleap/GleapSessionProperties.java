package io.gleap;

import org.json.JSONObject;

public class GleapSessionProperties {
    private String userId;
    private String name;
    private String email;
    private String hash;
    private double value;
    private String phone;
    private String plan;
    private String companyId;
    private String companyName;
    private JSONObject customData;

    /**
     * Create a gleap user. This can be used to identify the user.
     * The hash is as HMAC hash. This hash is created with your secret.
     */
    public GleapSessionProperties() {
    }

    /**
     * Create a gleap user. This can be used to identify the user.
     * The hash is as HMAC hash. This hash is created with your secret.
     * @param userId id of the user
     */
    public GleapSessionProperties(String userId) {
        this.userId = userId;
    }

    /**
     * Create a gleap user. This can be used to identify the user.
     * The hash is as HMAC hash. This hash is created with your secret.
     * @param name Name of the user
     * @param email Email of the user
     */
    public GleapSessionProperties(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public GleapSessionProperties(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public GleapSessionProperties(String userId, String name, String email, String hash) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.hash = hash;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public JSONObject getCustomData() {
        return customData;
    }

    public void setCustomData(JSONObject customData) {
        this.customData = customData;
    }

    public JSONObject getJSONPayload() {
        JSONObject jsonObject = new JSONObject();

        try {
            if (this.getEmail() != null && !this.getEmail().isEmpty()) {
                jsonObject.put("email", this.getEmail());
            }
            if (this.getName() != null && !this.getName().isEmpty()) {
                jsonObject.put("name", this.getName());
            }
            if (this.getHash() != null && !this.getHash().isEmpty()) {
                jsonObject.put("userHash", this.getHash());
            }
            if (this.getValue() != 0) {
                jsonObject.put("value", this.getValue());
            }
            if (this.getPhone() != null && !this.getPhone().isEmpty()) {
                jsonObject.put("phone", this.getPhone());
            }
            if (this.getPlan() != null && !this.getPlan().isEmpty()) {
                jsonObject.put("plan", this.getPlan());
            }
            if (this.getCompanyName() != null && !this.getCompanyName().isEmpty()) {
                jsonObject.put("companyName", this.getCompanyName());
            }
            if (this.getCompanyId() != null && !this.getCompanyId().isEmpty()) {
                jsonObject.put("companyId", this.getCompanyId());
            }

            JSONObject customData = this.getCustomData();
            if (customData != null) {
                jsonObject = JsonUtil.mergeJSONObjects(jsonObject, customData);
            }

            // Append language.
            jsonObject.put("lang", GleapConfig.getInstance().getLanguage());
        } catch (Exception ex) {

        }

        return jsonObject;
    }

    public static GleapSessionProperties fromJSONObject(JSONObject result) {
        if (result == null) {
            return null;
        }

        GleapSessionProperties gleapSessionProperties = new GleapSessionProperties();

        try {
            if (result.has("name")) {
                gleapSessionProperties.setName(result.getString("name"));
            }

            if (result.has("email")) {
                gleapSessionProperties.setEmail(result.getString("email"));
            }

            if (result.has("value")) {
                gleapSessionProperties.setValue(result.getDouble("value"));
            }

            if (result.has("companyName")) {
                gleapSessionProperties.setCompanyName(result.getString("companyName"));
            }

            if (result.has("companyId")) {
                gleapSessionProperties.setCompanyId(result.getString("companyId"));
            }

            if (result.has("plan")) {
                gleapSessionProperties.setPlan(result.getString("plan"));
            }

            if (result.has("customData")) {
                JSONObject customData = result.getJSONObject("customData");
                customData.remove("lang");
                gleapSessionProperties.setCustomData(result.getJSONObject("customData"));
            }

            if (result.has("userId")) {
                gleapSessionProperties.setUserId(result.getString("userId"));
            }
        } catch (Exception id) {

        }

        return gleapSessionProperties;
    }
}
