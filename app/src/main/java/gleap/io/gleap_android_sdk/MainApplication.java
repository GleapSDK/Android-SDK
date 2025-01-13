package gleap.io.gleap_android_sdk;

import android.app.Application;

import org.json.JSONException;
import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.GleapAiTool;
import io.gleap.GleapAiToolParameter;
import io.gleap.callbacks.AiToolExecutedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.OutboundSentCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Gleap.getInstance().setLanguage("de-at");
        Gleap.initialize("BTmQRJBPvKrOErNQCmyPYPAzSsw3gPmw", this);
        Gleap.getInstance().setTags(new String[] {
                "Android",
                "Tags",
                "#Beste"
        });

        // Creating parameters
        GleapAiToolParameter amountParameter = new GleapAiToolParameter(
                "amount",
                "The amount of money to send. Must be positive and provided by the user.",
                "string",
                true
        );

        String[] possibleEnumValues = {"Alice", "Bob"};

        GleapAiToolParameter contactParameter = new GleapAiToolParameter(
                "contact",
                "The contact to send money to.",
                "string",
                true,
                possibleEnumValues
        );

        GleapAiToolParameter[] params = {amountParameter, contactParameter};

        // Creating the AI tool with the parameters
        GleapAiTool transactionTool = new GleapAiTool(
                "send-money",
                "Send money to a given contact.",
                "The transfer got initiated but not completed yet. The user must confirm the transfer in the banking app.",
                "button",
                params
        );

        GleapAiTool[] tools = {transactionTool};

        // Set the available tools using the static method
        Gleap.getInstance().setAiTools(tools);

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
