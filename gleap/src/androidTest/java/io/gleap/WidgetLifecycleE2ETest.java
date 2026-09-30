package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.res.Configuration;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import io.gleap.callbacks.WidgetClosedCallback;

/**
 * The widget's lifecycle: GleapMainActivity with the fake widget page, rotated and recreated,
 * closed through the SDK.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetLifecycleE2ETest extends E2ETestBase {
    private ActivityScenario<E2EHostActivity> host;

    @Before
    public void launchHost() {
        host = ActivityScenario.launch(E2EHostActivity.class);
        E2EEnvironment.awaitResumed(E2EHostActivity.class);
    }

    @After
    public void closeHost() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);
        E2EEnvironment.forceCloseWidget();
        host.close();
    }

    @Test
    public void theWidgetClosesThroughTheSdkAfterRotationAndRecreation() throws Exception {
        final AtomicInteger closed = new AtomicInteger();
        Gleap.getInstance().setWidgetClosedCallback(new WidgetClosedCallback() {
            @Override
            public void invoke() {
                closed.incrementAndGet();
            }
        });
        final GleapMainActivity widget = E2EEnvironment.openWidget();
        assertTrue(Gleap.getInstance().isOpened());

        // Rotation: handled by the widget itself (configChanges), the page stays loaded.
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.setRotation(UiAutomation.ROTATION_FREEZE_90);
        E2EEnvironment.waitUntil("the widget is in landscape", new E2EEnvironment.Condition() {
            @Override
            public boolean check() {
                return widget.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            }
        });
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0);
        E2EEnvironment.waitUntil("the widget is in portrait again", new E2EEnvironment.Condition() {
            @Override
            public boolean check() {
                return widget.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
            }
        });
        assertSame("Not recreated by the rotation", widget, E2EEnvironment.widgetAlive());
        assertEquals("true", E2EEnvironment.evalJs(widget, "window.__pinged === true"));
        assertTrue(Gleap.getInstance().isOpened());

        // Recreation (font size, language, window size changed while it is open).
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                widget.recreate();
            }
        });
        GleapMainActivity recreated = E2EEnvironment.waitFor("the recreated widget is resumed", E2EEnvironment.TIMEOUT_MS,
                new Callable<GleapMainActivity>() {
                    @Override
                    public GleapMainActivity call() {
                        GleapMainActivity resumed = E2EEnvironment.resumedWidget();
                        return resumed != null && resumed != widget ? resumed : null;
                    }
                });
        assertNotSame(widget, recreated);
        E2EEnvironment.awaitWidgetLoaded(recreated);
        assertTrue("Still open after the recreation", Gleap.getInstance().isOpened());
        assertEquals("No WidgetClosed for a recreation", 0, closed.get());

        E2EEnvironment.closeWidget();
        assertEquals(1, closed.get());
    }

    /**
     * Regression: GleapMainActivity.onTaskComplete called GleapDetectorUtil.resumeAllDetectors()
     * once a ticket was created (201), which marked the widget as closed while it still showed its
     * confirmation; Gleap.close() only closes when isOpened().
     */
    @Test
    public void closeStillClosesTheWidgetAfterATicketWasSentFromIt() throws Exception {
        GleapMainActivity widget = E2EEnvironment.openWidget();
        String description = E2EEnvironment.unique("close-after-ticket");
        E2EEnvironment.sendBugReportFromWidget(widget, description);
        E2EEnvironment.awaitTicket(description);
        E2EEnvironment.awaitWidgetMessage(widget, "feedback-sent");

        // The widget shows its confirmation: it is still open, and the app can close it.
        assertSame(widget, E2EEnvironment.resumedWidget());
        boolean openedWhileShown = Gleap.getInstance().isOpened();
        Gleap.getInstance().close();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline && E2EEnvironment.widgetAlive() != null) {
            SystemClock.sleep(100);
        }
        boolean closedByTheSdk = E2EEnvironment.widgetAlive() == null;
        assertTrue("After a ticket was sent from the widget, isOpened() = " + openedWhileShown
                        + " while the widget is still shown, and Gleap.close() "
                        + (closedByTheSdk ? "closed it" : "did not close it within 5 s"),
                openedWhileShown && closedByTheSdk);
    }
}
