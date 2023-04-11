package io.gleap;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class GleapUser {
    private String userId;
    private boolean isNew = false;
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

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        GleapUser otherUser = (GleapUser) obj;
        if (otherUser == null) {
            return false;
        }

        boolean match = true;
        if (!this.userId.equals(otherUser.userId)) {
            match = false;
        }

        if (gleapUserProperties != null && otherUser.getGleapUserProperties() != null) {
            GleapUserProperties otherUserPropterties = otherUser.getGleapUserProperties();
            if (this.gleapUserProperties.getName() != null && otherUserPropterties.getName() != null && !this.gleapUserProperties.getName().equals(otherUserPropterties.getName())) {
                match = false;
            }
            if (this.gleapUserProperties.getEmail() != null && otherUserPropterties.getEmail() != null && !this.gleapUserProperties.getEmail().equals(otherUserPropterties.getEmail())) {
                match = false;
            }
            if (this.gleapUserProperties.getPhoneNumber() != null && otherUserPropterties.getPhoneNumber() != null) {
                if (!this.gleapUserProperties.getPhoneNumber().equals(otherUserPropterties.getPhoneNumber())) {
                    match = false;
                }
            }
            if (this.gleapUserProperties.getValue() != otherUserPropterties.getValue()) {
                match = false;
            }

            JSONObject customData = gleapUserProperties.getCustomData();
            JSONObject otherUserData = otherUser.getGleapUserProperties().getCustomData();
            if (customData != null && otherUserData != null) {
                Iterator<String> keys = customData.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (otherUserData.has(key)) {
                        try {
                            if (!customData.get(key).equals(otherUserData.get(key))) {
                                match = false;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        match = false;
                    }
                }
            }

            if (customData != null && otherUserData == null) {
                match = false;
            }

            if (customData == null && otherUserData != null) {
                match = false;
            }
        }

        return match;
    }
}
