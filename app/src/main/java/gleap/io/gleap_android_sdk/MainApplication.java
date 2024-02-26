package gleap.io.gleap_android_sdk;

import android.app.Application;

import io.gleap.Gleap;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.NotificationUnreadCountUpdatedCallback;
import io.gleap.callbacks.RegisterPushMessageGroupCallback;
import io.gleap.callbacks.UnRegisterPushMessageGroupCallback;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Gleap.initialize("ogWhNhuiZcGWrva5nlDS8l7a78OfaLlV", this);
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

        Gleap.getInstance().setRegisterPushMessageGroupCallback(new RegisterPushMessageGroupCallback() {
            @Override
            public void invoke(String pushMessageGroup) {
                System.err.println("Subscribe: "+pushMessageGroup);
            }
        });

        Gleap.getInstance().setNotificationUnreadCountUpdatedCallback(new NotificationUnreadCountUpdatedCallback() {
            @Override
            public void invoke(int count) {
                System.out.println("COUNTER!!!!! " + count);
            }
        });

        Gleap.getInstance().setUnRegisterPushMessageGroupCallback(new UnRegisterPushMessageGroupCallback() {
            @Override
            public void invoke(String pushMessageGroup) {
                System.err.println("Unsubscribe: "+pushMessageGroup);
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
