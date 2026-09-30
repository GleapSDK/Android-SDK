package io.gleap;

import org.json.JSONObject;

/**
 * Opens what a tapped Gleap push notification points to (news article, checklist or
 * conversation) as soon as the SDK is ready and an activity is shown.
 */
final class GleapPushActions {
    private static OpenPushAction openPushAction;

    private GleapPushActions() {
    }

    static void handlePushNotification(JSONObject notificationData) {
        try {
            String type = "";
            String id = "";
            if (notificationData.has("type")) {
                type = notificationData.getString("type");
            }
            if (notificationData.has("id")) {
                id = notificationData.getString("id");
            }

            if (!type.isEmpty()) {
                openPushAction = new OpenPushAction(type, id);
                processOpenPushActions();
            }
        } catch (Exception ex) {
            GleapErrors.report(ex, "handlePushNotification");
        }
    }

    /**
     * Opens the pending push action 1.5 s from now, if the SDK is ready by then. Called again
     * whenever the SDK gets closer to ready (config loaded, session loaded, activity started).
     */
    static void processOpenPushActions() {
        try {
            GleapMainThread.postDelayed(new Runnable() {
                @Override
                public void run() throws RuntimeException {
                    try {
                        if (ActivityUtil.getCurrentActivity() == null) {
                            return;
                        }

                        if (GleapConfig.getInstance().getPlainConfig() == null) {
                            return;
                        }

                        if (GleapSessionController.getInstance() == null
                                || !GleapSessionController.getInstance().isSessionLoaded()) {
                            return;
                        }

                        if (GleapDetectorUtil.isWidgetOpen()) {
                            return;
                        }

                        try {
                            if (openPushAction != null) {
                                Gleap gleap = Gleap.getInstance();
                                switch (openPushAction.getType()) {
                                    case "news":
                                        gleap.openNewsArticle(openPushAction.getId(), true);
                                        break;
                                    case "checklist":
                                        gleap.openChecklist(openPushAction.getId(), true);
                                        break;
                                    case "conversation":
                                        gleap.openConversation(openPushAction.getId());
                                        break;
                                }

                                openPushAction = null;
                            }
                        } catch (Error | Exception error) {
                            GleapErrors.report(error, "processOpenPushActions - inner");
                        }
                    } catch (Error | Exception error) {
                        GleapErrors.report(error, "processOpenPushActions - outer");
                    }
                }
            }, 1500);
        } catch (Error | Exception error) {
            GleapErrors.report(error, "processOpenPushActions");
        }
    }
}
