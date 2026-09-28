package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The SDK under test: initialized once per test process (Gleap.initialize only runs once) against
 * {@link E2EGleapServer}, plus the helpers the e2e tests share.
 */
final class E2EEnvironment {
    static final long TIMEOUT_MS = 20000;
    // Tickets wait behind the SDK's other requests (one request thread...).
    static final long TICKET_TIMEOUT_MS = 45000;

    private static E2EGleapServer server;
    private static boolean started;

    interface Condition {
        boolean check() throws Exception;
    }

    private E2EEnvironment() {
    }

    static synchronized void start() {
        if (started) {
            return;
        }
        try {
            E2ENetworkGuard.install();
            E2ENetworkGuard.installWebViewProxy();
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");

            Application application = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getApplicationContext();
            // A session or user stored by an earlier run of this test APK.
            application.getSharedPreferences("gleap-secure", Context.MODE_PRIVATE).edit().clear().commit();

            server = new E2EGleapServer();
            server.start();

            Gleap gleap = Gleap.getInstance();
            gleap.setApiUrl(server.baseUrl());
            gleap.setWSApiUrl(server.wsUrl());
            gleap.setFrameUrl(server.frameUrl());
            gleap.setBannerUrl(server.baseUrl() + "/outbound");
            gleap.setModalUrl(server.baseUrl() + "/outbound/modal");
            gleap.setRealtimeHost("127.0.0.1:" + server.port());
            gleap.setLanguage("en");

            Gleap.initialize(E2EGleapServer.SDK_KEY, application);

            waitUntil("the remote config is applied", new Condition() {
                @Override
                public boolean check() {
                    return GleapConfig.getInstance().getPlainConfig() != null;
                }
            });
            waitUntil("the SDK is set up after the config", new Condition() {
                @Override
                public boolean check() {
                    return GleapBug.getInstance().getPhoneMeta() != null && GleapWidgetLauncher.screenshotTaker != null;
                }
            });
            waitUntil("the session is loaded", new Condition() {
                @Override
                public boolean check() {
                    return GleapWidgetLauncher.isGleapReady();
                }
            });
            waitUntil("the WebSocket is connected", new Condition() {
                @Override
                public boolean check() {
                    return server.webSocketOpens.get() > 0;
                }
            });
            started = true;
        } catch (Exception e) {
            throw new AssertionError("Could not start the e2e environment", e);
        }
    }

    /**
     * Puts back what tests change: network log rules and records, the widget closed callback, an
     * open widget.
     */
    static void resetBetweenTests() {
        Gleap gleap = Gleap.getInstance();
        gleap.setNetworkLogPropsToIgnore(null);
        gleap.setNetworkLogsBlacklist(null);
        GleapBug.getInstance().getNetworkBuffer().clear();
        gleap.setWidgetClosedCallback(null);
        if (gleap.isOpened() || widgetAlive() != null) {
            forceCloseWidget();
        }
    }

    /**
     * Cleanup: closes the widget through the SDK, or finishes its activity when that does not work.
     */
    static void forceCloseWidget() {
        Gleap.getInstance().close();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline && widgetAlive() != null) {
            SystemClock.sleep(100);
        }
        final GleapMainActivity widget = widgetAlive();
        if (widget != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    widget.finish();
                }
            });
        }
        waitUntil("the widget activity is gone", new Condition() {
            @Override
            public boolean check() {
                return widgetAlive() == null;
            }
        });
        if (Gleap.getInstance().isOpened()) {
            // Nothing is shown any more: let the SDK open the widget again.
            GleapDetectorUtil.resumeAllDetectors();
        }
    }

    // --- waiting ---------------------------------------------------------------------------------

    static void waitUntil(String what, Condition condition) {
        waitUntil(what, TIMEOUT_MS, condition);
    }

    static void waitUntil(String what, long timeoutMs, Condition condition) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        Exception last = null;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (condition.check()) {
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out after " + timeoutMs + " ms waiting until " + what, last);
    }

    static <T> T waitFor(String what, long timeoutMs, final Callable<T> probe) {
        final Object[] result = {null};
        waitUntil(what, timeoutMs, new Condition() {
            @Override
            public boolean check() throws Exception {
                result[0] = probe.call();
                return result[0] != null;
            }
        });
        @SuppressWarnings("unchecked")
        T value = (T) result[0];
        return value;
    }

    // --- tickets ---------------------------------------------------------------------------------

    static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    /**
     * The ticket (POST /bugs/v2) with this description, once the fake server received it.
     */
    static JSONObject awaitTicket(final String description) {
        return waitFor("the ticket \"" + description + "\" reaches the fake server", TICKET_TIMEOUT_MS,
                new Callable<JSONObject>() {
                    @Override
                    public JSONObject call() {
                        return server.ticketWithDescription(description);
                    }
                });
    }

    /**
     * Sends a silent crash report (screenshot and replay excluded, the default) and returns the
     * ticket the fake server received.
     */
    static JSONObject sendSilentReport(String description) {
        Gleap.getInstance().sendSilentCrashReport(description, Gleap.SEVERITY.LOW);
        return awaitTicket(description);
    }

    static List<JSONObject> objects(JSONArray array) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                list.add(object);
            }
        }
        return list;
    }

    // --- activities and the widget ---------------------------------------------------------------

    static Activity resumedActivity() {
        final Activity[] result = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED);
                result[0] = resumed.isEmpty() ? null : resumed.iterator().next();
            }
        });
        return result[0];
    }

    static void awaitResumed(final Class<? extends Activity> screen) {
        waitUntil(screen.getSimpleName() + " is resumed", new Condition() {
            @Override
            public boolean check() {
                Activity activity = resumedActivity();
                return activity != null && activity.getClass() == screen;
            }
        });
    }

    /**
     * A GleapMainActivity that is not destroyed yet, or null.
     */
    static GleapMainActivity widgetAlive() {
        final GleapMainActivity[] result = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                for (Stage stage : Stage.values()) {
                    if (stage == Stage.DESTROYED) {
                        continue;
                    }
                    for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) {
                        if (activity instanceof GleapMainActivity) {
                            result[0] = (GleapMainActivity) activity;
                        }
                    }
                }
            }
        });
        return result[0];
    }

    static GleapMainActivity resumedWidget() {
        Activity activity = resumedActivity();
        return activity instanceof GleapMainActivity ? (GleapMainActivity) activity : null;
    }

    /**
     * Opens the widget over the current activity and waits for the handshake: the page pinged the
     * SDK and got the config and the session.
     */
    static GleapMainActivity openWidget() {
        Gleap.getInstance().open();
        GleapMainActivity widget = waitFor("the widget activity is resumed", TIMEOUT_MS, new Callable<GleapMainActivity>() {
            @Override
            public GleapMainActivity call() {
                return resumedWidget();
            }
        });
        awaitWidgetLoaded(widget);
        return widget;
    }

    static void awaitWidgetLoaded(final GleapMainActivity widget) {
        waitUntil("the widget page did the ping handshake and got config and session", new Condition() {
            @Override
            public boolean check() {
                return "true".equals(evalJs(widget, "typeof messageNames === 'function'"
                        + " && messageNames().indexOf('config-update') >= 0"
                        + " && messageNames().indexOf('session-update') >= 0"));
            }
        });
    }

    static void closeWidget() {
        Gleap.getInstance().close();
        waitUntil("the widget is closed", new Condition() {
            @Override
            public boolean check() {
                return !Gleap.getInstance().isOpened() && widgetAlive() == null;
            }
        });
    }

    /**
     * Runs script in the widget page and returns its result as JSON text ("null" when it threw).
     */
    static String evalJs(GleapMainActivity widget, final String script) {
        final WebView[] webView = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                webView[0] = widget.findViewById(gleap.io.gleap.R.id.gleap_webview);
            }
        });
        if (webView[0] == null) {
            throw new AssertionError("The widget has no WebView");
        }
        final CountDownLatch done = new CountDownLatch(1);
        final String[] result = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                webView[0].evaluateJavascript(script, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        result[0] = value;
                        done.countDown();
                    }
                });
            }
        });
        try {
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("The widget page did not answer: " + script);
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        return result[0];
    }

    /**
     * Makes the widget page send a bug report with this description, as the messenger does when
     * its form is submitted ("send-feedback" through the SDK bridge).
     */
    static void sendBugReportFromWidget(GleapMainActivity widget, String description) {
        try {
            JSONObject data = new JSONObject()
                    .put("action", new JSONObject().put("feedbackType", "BUG"))
                    .put("formData", new JSONObject().put("description", description));
            JSONObject command = new JSONObject().put("name", "send-feedback").put("data", data);
            evalJs(widget, "gleapBridge(" + command + "); true");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static void awaitWidgetMessage(final GleapMainActivity widget, final String name) {
        waitUntil("the widget page receives \"" + name + "\"", TICKET_TIMEOUT_MS, new Condition() {
            @Override
            public boolean check() throws Exception {
                return evalJs(widget, "messageNames()").contains(JSONObject.quote(name));
            }
        });
    }

    // --- device ----------------------------------------------------------------------------------

    /**
     * Runs a shell command on the device (as the shell user) and returns its output.
     */
    static String shell(String command) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Shell command failed: " + command, e);
        }
    }
}
