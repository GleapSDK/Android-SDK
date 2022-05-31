package io.gleap;

import java.net.URLEncoder;

class GleapURLGenerator {
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
            if (userSession != null) {
                postfixUrl += "&gleapId=" + userSession.getId();
                postfixUrl += "&gleapHash=" + userSession.getHash();
            }

            String feedBackFlow = GleapConfig.getInstance().getFeedbackFlow();
            if (!feedBackFlow.equals("")) {
                postfixUrl += "&startFlow=" + feedBackFlow;
                GleapConfig.getInstance().setFeedbackFlow("");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }


        return postfixUrl;
    }
}
