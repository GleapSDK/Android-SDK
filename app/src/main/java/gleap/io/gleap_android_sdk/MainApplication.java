package gleap.io.gleap_android_sdk;

import android.app.Application;

import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Gleap.initialize("QTJpSeGQXMQPXxKqaXB8ZSBmZ57idbgS", this);
        Gleap.getInstance().setTags(new String[] {
                "Android",
                "Tags",
                "#Beste"
        });

        Gleap.getInstance().registerCustomAction(new CustomActionCallback() {
            @Override
            public void invoke(String message) {
                System.out.println(message);
            }
        });

        String jsonString = "{\"google.delivered_priority\":\"normal\",\"google.sent_time\":1709032798200,\"google.ttl\":2419200,\"google.original_priority\":\"normal\",\"sender\":\"GLEAP\",\"google.product_id\":72175901,\"id\":\"QY59fQ7iDz2YK9uE1bJi54FaNDNekokboMpKaXh8ikOs50o3XTPBaerUiqnAXxiynijw9p\",\"from\":\"\\/topics\\/gleapuser-964b07cb33e95274d3a1b3a4b66809f4749d7d0aa51c86079d49d5ad5df76d94\",\"type\":\"conversation\",\"google.message_id\":\"0:1709032798416886%bd50d7f1bd50d7f1\",\"gcm.n.analytics_data\":\"Bundle[mParcelledData.dataSize=240]\",\"collapse_key\":\"gleap.io.gleap_android_sdk\"}";

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            // Use jsonObject as needed, for example, print it
            Gleap.getInstance().handlePushNotification(jsonObject);
        } catch (Exception e) {
            e.printStackTrace();
        }

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
