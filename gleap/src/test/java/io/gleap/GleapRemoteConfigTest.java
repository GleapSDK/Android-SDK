package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The feature gates and network log rules the SDK takes from the remote config.
 */
public class GleapRemoteConfigTest {
    private final List<Runnable> postedToMainThread = new ArrayList<>();

    @Before
    public void setUp() {
        GleapConfig.resetForTesting();
        GleapMainThread.setTestExecutor(new Executor() {
            @Override
            public void execute(Runnable command) {
                postedToMainThread.add(command);
            }
        });
    }

    @After
    public void tearDown() {
        GleapMainThread.setTestExecutor(null);
        GleapConfig.resetForTesting();
        Gleap.getInstance().setNetworkLogsBlacklist(null);
        Gleap.getInstance().setNetworkLogPropsToIgnore(null);
    }

    private static GleapConfig apply(JSONObject flowConfig) throws Exception {
        GleapConfig config = GleapConfig.getInstance();
        config.initConfig(new JSONObject().put("flowConfig", flowConfig));
        return config;
    }

    @Test
    public void defaultsWithoutAFlowConfig() {
        GleapConfig config = GleapConfig.getInstance();
        JSONObject remote = new JSONObject();
        config.initConfig(remote);

        assertSame(remote, config.getPlainConfig());
        assertTrue(config.isEnableConsoleLogs());
        assertFalse(config.isEnableReplays());
        assertFalse(config.isActivationMethodShake());
        assertFalse(config.isActivationMethodScreenshotGesture());
        assertFalse(config.isActivationMethodFeedbackButton());
        assertEquals(0, config.getBlackList().length());
        assertEquals(0, config.getNetworkLogPropsToIgnore().length());
    }

    @Test
    public void consoleLogsCanBeTurnedOffRemotely() throws Exception {
        assertFalse(apply(new JSONObject().put("enableConsoleLogs", false)).isEnableConsoleLogs());
        assertTrue(apply(new JSONObject().put("enableConsoleLogs", true)).isEnableConsoleLogs());
    }

    @Test
    public void activationMethodsAndReplaysFollowTheRemoteConfig() throws Exception {
        GleapConfig config = apply(new JSONObject()
                .put("activationMethodShake", true)
                .put("activationMethodScreenshotGesture", true)
                .put("activationMethodFeedbackButton", true)
                .put("enableReplays", true)
                .put("replaysInterval", 3));

        assertTrue(config.isActivationMethodShake());
        assertTrue(config.isActivationMethodScreenshotGesture());
        assertTrue(config.isActivationMethodFeedbackButton());
        assertTrue(config.isEnableReplays());
        assertEquals(3, config.getInterval());
        assertEquals(3000, GleapBug.getInstance().getReplay().getInterval());
    }

    @Test
    public void networkLogRulesFeedTheSanitizerTogetherWithTheLocalOnes() throws Exception {
        apply(new JSONObject()
                .put("networkLogBlacklist", new JSONArray().put("internal.example.com"))
                .put("networkLogPropsToIgnore", new JSONArray().put("password")));
        Gleap.getInstance().setNetworkLogsBlacklist(new String[]{"tracking.example.com"});

        assertTrue(GleapNetworkLogSanitizer.isBlacklistedByConfig("https://internal.example.com/users"));
        assertTrue(GleapNetworkLogSanitizer.isBlacklistedByConfig("https://tracking.example.com/pixel"));
        assertFalse(GleapNetworkLogSanitizer.isBlacklistedByConfig("https://api.example.com/users"));
        assertEquals("password", GleapConfig.getInstance().getNetworkLogPropsToIgnore().getString(0));
    }

    @Test
    public void aHiddenButtonPositionHidesTheButtonUnlessTheAppChoseItself() throws Exception {
        GleapConfig config = apply(new JSONObject().put("feedbackButtonPosition", "HIDDEN"));
        assertEquals(WidgetPosition.HIDDEN, config.getWidgetPosition());
        assertTrue(config.isHideFeedbackButton());

        GleapConfig.resetForTesting();
        GleapConfig.getInstance().setFeedbackButtonManuallySet(true);
        config = apply(new JSONObject().put("feedbackButtonPosition", "HIDDEN"));
        assertEquals(WidgetPosition.HIDDEN, config.getWidgetPosition());
        assertFalse(config.isHideFeedbackButton());
    }

    @Test
    public void classicButtonPositionsSwitchToTheClassicButton() throws Exception {
        GleapConfig config = apply(new JSONObject().put("feedbackButtonPosition", "BUTTON_CLASSIC_LEFT"));
        assertEquals(WidgetPosition.CLASSIC_LEFT, config.getWidgetPosition());
        assertEquals(WidgetPositionType.CLASSIC, config.getWidgetPositionType());
    }

    @Test
    public void valuesMissingFromALaterConfigKeepTheirValue() throws Exception {
        apply(new JSONObject().put("enableConsoleLogs", false).put("buttonX", 42));
        GleapConfig config = apply(new JSONObject().put("buttonY", 7));

        assertFalse(config.isEnableConsoleLogs());
        assertEquals(42, config.getButtonX());
        assertEquals(7, config.getButtonY());
    }

    @Test
    public void aValueOfTheWrongTypeStopsTheParseThere() throws Exception {
        GleapConfig config = apply(new JSONObject()
                .put("enableConsoleLogs", false)
                .put("activationMethodShake", "sometimes")
                .put("buttonX", 99));

        assertFalse(config.isEnableConsoleLogs());
        assertFalse(config.isActivationMethodShake());
        assertEquals(20, config.getButtonX());
    }

    @Test
    public void aLoadedConfigRetriesThePendingPushAction() throws Exception {
        apply(new JSONObject());
        assertNotNull(GleapConfig.getInstance().getPlainConfig());
        assertEquals(1, postedToMainThread.size());
    }
}
