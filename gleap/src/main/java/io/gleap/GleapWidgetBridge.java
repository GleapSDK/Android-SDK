package io.gleap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.List;

import io.gleap.callbacks.GleapAgentToolResultCallback;

/**
 * The widget's calls into the SDK: the messenger web app calls
 * {@code GleapJSBridge.gleapCallback(json)} with a command, which runs on the main thread.
 */
final class GleapWidgetBridge {
    private final WeakReference<GleapMainActivity> activityRef;

    GleapWidgetBridge(GleapMainActivity activity) {
        activityRef = new WeakReference<>(activity);
    }

    @JavascriptInterface
    public void gleapCallback(final String object) {
        final GleapMainActivity activity = activityRef.get();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject gleapCallback = new JSONObject(object);
                    String command = gleapCallback.getString("name");

                    switch (command) {
                        case "ping":
                            sendConfigUpdate(activity);
                            sendSessionUpdate(activity);
                            GleapCaptureCoordinator.getInstance().sendCapabilities(activity);
                            sendPrefillData(activity);
                            sendScreenshotUpdate(activity);
                            sendPendingActions(activity);
                            // The result of a capture that ended while the widget was closed.
                            GleapCaptureCoordinator.getInstance().deliverPending(activity);

                            GleapMainThread.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    JSONObject data = new JSONObject();
                                    try {
                                        data.put("isWidgetOpen", true);
                                    } catch (JSONException e) {
                                        GleapLog.w("Could not build the widget status", e);
                                    }
                                    try {
                                        activity.sendMessage(GleapWidgetMessages.message("widget-status-update", data));
                                    } catch (JSONException e) {
                                        GleapLog.w("Could not send the widget status", e);
                                    }
                                }
                            }, 100);
                            activity.revealWidget();
                            break;
                        case "cleanup-drawings":
                            GleapBug.getInstance().setScreenshot(null);
                            break;
                        case "tool-execution":
                            try {
                                if (GleapCallbacks.getInstance().getAiToolExecutedCallback() != null) {
                                    GleapCallbacks.getInstance().getAiToolExecutedCallback().aiToolExecuted(gleapCallback.getJSONObject("data"));
                                }
                            } catch (Exception exp) {
                            }
                            break;
                        case "frontend-tool-execute":
                            try {
                                GleapAgentToolManager.getInstance().executeTool(gleapCallback.getJSONObject("data"), new GleapAgentToolResultCallback() {
                                    @Override
                                    public void onResult(final Object resultData) {
                                        activity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    activity.sendMessage(GleapWidgetMessages.message("frontend-tool-result", (JSONObject) resultData));
                                                } catch (Error | Exception ignore) {
                                                }
                                            }
                                        });
                                    }
                                });
                            } catch (Exception exp) {
                            }
                            break;
                        case "collect-ticket-data":
                            collectTicketData();
                            break;
                        case "close-widget":
                            activity.closeMainGleapActivity();
                            break;
                        case "screenshot-updated":
                            updateScreenshot(gleapCallback);
                            break;
                        case "run-custom-action":
                            customActionCalled(gleapCallback);
                            break;
                        case "open-url":
                            openExternalURL(activity, gleapCallback);
                            break;
                        case "notify-event":
                            notifyEvent(gleapCallback);
                            break;
                        case "send-feedback":
                            sendFeedback(gleapCallback);
                            break;
                        case "height-update":
                            activity.onWidgetContent();
                            break;
                        case "survey-shown":
                            activity.onWidgetContent();
                            activity.onSurveyShown(gleapCallback.optJSONObject("data"));
                            break;
                        case "survey-legacy":
                            activity.onSurveyLegacy();
                            break;
                        case "survey-answered":
                        case "survey-completed":
                        case "survey-closed":
                        case "survey-step-viewed":
                        case "sheet-viewport":
                            // Surveys 2.0 lifecycle and shell layout: handled by the page;
                            // completion reaches the app as notify-event outbound-sent.
                            break;
                        case "capture-start":
                        case "capture-cancel":
                        case "capture-done":
                        case "capture-editor":
                            // Only from the messenger page; nothing is captured before the
                            // customer taps the SDK's own bar.
                            GleapCaptureCoordinator.getInstance().onWidgetMessage(activity, command,
                                    gleapCallback.optJSONObject("data"));
                            break;
                    }
                } catch (Exception err) {
                }
            }
        });
    }

    private void collectTicketData() {
        try {
            final JSONObject data = GleapWidgetMessages.ticketData();
            final boolean withConsoleLogs = GleapConfig.getInstance().isEnableConsoleLogs();
            final GleapBug gleapBug = GleapBug.getInstance();

            // Reading logcat and preparing the network logs takes a moment: not on the main thread.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONArray networkLogs = new JSONArray();
                    JSONArray consoleLog = null;
                    try {
                        networkLogs = gleapBug.getNetworklogs();
                        if (withConsoleLogs) {
                            consoleLog = gleapBug.getLogs();
                        }
                    } catch (Error | Exception ignore) {
                    }

                    final JSONArray finalNetworkLogs = networkLogs;
                    final JSONArray finalConsoleLog = consoleLog;
                    final GleapMainActivity activity = activityRef.get();
                    if (activity == null) {
                        return;
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                data.put("networkLogs", finalNetworkLogs);
                                if (finalConsoleLog != null) {
                                    data.put("consoleLog", finalConsoleLog);
                                }
                                activity.sendMessage(GleapWidgetMessages.message("collect-ticket-data", data));
                            } catch (Error | Exception ignore) {
                            }
                        }
                    });
                }
            }, "gleap-collect-ticket-data").start();
        } catch (Error | Exception ignore) {
        }
    }

    private static void customActionCalled(JSONObject object) {
        try {
            String data = object.getString("data");
            if (GleapCallbacks.getInstance().getCustomActions() != null) {
                String shareToken = null;
                if (object.has("shareToken")) {
                    shareToken = object.getString("shareToken");
                }

                GleapCallbacks.getInstance().getCustomActions().invoke(data, shareToken);
            }
        } catch (JSONException e) {
            GleapLog.w("Invalid custom action message", e);
        }
    }

    private static void openExternalURL(GleapMainActivity activity, JSONObject object) {
        try {
            String url = object.getString("data");
            if (url != null && url.length() > 0) {
                if (!GleapExternalLinks.mayOpen(url)) {
                    GleapLog.w("Blocked a link with a disallowed scheme");
                    return;
                }
                if (Gleap.internalCloseWidgetOnExternalLinkOpen) {
                    GleapMainActivity.setUrlToOpenAfterClose(url);
                    activity.closeMainGleapActivity();
                } else {
                    Gleap.getInstance().handleLink(url);
                }
            }
        } catch (Exception e) {
        }
    }

    private static void notifyEvent(JSONObject object) {
        try {
            JSONObject data = object.getJSONObject("data");
            String eventType = data.getString("type");
            JSONObject eventData = data.getJSONObject("data");

            if (eventType.equals("flow-started")) {
                if (GleapCallbacks.getInstance().getFeedbackFlowStartedCallback() != null) {
                    GleapCallbacks.getInstance().getFeedbackFlowStartedCallback().invoke(eventData.toString());
                }
            } else if (eventType.equals("outbound-sent")) {
                surveyCompleted(eventData);
            }
        } catch (Exception ex) {
        }
    }

    /**
     * A Surveys 2.0 survey was completed. The messenger saves its answers itself (no
     * send-feedback), so the callbacks and the outbound-&lt;id&gt;-submitted event a legacy survey
     * gets after sending (see HttpHelper) come from here, in the legacy shape.
     */
    static void surveyCompleted(JSONObject eventData) {
        if (eventData == null) {
            return;
        }
        JSONObject formData = eventData.optJSONObject("formData");
        if (formData == null) {
            formData = new JSONObject();
        }

        try {
            if (GleapCallbacks.getInstance().getFeedbackSentCallback() != null) {
                GleapCallbacks.getInstance().getFeedbackSentCallback().invoke(formData);
            }
        } catch (Exception ignore) {
        }

        String outboundId = eventData.optString("outboundId", "");
        if (outboundId.length() == 0) {
            return;
        }

        try {
            if (GleapCallbacks.getInstance().getOutboundSentCallback() != null) {
                JSONObject sent = new JSONObject();
                sent.put("outboundId", outboundId);
                sent.putOpt("outbound", eventData.opt("outbound"));
                sent.put("formData", formData);
                sent.putOpt("responseId", eventData.opt("responseId"));
                sent.putOpt("endingId", eventData.opt("endingId"));
                GleapCallbacks.getInstance().getOutboundSentCallback().invoke(sent);
            }
        } catch (Exception ignore) {
        }

        try {
            Gleap.getInstance().trackEvent("outbound-" + outboundId + "-submitted", formData);
        } catch (Exception ignore) {
        }
    }

    private static void sendPendingActions(GleapMainActivity activity) {
        List<GleapAction> queue = GleapActionQueueHandler.getInstance().getActionQueue();
        for (GleapAction action : queue) {
            try {
                activity.sendMessage(GleapWidgetMessages.message(action.getCommand(), action.getData()));
            } catch (JSONException e) {
                GleapLog.w("Could not send a pending widget action", e);
            }
        }

        List<GleapWebViewMessage> messages = GleapConfig.getInstance().getGleapWebViewMessages();
        for (GleapWebViewMessage message : messages) {
            activity.sendMessage(message.getMessage());
        }
        GleapActionQueueHandler.getInstance().clearActionMessageQueue();
        GleapConfig.getInstance().clearGleapWebViewMessages();
    }

    private static void updateScreenshot(JSONObject object) {
        if (object.has("data")) {
            try {
                String base64String = object.getString("data");
                if (base64String != null) {
                    String base64Image = base64String.split(",")[1];
                    byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    GleapBug.getInstance().setScreenshot(decodedByte);
                }
            } catch (Exception e) {
                GleapLog.w("Could not read the edited screenshot", e);
            }
        }
    }

    private void sendFeedback(final JSONObject jsonObject) {
        final GleapMainActivity activity = activityRef.get();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject data = jsonObject.getJSONObject("data");
                    GleapBug gleapBug = GleapBug.getInstance();
                    try {
                        JSONObject action = data.getJSONObject("action");
                        if (action.has("feedbackType")) {
                            gleapBug.setType(action.getString("feedbackType"));
                        }

                        if (action.has("excludeData")) {
                            GleapConfig.getInstance().setStripModel(action.getJSONObject("excludeData"));
                        }

                        if (data.has("outboundId")) {
                            gleapBug.setOutboundId(data.getString("outboundId"));
                        }

                        if (data.has("spamToken")) {
                            gleapBug.setSpamToken(data.getString("spamToken"));
                        }

                        if (data.has("formData")) {
                            JSONObject formData = data.getJSONObject("formData");
                            gleapBug.setData(formData);
                        }
                    } catch (JSONException e) {
                        GleapLog.w("Invalid feedback data from the widget", e);
                    }

                    HttpHelper.send(activity, activity.getApplicationContext());
                } catch (Exception ex) {
                }
            }
        });
    }

    static void sendConfigUpdate(GleapMainActivity activity) {
        try {
            activity.sendMessage(GleapWidgetMessages.message("config-update", GleapWidgetMessages.configUpdate()));
        } catch (Exception err) {
        }
    }

    private static void sendPrefillData(GleapMainActivity activity) {
        try {
            JSONObject data = PrefillHelper.getInstancen().getPreFillData();
            if (data != null) {
                activity.sendMessage(GleapWidgetMessages.message("prefill-form-data", data));
            }
        } catch (Exception err) {
            GleapLog.w("Could not send the prefill data", err);
        }
    }

    static void sendSessionUpdate(GleapMainActivity activity) {
        try {
            activity.sendMessage(GleapWidgetMessages.message("session-update", GleapWidgetMessages.sessionUpdate()));
        } catch (Exception exception) {
        }
    }

    private static void sendScreenshotUpdate(GleapMainActivity activity) {
        try {
            JSONObject message = new JSONObject();
            String image = ScreenshotUtil.bitmapToBase64(GleapBug.getInstance().getScreenshot());
            byte[] decodedString = Base64.decode(image, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            GleapBug.getInstance().setScreenshot(decodedByte);

            message.put("name", "screenshot-update");
            message.put("data", "data:image/png;base64," + image);
            activity.sendMessage(message.toString());
        } catch (Exception err) {
        }
    }
}
