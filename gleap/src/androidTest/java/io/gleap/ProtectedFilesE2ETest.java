package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.mockwebserver.RecordedRequest;

/**
 * Protected conversation files on the device: the widget asks the SDK to open one (or links to
 * it), the SDK downloads it from the fake server with the file session and starts the app that
 * views its type with a content URI of the SDK's FileProvider. No viewer is actually started: the
 * intents are caught.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 26)
public class ProtectedFilesE2ETest extends E2ETestBase {
    private static final String FILE_ID = "0123456789abcdef01234567";

    private ActivityScenario<E2EHostActivity> host;
    private ViewerMonitor viewers;

    /**
     * Catches the intents that would open a file (or a link) outside the SDK.
     */
    static final class ViewerMonitor extends Instrumentation.ActivityMonitor {
        final List<Intent> intents = new CopyOnWriteArrayList<>();

        @Override
        public Instrumentation.ActivityResult onStartActivity(Intent intent) {
            if (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_CHOOSER.equals(intent.getAction())) {
                intents.add(intent);
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
            return null;
        }
    }

    @Before
    public void setUp() {
        host = ActivityScenario.launch(E2EHostActivity.class);
        E2EEnvironment.awaitResumed(E2EHostActivity.class);
        viewers = new ViewerMonitor();
        InstrumentationRegistry.getInstrumentation().addMonitor(viewers);
        E2EEnvironment.server().fileRequests.clear();
        // A file session, as a verified identify gives it.
        GleapSessionController.getInstance().getUserSession().setFileAccess(E2EGleapServer.FILE_SESSION,
                DateUtil.dateToString(new Date(System.currentTimeMillis() + 15 * 60 * 1000)));
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().removeMonitor(viewers);
        GleapSession session = GleapSessionController.getInstance().getUserSession();
        if (session != null) {
            session.setFileAccess(null, null);
        }
        E2EEnvironment.forceCloseWidget();
        host.close();
    }

    private static String fileUrl() {
        return E2EEnvironment.server().baseUrl() + "/af/" + FILE_ID;
    }

    private Intent awaitViewer() {
        return E2EEnvironment.waitFor("the SDK starts a viewer", E2EEnvironment.TIMEOUT_MS, new Callable<Intent>() {
            @Override
            public Intent call() {
                return viewers.intents.isEmpty() ? null : viewers.intents.get(0);
            }
        });
    }

    private static String read(Uri uri) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void assertSharedReadOnly(Intent viewer) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri uri = viewer.getData();
        assertEquals(Intent.ACTION_VIEW, viewer.getAction());
        assertEquals("content", uri.getScheme());
        assertEquals(context.getPackageName() + ".gleap.files", uri.getAuthority());
        assertTrue("the viewer may read it", (viewer.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertEquals("and not write it", 0, viewer.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Test
    public void theWidgetOpensAProtectedFileInTheAppThatViewsIt() throws Exception {
        GleapMainActivity widget = E2EEnvironment.openWidget();
        assertTrue("the SDK announces it", E2EEnvironment.evalJs(widget,
                "JSON.stringify(window.__messages.filter(function (m) { return m.name === 'session-update'; })"
                        + ".pop().data.capabilities)").contains("openProtectedFile"));

        JSONObject command = new JSONObject().put("name", "open-protected-file")
                .put("data", new JSONObject().put("url", fileUrl()).put("name", "Quarterly report"));
        E2EEnvironment.evalJs(widget, "gleapBridge(" + command + "); true");

        Intent viewer = awaitViewer();
        assertSharedReadOnly(viewer);
        assertEquals("application/pdf", viewer.getType());
        assertEquals("Quarterly report.pdf", viewer.getData().getLastPathSegment());
        assertEquals(E2EGleapServer.PROTECTED_FILE_BODY, read(viewer.getData()));

        RecordedRequest download = E2EEnvironment.server().fileRequests.get(0);
        assertEquals(E2EGleapServer.FILE_SESSION, download.getHeader("X-File-Session"));
        assertNull("the files host gets no other credential", download.getHeader("Api-Token"));
        assertNull(download.getHeader("Gleap-Hash"));
        assertSame("the widget stays open", widget, E2EEnvironment.widgetAlive());
    }

    @Test
    public void theWidgetsLinksKeepAProtectedFileInTheAppAndNeverCloseForABlob() throws Exception {
        GleapMainActivity widget = E2EEnvironment.openWidget();

        E2EEnvironment.evalJs(widget, "gleapBridge({name: 'open-url', data: 'blob:" + E2EEnvironment.server().baseUrl() + "/5f6c'}); true");
        SystemClock.sleep(1500);
        assertSame("a blob: link does not close the widget", widget, E2EEnvironment.widgetAlive());
        assertTrue(Gleap.getInstance().isOpened());
        assertTrue("and opens nothing", viewers.intents.isEmpty());

        E2EEnvironment.evalJs(widget, "gleapBridge({name: 'open-url', data: '" + fileUrl() + "'}); true");
        Intent viewer = awaitViewer();
        assertSharedReadOnly(viewer);
        assertEquals("not the browser", 1, viewers.intents.size());
        assertEquals(E2EGleapServer.PROTECTED_FILE_BODY, read(viewer.getData()));
        assertSame(widget, E2EEnvironment.widgetAlive());
    }
}
