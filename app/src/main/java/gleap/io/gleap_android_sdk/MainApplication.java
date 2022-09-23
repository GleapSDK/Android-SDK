package gleap.io.gleap_android_sdk;

import android.app.Application;

import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.callbacks.ConfigLoadedCallback;
import io.gleap.callbacks.CustomActionCallback;
import io.gleap.callbacks.FeedbackFlowStartedCallback;
import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackSentCallback;
import io.gleap.callbacks.WidgetClosedCallback;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Gleap.initialize("DUPaIr7s689BBblcFI4pc5aBgYJTm7Sc", this);

        Gleap.getInstance().setLanguage("AR_EG");

       /*   GleapUserProperties userProperties = new GleapUserProperties("Test User", "niklas@gmail.com" );
        //userProperties.setHash();
        GleapUser gleapUserWithId = new GleapUser("12");
        GleapUser gleapUserWithIdAndProps = new GleapUser("12", userProperties);

        File file = new File("/data/user/0/gleap.io.gleap_android_sdk/cache/file5101004034427200754.png");
        Gleap.getInstance().addAttachment(file);


      //  Gleap.getInstance().identifyUser("1234", userProperties, "f60d2a8960f5e2711159d72b67695014a05aa576023d77056bb27e7d7a96b4a6");
*/


        Gleap.getInstance().setWidgetClosedCallback(new WidgetClosedCallback() {
            @Override
            public void invoke() {
                Gleap.getInstance().logEvent(WidgetClosedCallback.class.getName());
            }
        });

        Gleap.getInstance().setConfigLoadedCallback(new ConfigLoadedCallback() {
            @Override
            public void configLoaded(JSONObject jsonObject) {
                Gleap.getInstance().logEvent(ConfigLoadedCallback.class.getName());
            }
        });



        Gleap.getInstance().setFeedbackSendingFailedCallback(new FeedbackSendingFailedCallback() {
            @Override
            public void invoke(String message) {
                Gleap.getInstance().logEvent(FeedbackSentCallback.class.getName());
            }
        });

        Gleap.getInstance().registerCustomAction(new CustomActionCallback() {
            @Override
            public void invoke(String message) {
                Gleap.getInstance().logEvent(CustomActionCallback.class.getName() + " " + message);
                Gleap.getInstance().close();
            }
        });

        Gleap.getInstance().setFeedbackFlowStartedCallback(new FeedbackFlowStartedCallback() {
            @Override
            public void invoke(String message) {
                Gleap.getInstance().logEvent(FeedbackFlowStartedCallback.class.getName());
            }
        });
    }
}
