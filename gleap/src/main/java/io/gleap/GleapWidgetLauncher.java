package io.gleap;

import android.app.Activity;
import android.app.AlertDialog;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

import gleap.io.gleap.R;

/**
 * Opens the widget for the public open* methods. An opener queues the widget command (e.g.
 * "open-news") that the widget reads once it is loaded, then takes the screenshot, which starts
 * {@link GleapMainActivity}. When Gleap never finished loading (e.g. the app was launched
 * offline) it restarts the session first and retries the opener.
 * <p>
 * All work runs on the main thread and only while an activity is shown; without one the call
 * reports a NullPointerException and does nothing, as it always did.
 */
final class GleapWidgetLauncher {
    interface ActionData {
        JSONObject build() throws Exception;
    }

    // Set once the remote config is loaded (see GleapInitializer).
    static ScreenshotTaker screenshotTaker;
    private static final AtomicBoolean sessionRecoveryInProgress = new AtomicBoolean(false);

    private GleapWidgetLauncher() {
    }

    /**
     * Queues {@code command} and opens the widget. The error contexts are the ones the opener
     * has always reported.
     */
    static void openWithAction(String command, ActionData data, Runnable retry,
                               String innerContext, String middleContext, String outerContext) {
        open(command, data, retry, true, innerContext, middleContext, outerContext);
    }

    static void openWithAction(String command, ActionData data, Runnable retry) {
        open(command, data, retry, true, "run", "run", "run");
    }

    /**
     * Like {@link #openWithAction}, for the openers that never checked for the screenshot taker:
     * before the remote config has loaded they queue the command and report the failing
     * screenshot.
     */
    static void openWithActionUnchecked(String command, ActionData data, Runnable retry, String outerContext) {
        open(command, data, retry, false, "run", "run", outerContext);
    }

    private static void open(final String command, final ActionData data, final Runnable retry,
                             final boolean requireScreenshotTaker,
                             final String innerContext, final String middleContext, String outerContext) {
        try {
            GleapMainThread.postWithActivity(new Runnable() {
                @Override
                public void run() throws RuntimeException {
                    try {
                        if (!GleapDetectorUtil.isWidgetOpen() && isGleapReady()) {
                            try {
                                if (!requireScreenshotTaker || screenshotTaker != null) {
                                    GleapActionQueueHandler.getInstance()
                                            .addActionMessage(new GleapAction(command, data.build()));
                                    screenshotTaker.takeScreenshot();
                                }
                            } catch (Exception e) {
                                GleapErrors.report(e, innerContext);
                            }
                        } else if (!GleapDetectorUtil.isWidgetOpen()) {
                            recoverSessionAndRetry(retry);
                        }
                    } catch (Error | Exception e) {
                        GleapErrors.report(e, middleContext);
                    }
                }
            });
        } catch (Error | Exception e) {
            GleapErrors.report(e, outerContext);
        }
    }

    /**
     * Opens the widget on a conversation (its home screen without a share token) without taking
     * the screenshot for tickets first: after the app turned captures off.
     */
    static void openConversationWithoutScreenshot(final String shareToken) {
        try {
            GleapMainThread.postWithActivity(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (GleapDetectorUtil.isWidgetOpen() || !isGleapReady() || screenshotTaker == null) {
                            return;
                        }
                        if (shareToken != null && !shareToken.isEmpty()) {
                            GleapActionQueueHandler.getInstance().addActionMessage(new GleapAction("open-conversation",
                                    new JSONObject().put("hideBackButton", false).put("shareToken", shareToken)));
                        }
                        GleapDetectorUtil.stopAllDetectors();
                        screenshotTaker.openScreenshot(null, SurveyType.NONE);
                    } catch (Error | Exception e) {
                        GleapErrors.report(e, "openConversationWithoutScreenshot");
                    }
                }
            });
        } catch (Error | Exception e) {
            GleapErrors.report(e, "openConversationWithoutScreenshot");
        }
    }

    /**
     * Opens the widget without a command (its home screen, or a survey: the survey command is
     * queued by the caller). Only the home screen restarts the session when Gleap is not ready.
     */
    static void openWithScreenshot(final SurveyType type, final Runnable retry) {
        try {
            GleapMainThread.postWithActivity(new Runnable() {
                @Override
                public void run() throws RuntimeException {
                    try {
                        if (!GleapDetectorUtil.isWidgetOpen() && isGleapReady()) {
                            try {
                                if (screenshotTaker != null) {
                                    screenshotTaker.takeScreenshot(type);
                                }
                            } catch (Exception e) {
                                GleapErrors.report(e, "run");
                            }
                        } else if (type == SurveyType.NONE && !GleapDetectorUtil.isWidgetOpen()) {
                            recoverSessionAndRetry(retry);
                        }
                    } catch (Error | Exception e) {
                        GleapErrors.report(e, "run");
                    }
                }
            });
        } catch (Error | Exception e) {
            GleapErrors.report(e, "run");
        }
    }

    /**
     * Whether the session and the remote config have actually been loaded — after an
     * offline app launch the session start is marked as done without ever succeeding,
     * so the widget could not load and opening it would silently do nothing.
     */
    static boolean isGleapReady() {
        return GleapSessionController.getInstance() != null
                && GleapSessionController.getInstance().isSessionLoaded()
                && GleapSessionController.getInstance().getUserSession() != null
                && GleapConfig.getInstance().getPlainConfig() != null;
    }

    /**
     * Attempts to restart the Gleap session (and config load) when the widget is opened
     * explicitly but Gleap never finished loading (e.g. the app was launched offline).
     * On success the widget opens as requested; on failure the user gets the same offline
     * alert the widget shows when it fails to load mid-session.
     */
    private static void recoverSessionAndRetry(final Runnable retryOpen) {
        if (!sessionRecoveryInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            new GleapBaseSessionService(new GleapBaseSessionService.SessionLoadedCallback() {
                @Override
                public void invoke(boolean success) {
                    try {
                        if (!success) {
                            sessionRecoveryInProgress.set(false);
                            showOfflineAlert();
                            return;
                        }

                        if (GleapConfig.getInstance().getPlainConfig() != null) {
                            sessionRecoveryInProgress.set(false);
                            retryOpen.run();
                            return;
                        }

                        // The config never loaded either — fetch it before opening the widget.
                        new ConfigLoader(new OnHttpResponseListener() {
                            @Override
                            public void onTaskComplete(JSONObject response) {
                                sessionRecoveryInProgress.set(false);
                                if (GleapConfig.getInstance().getPlainConfig() != null) {
                                    GleapDetectorUtil.clearAllDetectors();
                                    GleapInitializer.onConfigLoaded();
                                    retryOpen.run();
                                } else {
                                    showOfflineAlert();
                                }
                            }
                        }).executeOnExecutor(GleapExecutor.SERIAL, GleapBug.getInstance());
                    } catch (Error | Exception exception) {
                        sessionRecoveryInProgress.set(false);
                        GleapErrors.report(exception, "recoverSessionAndRetry - callback");
                    }
                }
            }).executeOnExecutor(GleapExecutor.SERIAL);
        } catch (Error | Exception exception) {
            sessionRecoveryInProgress.set(false);
            GleapErrors.report(exception, "recoverSessionAndRetry");
        }
    }

    private static void showOfflineAlert() {
        try {
            final Activity activity = ActivityUtil.getCurrentActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AlertDialog alertDialog = new AlertDialog.Builder(activity)
                                .setPositiveButton(activity.getString(R.string.gleap_alert_no_internet_accept), null)
                                .create();
                        alertDialog.setTitle(activity.getString(R.string.gleap_alert_no_internet_title));
                        alertDialog.setMessage(activity.getString(R.string.gleap_alert_no_internet_subtitle));
                        alertDialog.show();
                    } catch (Error | Exception ignore) {
                    }
                }
            });
        } catch (Error | Exception ignore) {
        }
    }
}
