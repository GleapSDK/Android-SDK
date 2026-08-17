package io.gleap;

class GleapSender {
    private String name;
    private String profileImageUrl;
    // Absent on payloads from servers that don't send it yet — those fall
    // through to the teammate avatar shape.
    private boolean isBot;

    public GleapSender(String name, String profileImageUrl) {
        this(name, profileImageUrl, false);
    }

    public GleapSender(String name, String profileImageUrl, boolean isBot) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.isBot = isBot;
    }

    public String getName() {
        return name;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public boolean isBot() {
        return isBot;
    }

    @Override
    public String toString() {
        return "GleapSender{" +
                "name='" + name + '\'' +
                ", profileImageUrl='" + profileImageUrl + '\'' +
                ", isBot=" + isBot +
                '}';
    }
}
