package gleap.io.gleap_android_sdk;

import android.app.Application;

import org.json.JSONException;
import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.GleapAiTool;
import io.gleap.GleapAiToolParameter;
import io.gleap.callbacks.AiToolExecutedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Gleap.initialize("Vx0SXWPHGU7Af54CabNL07k6HRELKTxu", this);
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
                params
        );

        GleapAiTool[] tools = {transactionTool};

        // Set the available tools using the static method
        Gleap.getInstance().setAiTools(tools);

        Gleap.getInstance().setTicketAttribute("test1", "This is a test");
        Gleap.getInstance().setTicketAttribute("test2", 20);

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
            public void invoke(String message) {
                System.out.println(message);
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
     //   Gleap.getInstance().setLanguage("pt");

       /*   GleapUserProperties userProperties = new GleapUserProperties("Test User", "niklas@gmail.com" );
        //userProperties.setHash();
        GleapUser gleapUserWithId = new GleapUser("12");
        GleapUser gleapUserWithIdAndProps = new GleapUser("12", userProperties);

        File file = new File("/data/user/0/gleap.io.gleap_android_sdk/cache/file5101004034427200754.png");
        Gleap.getInstance().addAttachment(file);


      //  Gleap.getInstance().identifyUser("1234", userProperties, "f60d2a8960f5e2711159d72b67695014a05aa576023d77056bb27e7d7a96b4a6");
*/
/*

        Gleap.getInstance().setWidgetClosedCallback(new WidgetClosedCallback() {
            @Override
            public void invoke() {
                Gleap.getInstance().trackEvent(WidgetClosedCallback.class.getName());
            }
        });

        Gleap.getInstance().setConfigLoadedCallback(new ConfigLoadedCallback() {
            @Override
            public void configLoaded(JSONObject jsonObject) {
                Gleap.getInstance().trackEvent(ConfigLoadedCallback.class.getName());
            }
        });
        
        Gleap.getInstance().setFeedbackSendingFailedCallback(new FeedbackSendingFailedCallback() {
            @Override
            public void invoke(String message) {
                Gleap.getInstance().trackEvent(FeedbackSentCallback.class.getName());
            }
        });

        Gleap.getInstance().registerCustomAction(new CustomActionCallback() {
            @Override
            public void invoke(String message) {
                Gleap.getInstance().trackEvent(CustomActionCallback.class.getName() + " " + message);
                Gleap.getInstance().close();
            }
        });

        Gleap.getInstance().setFeedbackFlowStartedCallback(new FeedbackFlowStartedCallback() {
            @Override
            public void invoke(String message) {
                Gleap.getInstance().trackEvent(FeedbackFlowStartedCallback.class.getName());
            }
        });*/
    }
}
