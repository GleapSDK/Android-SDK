package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * openConversations(showBackButton) shows the back button when asked to, like on iOS.
 */
public class GleapOpenConversationsTest {
    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        GleapConfig.getInstance().plainConfig = new JSONObject();
        GleapWidgetLauncher.screenshotTaker = new ScreenshotTaker() {
            @Override
            public void takeScreenshot() {
            }
        };
        GleapActionQueueHandler.getInstance().clearActionMessageQueue();
    }

    @After
    public void tearDown() {
        GleapWidgetLauncher.screenshotTaker = null;
        GleapActionQueueHandler.getInstance().clearActionMessageQueue();
        sdk.tearDown();
    }

    // The hideBackButton the widget gets.
    private boolean hideBackButton(Runnable open) throws Exception {
        GleapActionQueueHandler.getInstance().clearActionMessageQueue();
        open.run();
        sdk.runMainThreadTasks();
        List<GleapAction> queue = GleapActionQueueHandler.getInstance().getActionQueue();
        assertEquals(1, queue.size());
        assertEquals("open-conversations", queue.get(0).getCommand());
        return queue.get(0).getData().getBoolean("hideBackButton");
    }

    @Test
    public void showBackButtonShowsTheBackButton() throws Exception {
        assertFalse(hideBackButton(() -> Gleap.getInstance().openConversations(true)));
        assertTrue(hideBackButton(() -> Gleap.getInstance().openConversations(false)));
        // Without an argument it is shown, as before.
        assertFalse(hideBackButton(() -> Gleap.getInstance().openConversations()));
    }
}
