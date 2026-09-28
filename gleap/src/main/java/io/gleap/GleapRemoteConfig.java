package io.gleap;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Applies the remote config (GET /config/{sdkKey}) to {@link GleapConfig}: the flowConfig
 * look (button, colors, loader background), the feature gates (console logs, replays,
 * activation methods) and the network log rules. Values missing from the config keep their
 * current value. A value of the wrong type stops the parse there; everything before it is
 * applied.
 */
final class GleapRemoteConfig {
    private GleapRemoteConfig() {
    }

    static void apply(GleapConfig target, JSONObject config) {
        if (config != null) {
            target.plainConfig = config;
        }

        JSONObject flowConfigs = new JSONObject();
        if (config.has("flowConfig")) {
            try {
                flowConfigs = config.getJSONObject("flowConfig");
            } catch (JSONException e) {
                GleapLog.w("Invalid flowConfig in the remote config", e);
            }
        }

        try {
            if (flowConfigs.has("enableConsoleLogs")) {
                target.enableConsoleLogs = flowConfigs.getBoolean("enableConsoleLogs");
            }

            if (flowConfigs.has("feedbackButtonPosition")) {
                switch (flowConfigs.getString("feedbackButtonPosition")) {
                    case "BOTTOM_RIGHT":
                        target.widgetPosition = WidgetPosition.BOTTOM_RIGHT;
                        GleapOverlayManager.getInstance().setShowFab(true);
                        break;
                    case "BOTTOM_LEFT":
                        target.widgetPosition = WidgetPosition.BOTTOM_LEFT;
                        GleapOverlayManager.getInstance().setShowFab(true);
                        break;
                    case "BUTTON_CLASSIC":
                        target.widgetPosition = WidgetPosition.CLASSIC_RIGHT;
                        target.widgetPositionType = WidgetPositionType.CLASSIC;
                        GleapOverlayManager.getInstance().setShowFab(true);
                        break;
                    case "BUTTON_CLASSIC_LEFT":
                        target.widgetPosition = WidgetPosition.CLASSIC_LEFT;
                        target.widgetPositionType = WidgetPositionType.CLASSIC;
                        GleapOverlayManager.getInstance().setShowFab(true);
                        break;
                    case "BUTTON_CLASSIC_BOTTOM":
                        target.widgetPosition = WidgetPosition.CLASSIC_BOTTOM;
                        target.widgetPositionType = WidgetPositionType.CLASSIC;
                        GleapOverlayManager.getInstance().setShowFab(true);
                        break;
                    default:
                        target.widgetPosition = WidgetPosition.HIDDEN;

                        if (!target.isFeedbackButtonManuallySet()) {
                            GleapOverlayManager.getInstance().setShowFab(false);
                            target.hideFeedbackButton = true;
                        }
                        break;
                }
            }

            if (flowConfigs.has("widgetButtonText")) {
                target.widgetButtonText = flowConfigs.getString("widgetButtonText");
            }

            if (flowConfigs.has("buttonLogo") && !flowConfigs.getString("buttonLogo").equals("")) {
                target.buttonLogo = flowConfigs.getString("buttonLogo");
            }

            if (flowConfigs.has("color")) {
                target.color = flowConfigs.getString("color");
            }

            if (flowConfigs.has("buttonColor")) {
                target.buttonColor = flowConfigs.getString("buttonColor");
            }

            if (flowConfigs.has("backgroundColor")) {
                target.backgroundColor = flowConfigs.getString("backgroundColor");
            }

            if (flowConfigs.has("borderRadius")) {
                try {
                    target.borderRadius = flowConfigs.optInt("borderRadius", 20);
                } catch (Exception ignore) {
                }
            }

            if (flowConfigs.has("headerColor")) {
                target.headerColor = flowConfigs.getString("headerColor");
            }

            if (flowConfigs.has("headerColor2")) {
                target.headerColor2 = flowConfigs.getString("headerColor2");
            }

            if (flowConfigs.has("headerColor3")) {
                target.headerColor3 = flowConfigs.getString("headerColor3");
            }

            if (flowConfigs.has("bgType")) {
                target.bgType = flowConfigs.getString("bgType");
            }

            if (flowConfigs.has("bgImage")) {
                target.bgImage = flowConfigs.getString("bgImage");
            }

            if (flowConfigs.has("v")) {
                target.homeVersion = flowConfigs.getInt("v");
            }

            if (flowConfigs.has("fadebg")) {
                target.fadeBg = flowConfigs.getBoolean("fadebg");
            }

            if (flowConfigs.has("bgBlur")) {
                target.bgBlur = flowConfigs.getBoolean("bgBlur");
            }

            if (flowConfigs.has("enableReplays")) {
                target.enableReplays = flowConfigs.getBoolean("enableReplays");
            }

            if (flowConfigs.has("activationMethodShake")) {
                target.activationMethodShake = flowConfigs.getBoolean("activationMethodShake");
            }

            if (flowConfigs.has("activationMethodScreenshotGesture")) {
                target.activationMethodScreenshotGesture = flowConfigs.getBoolean("activationMethodScreenshotGesture");
            }

            if (flowConfigs.has("activationMethodFeedbackButton")) {
                target.activationMethodFeedbackButton = flowConfigs.getBoolean("activationMethodFeedbackButton");
            }

            if (flowConfigs.has("replaysInterval")) {
                target.interval = flowConfigs.getInt("replaysInterval");
                GleapBug.getInstance().setReplay(new Replay(60 / target.interval, 1000 * target.interval));
            }

            if (flowConfigs.has("buttonX")) {
                target.buttonX = flowConfigs.getInt("buttonX");
            }

            if (flowConfigs.has("buttonY")) {
                target.buttonY = flowConfigs.getInt("buttonY");
            }

            if (flowConfigs.has("networkLogPropsToIgnore")) {
                target.networkLogPropsToIgnore = flowConfigs.getJSONArray("networkLogPropsToIgnore");
            }

            if (flowConfigs.has("networkLogBlacklist")) {
                target.blackList = flowConfigs.getJSONArray("networkLogBlacklist");
            }
        } catch (JSONException e) {
            GleapLog.w("Could not read the remote config", e);
        }

        Gleap.getInstance().processOpenPushActions();
    }
}
