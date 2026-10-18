package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bug report screenshots and app replay frames (both from ScreenshotUtil) paint the views masked
 * with {@link Gleap#maskView} and password fields black, as the captures of capture requests do.
 */
@RunWith(AndroidJUnit4.class)
public class ScreenshotMaskingE2ETest extends E2ETestBase {
    private ActivityScenario<E2EHostActivity> host;
    private View masked;
    private View password;
    private View plain;

    @Before
    public void launchHost() {
        host = ActivityScenario.launch(E2EHostActivity.class);
        E2EEnvironment.awaitResumed(E2EHostActivity.class);
        host.onActivity(new ActivityScenario.ActivityAction<E2EHostActivity>() {
            @Override
            public void perform(E2EHostActivity activity) {
                FrameLayout content = new FrameLayout(activity);
                content.setBackgroundColor(Color.WHITE);
                masked = block(activity, 0, Color.RED);
                EditText field = new EditText(activity);
                field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                field.setBackgroundColor(Color.RED);
                field.setText("secret");
                field.setLayoutParams(params(400));
                password = field;
                plain = block(activity, 800, Color.RED);
                content.addView(masked);
                content.addView(password);
                content.addView(plain);
                activity.setContentView(content);
                Gleap.getInstance().maskView(masked);
            }
        });
        E2EEnvironment.waitUntil("the views are laid out", new E2EEnvironment.Condition() {
            @Override
            public boolean check() {
                return plain != null && plain.isLaidOut() && plain.getWidth() > 0;
            }
        });
    }

    @After
    public void closeHost() {
        Gleap.getInstance().unmaskView(masked);
        host.close();
    }

    @Test
    public void bugReportScreenshotsMaskMaskedViewsAndPasswordFields() throws Exception {
        final AtomicReference<Bitmap> screenshot = new AtomicReference<>();
        final CountDownLatch taken = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ScreenshotUtil.takeScreenshot(new ScreenshotUtil.GetImageCallback() {
                    @Override
                    public void getImage(Bitmap bitmap) {
                        screenshot.set(bitmap);
                        taken.countDown();
                    }
                });
            }
        });
        assertTrue("The screenshot was taken", taken.await(10, TimeUnit.SECONDS));
        Bitmap bitmap = screenshot.get();
        assertNotNull("A screenshot is delivered", bitmap);

        assertEquals("A view masked with Gleap.maskView is black", Color.BLACK, centerPixel(bitmap, masked));
        assertEquals("A password field is black", Color.BLACK, centerPixel(bitmap, password));
        assertEquals("An unmasked view stays visible", Color.RED, centerPixel(bitmap, plain));
    }

    private static View block(E2EHostActivity activity, int top, int color) {
        View view = new View(activity);
        view.setBackgroundColor(color);
        view.setLayoutParams(params(top));
        return view;
    }

    private static FrameLayout.LayoutParams params(int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(400, 200);
        params.leftMargin = 100;
        params.topMargin = top + 200;
        return params;
    }

    // The screenshot is scaled down from the window: the view's center at that scale, opaque.
    private static int centerPixel(Bitmap bitmap, View view) {
        View root = view.getRootView();
        int[] location = new int[2];
        view.getLocationInWindow(location);
        float scaleX = bitmap.getWidth() / (float) root.getWidth();
        float scaleY = bitmap.getHeight() / (float) root.getHeight();
        int x = Math.round((location[0] + view.getWidth() / 2f) * scaleX);
        int y = Math.round((location[1] + view.getHeight() / 2f) * scaleY);
        return bitmap.getPixel(x, y) | 0xFF000000;
    }
}
