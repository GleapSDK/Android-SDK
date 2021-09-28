package io.gleap;

import java.net.URLEncoder;

public class GleapURLGenerator {
    public static String generateURL() {
        GleapBug bug = GleapBug.getInstance();
        GleapConfig config = GleapConfig.getInstance();
        String postfixUrl = "";
        try {
            if (config.getLanguage() != null) {
                if (postfixUrl.length() > 0) {
                    postfixUrl += "&lang=" + URLEncoder.encode(config.getLanguage(), "utf-8");
                } else {
                    postfixUrl += "?lang=" + URLEncoder.encode(config.getLanguage(), "utf-8");
                }
            }

            UserSessionController userSessionController = UserSessionController.getInstance();
            UserSession  userSession = userSessionController.getUserSession();
            if (postfixUrl.length() > 0) {
                postfixUrl += "&sessionId=" + userSession.getId();
            } else {
                postfixUrl += "?sessionId=" + userSession.getId();
            }

            if (postfixUrl.length() > 0) {
                postfixUrl += "&sessionHash=" + userSession.getHash();
            } else {
                postfixUrl += "?sessionHash=" + userSession.getHash();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }


        return postfixUrl;
    }
}
