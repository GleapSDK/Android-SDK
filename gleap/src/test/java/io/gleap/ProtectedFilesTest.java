package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Protected conversation files in the app: the widget asks the SDK to open one, the SDK
 * downloads it with the file session (and nothing else) and hands it to a viewer.
 */
public class ProtectedFilesTest {
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";
    private static final String FILE_ID = "0123456789abcdef01234567";
    private static final String FILE_URL = "https://files.gleap.io/af/" + FILE_ID;
    private static final String FILE_PATH = "/af/" + FILE_ID;
    private static final String PDF = "%PDF-1.4 protected";

    @Rule
    public TemporaryFolder cache = new TemporaryFolder();

    private SdkTestEnvironment sdk;
    private GleapProtectedFiles files;
    private final List<File> shown = new ArrayList<>();
    private final List<String> shownTypes = new ArrayList<>();
    // Downloads wait here until the test runs them.
    private final List<Runnable> downloads = new ArrayList<>();
    private final String expiresAt = DateUtil.dateToString(new Date(System.currentTimeMillis() + 15 * 60 * 1000));

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
        sdk.storeSession("id-1", "hash-1");
        sdk.controller.setSessionLoaded(true);
        GleapProtectedFiles.setExecutorForTesting(downloads::add);
        files = new GleapProtectedFiles(cache.getRoot(), (file, mimeType) -> {
            shown.add(file);
            shownTypes.add(mimeType);
        });
    }

    @After
    public void tearDown() {
        GleapProtectedFiles.setExecutorForTesting(null);
        sdk.tearDown();
    }

    private void giveFileAccess() throws Exception {
        sdk.controller.processSessionActionResult(new JSONObject("{\"gleapId\":\"id-1\",\"gleapHash\":\"hash-1\","
                + "\"userId\":\"user-1\",\"authenticatedFilesRequired\":true,\"fileAccessToken\":\"" + TOKEN + "\","
                + "\"fileAccessExpiresAt\":\"" + expiresAt + "\"}"), false, false);
    }

    private void runDownloads() {
        while (!downloads.isEmpty()) {
            downloads.remove(0).run();
        }
        sdk.runMainThreadTasks();
    }

    private File downloadsFolder() {
        return new File(cache.getRoot(), GleapProtectedFiles.FOLDER);
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void onlyProtectedFilesOfTheConfiguredRegionAreRecognized() {
        GleapConfig.getInstance().setApiUrl("https://api.gleap.io");
        assertEquals(FILE_ID, GleapProtectedFiles.fileId("https://files.gleap.io/af/" + FILE_ID));
        assertEquals(FILE_ID, GleapProtectedFiles.fileId("https://files.gleap.io/files/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("http://files.gleap.io/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("https://files.gleap.io:8443/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("https://user:pass@files.gleap.io/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("https://files.gleap.io.example.com/af/" + FILE_ID));
        assertNull("a US file is not one of the EU API", GleapProtectedFiles.fileId("https://files.us.gleap.ai/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("https://files.gleap.io/af/" + FILE_ID + "/name.pdf"));
        assertNull(GleapProtectedFiles.fileId("https://files.gleap.io/af/0123456789ABCDEF01234567"));
        assertNull(GleapProtectedFiles.fileId("blob:https://messenger-app.gleap.io/5f6c"));
        assertNull(GleapProtectedFiles.fileId(null));

        GleapConfig.getInstance().setRegion(GleapRegion.US);
        assertEquals(FILE_ID, GleapProtectedFiles.fileId("https://files.us.gleap.ai/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("https://files.gleap.io/af/" + FILE_ID));

        GleapConfig.getInstance().setApiUrl("http://localhost:9000");
        assertEquals("a local API serves its own files", FILE_ID, GleapProtectedFiles.fileId("http://localhost:9000/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("http://localhost:9001/af/" + FILE_ID));
        assertNull(GleapProtectedFiles.fileId("https://files.gleap.io/af/" + FILE_ID));
    }

    @Test
    public void aFileIsDownloadedWithTheFileSessionOnlyAndShownUnderItsName() throws Exception {
        giveFileAccess();
        sdk.server.respondWithHeader(FILE_PATH, 200, PDF, "Content-Type", "application/pdf");

        assertTrue(files.open(FILE_URL, "Quarterly report.pdf"));
        runDownloads();

        FakeGleapServer.Request request = sdk.server.last(FILE_PATH);
        assertEquals(FILE_URL, request.getURL().toString());
        assertEquals("GET", request.getRequestMethod());
        assertEquals(TOKEN, request.headers.get("X-File-Session"));
        for (String header : new String[]{"Api-Token", "Gleap-Id", "Gleap-Hash", "Gleap-Grant", "Authorization"}) {
            assertNull("the files host gets no " + header, request.headers.get(header));
        }
        assertFalse("no redirect is followed", request.getInstanceFollowRedirects());
        assertEquals(1, shown.size());
        assertEquals("Quarterly report.pdf", shown.get(0).getName());
        assertEquals("application/pdf", shownTypes.get(0));
        assertEquals(PDF, read(shown.get(0)));
        assertEquals(downloadsFolder(), shown.get(0).getParentFile().getParentFile());
    }

    @Test
    public void aLinkHasNoNameSoTheServersIsUsed() throws Exception {
        giveFileAccess();
        sdk.server.respondWithHeader(FILE_PATH, 200, PDF, "Content-Disposition", "attachment; filename=\"invoice-42.pdf\"");

        assertTrue(files.open(FILE_URL, null));
        runDownloads();

        assertEquals("invoice-42.pdf", shown.get(0).getName());
        assertEquals("a type the server does not know falls back to bytes", GleapProtectedFiles.OCTET_STREAM, shownTypes.get(0));
    }

    @Test
    public void aFileNameIsItsLastPartWithoutControlCharacters() {
        assertEquals("invoice-42.PDF", GleapProtectedFiles.fileName("../../invoices/invoice-42.PDF", null, null));
        assertEquals("report.pdf", GleapProtectedFiles.fileName("https://files.gleap.io/x/report.pdf", null, null));
        assertEquals("aäb.txt", GleapProtectedFiles.fileName(" a\u0000äb.txt\n", null, null));
        assertEquals("server.zip", GleapProtectedFiles.fileName("  ", "attachment; filename=server.zip", null));
        assertEquals("file", GleapProtectedFiles.fileName("..", null, null));
        assertEquals("application/pdf", GleapProtectedFiles.mimeType("application/pdf; charset=binary", "x"));
    }

    @Test
    public void withoutAFileSessionTheFileWaitsForTheNextSession() throws Exception {
        sdk.server.respond(FILE_PATH, 200, PDF);

        assertTrue(files.open(FILE_URL, "report.pdf"));
        runDownloads();
        assertTrue("nothing is requested without a file session", sdk.server.requestsTo(FILE_PATH).isEmpty());

        giveFileAccess();
        // The widget gets the renewed session (GleapWidgetBridge.sendSessionUpdate).
        files.sessionChanged();
        runDownloads();

        assertEquals(TOKEN, sdk.server.last(FILE_PATH).headers.get("X-File-Session"));
        assertEquals(1, shown.size());
    }

    @Test
    public void otherUrlsNeverGetTheFileSession() throws Exception {
        giveFileAccess();

        assertFalse(files.open("https://files.example.com/af/" + FILE_ID, "x.pdf"));
        assertFalse(files.open("https://example.com/report.pdf", null));
        assertFalse(files.open(null, null));
        runDownloads();

        assertTrue(sdk.server.requestsTo("/af/").isEmpty());
        assertTrue(shown.isEmpty());
    }

    @Test
    public void aFailedDownloadShowsNothingAndKeepsNothing() throws Exception {
        giveFileAccess();
        sdk.server.respond(FILE_PATH, 403, "forbidden");

        assertTrue(files.open(FILE_URL, "report.pdf"));
        runDownloads();

        assertEquals(1, sdk.server.requestsTo(FILE_PATH).size());
        assertTrue(shown.isEmpty());
        String[] kept = downloadsFolder().list();
        assertTrue(kept == null || kept.length == 0);
    }

    @Test
    public void onlyTheLatestFileIsShown() throws Exception {
        giveFileAccess();
        sdk.server.respond(FILE_PATH, 200, PDF);

        assertTrue(files.open(FILE_URL, "first.pdf"));
        assertTrue(files.open(FILE_URL, "second.pdf"));
        runDownloads();

        assertEquals(1, shown.size());
        assertEquals("second.pdf", shown.get(0).getName());
        assertEquals(1, downloadsFolder().list().length);
    }

    @Test
    public void aLogoutDropsAFileOnItsWayAndTheFilesOpenedBefore() throws Exception {
        giveFileAccess();
        sdk.server.respond(FILE_PATH, 200, PDF).respond("/files/session/revoke", 204, "");
        assertTrue(files.open(FILE_URL, "first.pdf"));
        runDownloads();
        assertEquals(1, shown.size());

        assertTrue(files.open(FILE_URL, "second.pdf"));
        Gleap.getInstance().clearIdentity();
        runDownloads();

        assertEquals("the second file is not shown to the next user", 1, shown.size());
        assertFalse(shown.get(0).exists());
        String[] kept = downloadsFolder().list();
        assertTrue(kept == null || kept.length == 0);
    }

    @Test
    public void theSessionUpdateAnnouncesThatTheAppOpensProtectedFiles() throws Exception {
        JSONObject capabilities = GleapWidgetMessages.sessionUpdate().getJSONObject("capabilities");
        assertTrue(capabilities.getBoolean("openProtectedFile"));
        assertTrue(capabilities.getBoolean("adoptSession"));
    }

    @Test
    public void blobLinksAreNeverOpened() {
        assertFalse(GleapExternalLinks.mayOpen("blob:https://messenger-app.gleap.io/5f6c"));
        assertFalse(GleapExternalLinks.mayOpen(" BLOB:https://messenger-app.gleap.io/5f6c"));
    }
}
