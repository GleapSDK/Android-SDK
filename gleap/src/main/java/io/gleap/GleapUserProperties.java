package io.gleap;

import org.json.JSONObject;

import java.util.Objects;

public class GleapUserProperties {
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
    public GleapUserProperties() {
    }

    /**
     * Create a gleap user. This can be used to identify the user.
     * The hash is as HMAC hash. This hash is created with your secret.
     * @param userId id of the user
     */
    public GleapUserProperties(String userId) {
        this.userId = userId;
    }

    /**
     * Create a gleap user. This can be used to identify the user.
     * The hash is as HMAC hash. This hash is created with your secret.
     * @param name Name of the user
     * @param email Email of the user
     */
    public GleapUserProperties(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public GleapUserProperties(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public GleapUserProperties(String userId, String name, String email, String hash) {
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
}
