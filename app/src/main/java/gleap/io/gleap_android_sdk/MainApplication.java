package gleap.io.gleap_android_sdk;

import android.app.Application;

import org.json.JSONException;
import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.callbacks.AiToolExecutedCallback;
import io.gleap.callbacks.GleapAgentToolHandler;
import io.gleap.callbacks.GleapAgentToolResultCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.ErrorCallback;
import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.OutboundSentCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;

public class MainApplication extends Application {
    private static String systemProperty(String key) {
        try {
            Process process = new ProcessBuilder("getprop", key).start();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String value = reader.readLine();
            reader.close();
            return value != null ? value.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Gleap.getInstance().setLanguage("es");

        Gleap.getInstance().setErrorCallback(new ErrorCallback() {
            @Override
            public void onError(Throwable error, String context) {
                System.out.println(context);
                if (error != null) {
                    error.printStackTrace();
                }
            }
        });

        String sdkKey = "ogWhNhuiZcGWrva5nlDS8l7a78OfaLlV";
        // Debug builds only: every request goes to a local stub server instead of Gleap, e.g.
        // adb shell setprop debug.gleap.stub http://10.0.2.2:8787 (then restart the app).
        String stub = BuildConfig.DEBUG ? systemProperty("debug.gleap.stub") : "";
        if (stub.startsWith("http://") || stub.startsWith("https://")) {
            Gleap.getInstance().setApiUrl(stub);
            Gleap.getInstance().setWSApiUrl(stub.replaceFirst("^http", "ws") + "/ws");
            Gleap.getInstance().setFrameUrl(stub + "/widget/appnew");
            Gleap.getInstance().setBannerUrl(stub + "/outbound");
            Gleap.getInstance().setModalUrl(stub + "/outbound/modal");
            Gleap.getInstance().setRealtimeHost(android.net.Uri.parse(stub).getAuthority());
            sdkKey = "stub-sdk-key";
        }

        Gleap.initialize(sdkKey, this);

        // The widget callbacks: a capture closes and reopens the widget, which the app does not
        // hear about (one widget session).
        Gleap.getInstance().setWidgetOpenedCallback(() -> android.util.Log.i("GleapDemo", "widget opened"));
        Gleap.getInstance().setWidgetClosedCallback(() -> android.util.Log.i("GleapDemo", "widget closed"));
        Gleap.getInstance().setNotificationUnreadCountUpdatedCallback(count ->
                android.util.Log.i("GleapDemo", "unread count " + count));

        // What a wrapper SDK does: hand over its buffered logs before a capture request's logs
        // are collected, then call done.
        Gleap.getInstance().setLogFlushHandler(done -> {
            try {
                Gleap.getInstance().attachConsoleLogs(new org.json.JSONArray().put(new JSONObject()
                        .put("date", "2026-09-30T00:00:00.000Z")
                        .put("priority", "INFO")
                        .put("log", "Flushed by the app's log flush handler")));
            } catch (JSONException ignore) {
            }
            done.run();
        });
        Gleap.getInstance().setTags(new String[] {
                "Android",
                "Tags",
                "#Beste"
        });

        Gleap.getInstance().setFeedbackSentCallback(new FeedbackSentCallback() {
            @Override
            public void invoke(JSONObject jsonObject) {
                System.out.println(jsonObject);
            }
        });

        // Executes the "send-money" Frontend tool defined on the AI agent in the Gleap dashboard.
        Gleap.getInstance().registerAgentTool("send-money", new GleapAgentToolHandler() {
            @Override
            public void execute(JSONObject params, GleapAgentToolResultCallback callback) {
                System.out.println("send-money called with params: " + params.toString());
                callback.onResult("The transfer got initiated but not completed yet. The user must confirm the transfer in the banking app.");
            }
        });

        Gleap.getInstance().setTicketAttribute("test1", "This is a test");
        Gleap.getInstance().setTicketAttribute("test2", 20);

        Gleap.getInstance().unsetTicketAttribute("test1");

        Gleap.getInstance().clearTicketAttributes();

        Gleap.getInstance().setAiToolExecutedCallback(new AiToolExecutedCallback() {
            @Override
            public void aiToolExecuted(JSONObject jsonObject) {
                try {
                    String toolName = jsonObject.getString("name");
                    JSONObject params = jsonObject.getJSONObject("params");

                    System.out.println(jsonObject.toString());
                    // {"name":"send-money","params":{"amount":"20","contact":"alice"}}
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Gleap.getInstance().registerCustomAction(new CustomActionCallback() {
            @Override
            public void invoke(String message, String shareToken) {
                System.out.println(message + " " + shareToken);
            }
        });

        Gleap.getInstance().setRegisterPushMessageGroupCallback(new RegisterPushMessageGroupCallback() {
            @Override
            public void invoke(String pushMessageGroup) {
                System.err.println("Subscribe: "+pushMessageGroup);
            }
        });

        Gleap.getInstance().setRegisterPushMessageGroupCallback(new RegisterPushMessageGroupCallback() {
            @Override
            public void invoke(String pushMessageGroup) {
                System.out.println(pushMessageGroup);
            }
        });


        Gleap.getInstance().setOutboundSentCallback(new OutboundSentCallback() {
            @Override
            public void invoke(JSONObject jsonObject) {
                try {
                    System.out.println("Outbound" + jsonObject.toString());
                } catch (Exception exp) {
                    System.out.println("OUTBOUND NULL!");
                }
            }
        });

        Gleap.getInstance().setFeedbackSentCallback(new FeedbackSentCallback() {
            @Override
            public void invoke(JSONObject jsonObject) {
                try {
                    System.out.println("Feedback" + jsonObject.toString());
                } catch (Exception exp) {
                    System.out.println("FEEDBACK NULL!");
                }
            }
        });
    }
}
