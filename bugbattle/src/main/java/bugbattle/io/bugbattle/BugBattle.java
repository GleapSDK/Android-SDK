package bugbattle.io.bugbattle;

import android.app.Activity;
import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

import bugbattle.io.bugbattle.controller.BugBattleActivationMethod;
import bugbattle.io.bugbattle.controller.BugBattleHttpsException;
import bugbattle.io.bugbattle.controller.BugBattleNotInitialisedException;
import bugbattle.io.bugbattle.controller.StepsToReproduce;
import bugbattle.io.bugbattle.model.FeedbackModel;
import bugbattle.io.bugbattle.model.PhoneMeta;
import bugbattle.io.bugbattle.service.BBDetector;
import bugbattle.io.bugbattle.service.ScreenshotTaker;
import bugbattle.io.bugbattle.service.ShakeGestureDetector;

public class BugBattle {
    private static BugBattle instance;
    private static ScreenshotTaker screenshotTaker;

    private BugBattle(String sdkKey, BugBattleActivationMethod activationMethod, Activity application) {
        FeedbackModel.getInstance().setSdkKey(sdkKey);
        FeedbackModel.getInstance().setPhoneMeta(new PhoneMeta(application));
        screenshotTaker = new ScreenshotTaker();

        try {
            Runtime.getRuntime().exec("logcat - c");
        } catch (Exception e) {
            System.out.println(e);
        }

        if (activationMethod == BugBattleActivationMethod.SHAKE) {
            BBDetector detector = new ShakeGestureDetector(application);
            FeedbackModel.getInstance().setGestureDetector(detector);
            detector.initialize();
        }
        if (activationMethod == BugBattleActivationMethod.THREE_FINGER_DOUBLE_TAB) {
            //  Interceptor.infiltrate();
            /*
            TouchGestureDetector touchGestureDetector = new TouchGestureDetector(application);
            FeedbackModel.getInstance().setGestureDetector(touchGestureDetector);
            touchGestureDetector.initialize();*/
        }
    }

    /**
     * Initialises the Bugbattle SDK.
     *
     * @param application      The application (this)
     * @param sdkKey           The SDK key, which can be found on dashboard.bugbattle.io
     * @param activationMethod Activation method, which triggers a new bug report.
     */
    public static BugBattle initialise(String sdkKey, BugBattleActivationMethod activationMethod, Activity application) {
        if (instance == null) {
            instance = new BugBattle(sdkKey, activationMethod, application);
        }
        return instance;
    }

    public static void setCloseCallback(CloseCallback closeCallback) {
        FeedbackModel.getInstance().setCloseCallback(closeCallback);
    }

    /**
     * Manually start the bug reporting workflow. This is used, when you use the activation method "NONE".
     *
     * @throws BugBattleNotInitialisedException thrown when BugBattle is not initialised
     */
    public static void startBugReporting() throws BugBattleNotInitialisedException {
        if (instance != null) {
            try {
                screenshotTaker.takeScreenshot();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            throw new BugBattleNotInitialisedException("BugBattle is not initialised");
        }
    }

    /**
     * Starts the bug reporting with a custom screenshot attached.
     *
     * @param bitmap the image will be used instead of the current
     */
    public static void startBugReporting(Bitmap bitmap) {
        FeedbackModel.getInstance().setScreenshot(bitmap);
        screenshotTaker.openScreenshot(bitmap);
    }

    /**
     * Track a step to add more information to the bug report
     *
     * @param type Type of the step. (for eg. Button)
     * @param data Custom data associated with the step.
     * @throws JSONException unable to create JSONObject
     */
    public static void trackStep(String type, String data) {
        StepsToReproduce.getInstance().setStep(type, data);
    }

    /**
     * Attach cusom data, which can be view in the BugBattle dashboard.
     *
     * @param customData The data to attach to a bug report
     */
    public static void attachCustomData(JSONObject customData) {
        FeedbackModel.getInstance().setCustomData(customData);
    }

    /**
     * Set/Prefill the email address for the user.
     *
     * @param email address, which is fileld in.
     */
    public static void setCustomerEmail(String email) {
        FeedbackModel.getInstance().setEmail(email);
    }

    /**
     * Enables the privacy policy check.
     *
     * @param enable Enable the privacy policy.
     */
    public static void enablePrivacyPolicy(boolean enable) {
        FeedbackModel.getInstance().enablePrivacyPolicy(enable);
    }

    /**
     * Sets a custom privacy policy url.
     *
     * @param privacyUrl The URL pointing to your privacy policy.
     */
    public static void setPrivacyPolicyUrl(String privacyUrl) {
        FeedbackModel.getInstance().setPrivacyPolicyUrl(privacyUrl);
    }

    /**
     * Sets the API url to your internal Bugbattle server. Please make sure that the server is reachable within the network.
     * Only HTTPS is allowed
     *
     * @param apiUrl url of the internal Bugbattle server
     * @throws BugBattleHttpsException Only https urls supported
     */
    public static void setApiURL(String apiUrl) throws BugBattleHttpsException {
        if (apiUrl.contains("https")) {
            FeedbackModel.getInstance().setApiUrl(apiUrl);
        } else {
            FeedbackModel.getInstance().setDisabled(true);
            throw new BugBattleHttpsException();
        }

    }
}
