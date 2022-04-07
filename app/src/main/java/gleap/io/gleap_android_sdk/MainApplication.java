package gleap.io.gleap_android_sdk;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;

import io.gleap.ConfigLoadedCallback;
import io.gleap.CustomActionCallback;
import io.gleap.FeedbackSentCallback;
import io.gleap.FeedbackSentWithDataCallback;
import io.gleap.FeedbackWillBeSentCallback;
import io.gleap.GetBitmapCallback;
import io.gleap.Gleap;
import io.gleap.GleapActivationMethod;
import io.gleap.GleapNotInitialisedException;
import io.gleap.GleapUser;
import io.gleap.GleapUserProperties;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Gleap.initialize("sY2CirCOveIVc8fwPRymTL4oz2flS9p5",  this);
        Gleap.getInstance().setConfigLoadedCallback(new ConfigLoadedCallback() {
            @Override
            public void configLoaded(JSONObject jsonObject) {
                System.out.println(jsonObject);
            }
        });

        GleapActivationMethod[] action = new GleapActivationMethod[2];
        action[0] = GleapActivationMethod.SHAKE;
        Gleap.getInstance().setActivationMethods(action);

        Gleap.getInstance().setFeedbackSentWithDataCallback(new FeedbackSentWithDataCallback() {
            @Override
            public void close(JSONObject jsonObject) {
                System.out.println(jsonObject.toString());
            }
        });

        GleapUserProperties userProperties = new GleapUserProperties("12", "niklas@gmail.com");
        GleapUser gleapUserWithId = new GleapUser("12");
        GleapUser gleapUserWithIdAndProps = new GleapUser("12", userProperties);
        File file = new File("/data/data/gleap.io.gleap_android_sdk/cache/file1057025163966562657.png");
        Gleap.getInstance().addAttachment(file);


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

        new HttpCall().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }
}
