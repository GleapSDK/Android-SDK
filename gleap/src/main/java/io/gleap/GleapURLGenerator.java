package io.gleap;

import java.net.URLEncoder;

public class GleapURLGenerator {
    public static String generateURL() {
        GleapBug bug = GleapBug.getInstance();
        GleapConfig config = GleapConfig.getInstance();
        String postfixUrl = "";
        try {
            if (config.getLanguage() != null) {
                postfixUrl += "?lang=" + URLEncoder.encode(config.getLanguage(), "utf-8");
            }

            UserSessionController userSessionController = UserSessionController.getInstance();
            UserSession userSession = userSessionController.getUserSession();
            postfixUrl += "&sessionId=" + userSession.getId();
            postfixUrl += "&sessionHash=" + userSession.getHash();
        } catch (Exception ex) {
            ex.printStackTrace();
        }


        return postfixUrl;
    }
}
