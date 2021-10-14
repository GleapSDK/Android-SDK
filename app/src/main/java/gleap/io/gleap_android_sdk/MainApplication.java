package gleap.io.gleap_android_sdk;

import android.app.Application;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.gleap.ConfigLoadedCallback;
import io.gleap.CustomActionCallback;
import io.gleap.FeedbackSentCallback;
import io.gleap.FeedbackWillBeSentCallback;
import io.gleap.Gleap;
import io.gleap.GleapNotInitialisedException;
import io.gleap.GleapUser;
import io.gleap.GleapUserProperties;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Gleap.initialize("ogWhNhuiZcGWrva5nlDS8l7a78OfaLlV",  this);
        GleapUserProperties userProperties = new GleapUserProperties("12");
        GleapUser gleapUserWithId = new GleapUser("12");
        GleapUser gleapUserWithIdAndProps = new GleapUser("12", userProperties);
        try {
            Gleap.getInstance().startFeedbackFlow();
        } catch (GleapNotInitialisedException e) {
            e.printStackTrace();
        }
        Gleap.getInstance().setFeedbackSentCallback(new FeedbackSentCallback() {
            @Override
            public void close() {
                // Feedback got sent
            }
        });

        Gleap.getInstance().setConfigLoadedCallback(new ConfigLoadedCallback() {
            @Override
            public void configLoaded(JSONObject jsonObject) {
                // Loaded Gleap config
            }
        });

        Gleap.getInstance().setFeedbackWillBeSentCallback(new FeedbackWillBeSentCallback() {
            @Override
            public void flowInvoced() {
                // The feedback will be sent
            }
        });

    }
}
