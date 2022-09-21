package io.gleap;

public class GleapSender {
    private String name;
    private String profileImageUrl;

    public GleapSender(String name, String profileImageUrl) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
    }

    public String getName() {
        return name;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    @Override
    public String toString() {
        return "GleapSender{" +
                "name='" + name + '\'' +
                ", profileImageUrl='" + profileImageUrl + '\'' +
                '}';
    }
}
