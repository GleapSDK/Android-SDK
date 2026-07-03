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
    @Override
    public void onCreate() {
        super.onCreate();
        // Gleap.getInstance().setLanguage("es");

        Gleap.getInstance().setErrorCallback(new ErrorCallback() {
            @Override
            public void onError(Throwable error, String context) {
                System.out.println(context);
            }
        });

        Gleap.initialize("ogWhNhuiZcGWrva5nlDS8l7a78OfaLlV", this);
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
