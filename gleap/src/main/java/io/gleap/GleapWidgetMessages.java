package io.gleap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The messages the SDK sends to the widget (messenger web app): {@code {"name": ..., "data": ...}}.
 */
final class GleapWidgetMessages {
    private GleapWidgetMessages() {
    }

    static String message(String name, Object data) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("name", name);
        message.put("data", data);
        return message.toString();
    }

    /**
     * config-update: the remote config the widget renders with (its flow config with the active
     * color scheme applied), and the language.
     */
    static JSONObject configUpdate() throws JSONException {
        JSONObject jsonObject = GleapConfig.getInstance().getPlainConfig();
        JSONObject data = new JSONObject();
        JSONObject flowConfig = GleapConfig.getInstance().getThemedFlowConfig();
        if (flowConfig == null) {
            throw new JSONException("No flowConfig");
        }
        data.put("config", flowConfig);
        data.put("actions", jsonObject.getJSONObject("projectActions"));
        data.put("overrideLanguage", GleapConfig.getInstance().getLanguage());
        data.put("isApp", true);
        return data;
    }

    /**
     * session-update: the session the widget authenticates with, the identified user and the
     * API and realtime hosts of the region.
     */
    static JSONObject sessionUpdate() throws JSONException {
        GleapSession gleapSession = GleapSessionController.getInstance().getUserSession();
        GleapSessionProperties user = GleapSessionController.getInstance().getGleapUserSession();
        JSONObject sessionData = new JSONObject();
        sessionData.put("gleapId", gleapSession.getId());
        sessionData.put("gleapHash", gleapSession.getHash());
        if (user != null) {
            if (user.getUserId() != null) {
                sessionData.put("userId", user.getUserId());
            }
            if (user.getName() != null) {
                sessionData.put("name", user.getName());
            }
            if (user.getEmail() != null) {
                sessionData.put("email", user.getEmail());
            }
            sessionData.put("value", user.getValue());
            sessionData.put("sla", user.getSla());
            if (user.getPhone() != null) {
                sessionData.put("phone", user.getPhone());
            }
            if (user.getCompanyName() != null) {
                sessionData.put("companyName", user.getCompanyName());
            }
            if (user.getAvatar() != null) {
                sessionData.put("avatar", user.getAvatar());
            }
            if (user.getPlan() != null) {
                sessionData.put("plan", user.getPlan());
            }
            if (user.getCompanyId() != null) {
                sessionData.put("companyId", user.getCompanyId());
            }
        }

        JSONObject data = new JSONObject();
        data.put("sessionData", sessionData);
        data.put("apiUrl", GleapConfig.getInstance().getApiUrl());
        String realtimeHost = GleapConfig.getInstance().getRealtimeHost();
        if (realtimeHost != null) {
            data.put("realtimeHost", realtimeHost);
        }
        data.put("sdkKey", GleapConfig.getInstance().getSdkKey());
        return data;
    }

    /**
     * The part of collect-ticket-data that is ready right away (the logs are added later).
     */
    static JSONObject ticketData() throws JSONException {
        GleapBug gleapBug = GleapBug.getInstance();
        JSONObject data = new JSONObject();
        data.put("formData", gleapBug.getTicketAttributes());
        data.put("customData", gleapBug.getCustomData());
        data.put("customEventLog", gleapBug.getCustomEventLog());

        PhoneMeta phoneMeta = gleapBug.getPhoneMeta();
        if (phoneMeta != null) {
            data.put("metaData", phoneMeta.getJSONObj());
        }

        try {
            data.put("tags", new JSONArray(gleapBug.getTags()));
        } catch (Exception ex) {
        }
        return data;
    }

    /**
     * The answer to send-feedback: feedback-sent (with the conversation's share token) when the
     * ticket was created, feedback-sending-failed otherwise.
     */
    static String feedbackResult(JSONObject response) throws JSONException {
        if (response.has("status") && response.getInt("status") == 201) {
            JSONObject message = new JSONObject();
            String shareToken = shareToken(response);
            if (!shareToken.equals("")) {
                message.put("shareToken", shareToken);
            }
            return message("feedback-sent", message);
        }

        JSONObject message = new JSONObject();
        message.put("data", "Something went wrong, please try again.");
        message.put("name", "feedback-sending-failed");
        return message.toString();
    }

    private static String shareToken(JSONObject httpResponse) {
        try {
            if (httpResponse.has("response")) {
                JSONObject response = httpResponse.getJSONObject("response");
                if (response.has("shareToken")) {
                    return response.getString("shareToken");
                }
            }
        } catch (Exception ignore) {
        }
        return "";
    }
}
