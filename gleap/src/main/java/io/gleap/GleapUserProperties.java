package io.gleap;

public class GleapUserProperties {
    private String userId;
    private String name;
    private String email;

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

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
