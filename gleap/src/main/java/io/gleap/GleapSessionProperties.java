package io.gleap;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class GleapSessionProperties {
    private String userId;
    private String name;
    private String email;
    private String hash;
    private double value;
    private String avatar;
    private String phone;
    private String plan;
    private String companyId;
    private String companyName;
    private double sla;
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

    public double getSla() {
        return sla;
    }

    public void setSla(double sla) {
        this.sla = sla;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
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
            if (this.getUserId() != null && !this.getUserId().isEmpty()) {
                jsonObject.put("userId", this.getUserId());
            }
            if (this.getHash() != null && !this.getHash().isEmpty()) {
                jsonObject.put("userHash", this.getHash());
            }
            if (this.getValue() != 0) {
                jsonObject.put("value", this.getValue());
            }
            if (this.getSla() != 0) {
                jsonObject.put("sla", this.getSla());
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
            if (this.getAvatar() != null && !this.getAvatar().isEmpty()) {
                jsonObject.put("avatar", this.getAvatar());
            }
            if (this.getCompanyId() != null && !this.getCompanyId().isEmpty()) {
                jsonObject.put("companyId", this.getCompanyId());
            }

            // Merge with custom data - flat.
            JSONObject customData = this.getCustomData();
            if (customData != null && customData.length() > 0) {
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
            if (result.has("userId")) {
                gleapSessionProperties.setUserId(result.getString("userId"));
            }

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

            if (result.has("avatar")) {
                gleapSessionProperties.setAvatar(result.getString("avatar"));
            }

            if (result.has("sla")) {
                gleapSessionProperties.setSla(result.getDouble("sla"));
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

    @Override
    public boolean equals(@Nullable Object obj) {
        GleapSessionProperties otherUserPropterties = (GleapSessionProperties) obj;

        if (otherUserPropterties == null) {
            return false;
        }

        if (this.userId == null || !this.userId.equals(otherUserPropterties.userId)) {
            return false;
        }

        if (this.getName() != null && otherUserPropterties.getName() != null && !this.getName().equals(otherUserPropterties.getName())) {
            return false;
        }

        if (this.getEmail() != null && otherUserPropterties.getEmail() != null && !this.getEmail().equals(otherUserPropterties.getEmail())) {
            return false;
        }

        if (this.getPhone() != null && otherUserPropterties.getPhone() != null) {
            if (!this.getPhone().equals(otherUserPropterties.getPhone())) {
                return false;
            }
        }

        if (this.getPlan() != null && otherUserPropterties.getPlan() != null) {
            if (!this.getPlan().equals(otherUserPropterties.getPlan())) {
                return false;
            }
        }

        if (this.getCompanyName() != null && otherUserPropterties.getCompanyName() != null) {
            if (!this.getCompanyName().equals(otherUserPropterties.getCompanyName())) {
                return false;
            }
        }

        if (this.getAvatar() != null && otherUserPropterties.getAvatar() != null) {
            if (!this.getAvatar().equals(otherUserPropterties.getAvatar())) {
                return false;
            }
        }

        if (this.getCompanyId() != null && otherUserPropterties.getCompanyId() != null) {
            if (!this.getCompanyId().equals(otherUserPropterties.getCompanyId())) {
                return false;
            }
        }

        if (this.getValue() != otherUserPropterties.getValue()) {
            return false;
        }

        if (this.getSla() != otherUserPropterties.getSla()) {
            return false;
        }

        JSONObject customData = this.getCustomData();
        JSONObject otherUserData = otherUserPropterties.getCustomData();

        if (customData != null && otherUserData != null) {
            Iterator<String> keys = otherUserData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (customData.has(key)) {
                    try {
                        Object obj1 = customData.get(key);
                        Object obj2 = otherUserData.get(key);

                        // If both objects are JSONObjects, compare them as JSONObjects
                        if (obj1 instanceof JSONObject && obj2 instanceof JSONObject) {
                            JSONObject json1 = (JSONObject) obj1;
                            JSONObject json2 = (JSONObject) obj2;

                            if (!json1.equals(json2)) {
                                return false;
                            }
                        } else {
                            // Convert both objects to strings for other types
                            String str1 = (obj1 == null) ? "" : obj1.toString();
                            String str2 = (obj2 == null) ? "" : obj2.toString();
                            boolean isEqual = str1.equals(str2);

                            if (!isEqual) {
                                return false;
                            }
                        }
                    } catch (Exception e) {}
                } else {
                    return false;
                }
            }
        }

        if (customData == null && otherUserData != null) {
            return false;
        }

        return true;
    }
}
