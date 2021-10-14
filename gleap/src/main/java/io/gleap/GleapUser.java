package io.gleap;

public class GleapUser {
    private String userId;
    private GleapUserProperties gleapUserProperties;

    public GleapUser(String userId) {
        this.userId = userId;
        this.gleapUserProperties = gleapUserProperties;
    }

    public GleapUser(String userId, GleapUserProperties gleapUserProperties) {
        this.userId = userId;
        this.gleapUserProperties = gleapUserProperties;
    }

    public String getUserId() {
        return userId;
    }

    public GleapUserProperties getGleapUserProperties() {
        return gleapUserProperties;
    }
}
