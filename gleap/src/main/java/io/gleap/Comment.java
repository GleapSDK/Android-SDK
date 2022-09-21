package io.gleap;

class Comment {
    private String type = "comment";
    private String text;
    private String shareToken;

    private GleapSender sender;


    public Comment(String type, String text, String shareToken, GleapSender sender) {
        this.sender = sender;
        this.type = type;
        this.text = text;
        this.shareToken = shareToken;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public GleapSender getSender() {
        return sender;
    }

    public String getShareToken() {
        return shareToken;
    }
}
