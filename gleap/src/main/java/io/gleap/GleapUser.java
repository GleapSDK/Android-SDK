package io.gleap;

import org.json.JSONObject;

public class GleapUser {
    private String userId;
    private GleapUserProperties gleapUserProperties;

    public GleapUser(String userId) {
        this.userId = userId;
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

    public void setGleapUserProperties(GleapUserProperties gleapUserProperties) {
        this.gleapUserProperties = gleapUserProperties;
    }

    public boolean compareTo(GleapUser otherUser) {
        if(otherUser == null) {
            return false;
        }

        boolean match = true;
        if(!this.userId.equals(otherUser.userId)) {
            match = false;
        }

        if(gleapUserProperties != null && otherUser.getGleapUserProperties() != null) {
            GleapUserProperties otherUserPropterties = otherUser.getGleapUserProperties();
            if (!this.gleapUserProperties.getName().equals(otherUserPropterties.getName())) {
                match = false;
            }
            if (!this.gleapUserProperties.getEmail().equals(otherUserPropterties.getEmail())) {
                match = false;
            }
            if (this.gleapUserProperties.getPhoneNumber() == null || otherUserPropterties.getPhoneNumber() == null ||!this.gleapUserProperties.getPhoneNumber().equals(otherUserPropterties.getPhoneNumber())) {
                match = false;
            }
            if (this.gleapUserProperties.getValue() != otherUserPropterties.getValue()) {
                match = false;
            }
        }

        return match;
    }
}
