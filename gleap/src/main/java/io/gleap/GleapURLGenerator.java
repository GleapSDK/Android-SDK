package io.gleap;

import java.net.URLEncoder;

class GleapURLGenerator {
    public static String generateURL() {
        GleapConfig config = GleapConfig.getInstance();
        String postfixUrl = "";
        try {
            if (config.getLanguage() != null) {
                postfixUrl += "?lang=" + URLEncoder.encode(config.getLanguage(), "utf-8");
            }

            GleapSessionController userSessionController = GleapSessionController.getInstance();
            if(userSessionController != null) {
                GleapSession gleapSession = userSessionController.getUserSession();
                if (gleapSession != null) {
                    postfixUrl += "&gleapId=" + gleapSession.getId();
                    postfixUrl += "&gleapHash=" + gleapSession.getHash();
                }
            }
        } catch (Exception ex) {
        }

        return postfixUrl;
    }
}
