package io.gleap;

import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protected conversation files (https://files.gleap.io/af/&lt;id&gt;) in the app. Their bytes can
 * only be read with the session's file session (X-File-Session), which a browser does not have,
 * so the SDK downloads the file itself into its cache and hands it to the app that views its
 * type ({@link GleapFileProvider}), or to the share sheet when none does.
 * <p>
 * Main thread only, except the download itself.
 */
final class GleapProtectedFiles {
    static final String FOLDER = "gleap-files";
    private static final Pattern FILE_PATH = Pattern.compile("^(?:/files)?/af/([a-f0-9]{24})$");
    private static final Pattern EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{1,8}$");
    private static final Pattern DISPOSITION_NAME =
            Pattern.compile("filename\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]+))", Pattern.CASE_INSENSITIVE);
    static final String OCTET_STREAM = "application/octet-stream";

    /**
     * Shows a downloaded file.
     */
    interface Viewer {
        void show(File file, String mimeType);
    }

    // A thread per download: a large file never holds back the SDK's own requests.
    private static volatile Executor executor = new Executor() {
        @Override
        public void execute(Runnable command) {
            new Thread(command, "gleap-protected-file").start();
        }
    };
    // The cache folder of the downloads, known once a widget opened: a logout deletes them.
    private static volatile File knownFolder;

    private final File folder;
    private final Viewer viewer;
    // The file to open next: waits here for a file session.
    private String pendingUrl;
    private String pendingName;
    // Bumped by every download: only the latest one is shown.
    private int download;
    private boolean downloading;

    GleapProtectedFiles(File cacheDir, Viewer viewer) {
        this.folder = new File(cacheDir, FOLDER);
        this.viewer = viewer;
        knownFolder = folder;
    }

    /**
     * The file id when {@code url} is a protected file of the configured API region: https on
     * files.gleap.io (files.us.gleap.ai for the US API) without port or user info, path
     * /af/&lt;id&gt; or /files/af/&lt;id&gt;; or the API's own origin when the API runs on
     * localhost. null for any other url: the file session is never sent anywhere else.
     */
    static String fileId(String url) {
        if (url == null) {
            return null;
        }
        try {
            URL file = new URL(url);
            URL api = new URL(GleapConfig.getInstance().getApiUrl());
            String apiHost = api.getHost().toLowerCase(Locale.ROOT);
            String host = file.getHost().toLowerCase(Locale.ROOT);
            if (file.getUserInfo() != null) {
                return null;
            }
            boolean trusted;
            if (apiHost.equals("localhost") || apiHost.equals("127.0.0.1")) {
                // Development: the local API serves the files itself.
                trusted = file.getProtocol().equalsIgnoreCase(api.getProtocol()) && host.equals(apiHost)
                        && file.getPort() == api.getPort();
            } else {
                String filesHost = apiHost.endsWith(".us.gleap.ai") ? "files.us.gleap.ai" : "files.gleap.io";
                trusted = file.getProtocol().equalsIgnoreCase("https") && file.getPort() == -1 && host.equals(filesHost);
            }
            if (!trusted) {
                return null;
            }
            Matcher match = FILE_PATH.matcher(file.getPath());
            return match.matches() ? match.group(1) : null;
        } catch (Exception invalid) {
            return null;
        }
    }

    /**
     * Downloads {@code url} with the file session and shows it. Without a valid file session the
     * file waits (the file session is renewed) and opens with the next {@link #sessionChanged()}.
     * A later call replaces a file that is still loading or waiting.
     *
     * @return false, and nothing is requested, when {@code url} is not a protected file
     */
    boolean open(String url, String name) {
        if (fileId(url) == null) {
            return false;
        }
        // The latest tap wins: a download on its way is not shown.
        download++;
        downloading = false;
        pendingUrl = url;
        pendingName = name;
        openPending();
        return true;
    }

    /**
     * The session changed: a file waiting for a file session opens now.
     */
    void sessionChanged() {
        openPending();
    }

    private void openPending() {
        final GleapSessionController controller = GleapSessionController.getInstance();
        GleapSession session = controller != null ? controller.getUserSession() : null;
        if (pendingUrl == null || downloading || controller == null) {
            return;
        }
        final String token = session != null ? session.validFileAccessToken(0) : null;
        if (token == null) {
            // Expired while the app was in the background: renewed with the app's last identify,
            // whose answer brings the next session update.
            GleapFileAccess.refreshIfExpiring();
            return;
        }
        final String url = pendingUrl;
        final String name = pendingName;
        pendingUrl = null;
        pendingName = null;
        final int current = ++download;
        downloading = true;
        final int generation = controller.currentGeneration();
        final String owner = session.getId();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Downloaded file = null;
                try {
                    file = fetch(url, name, token);
                } catch (Exception error) {
                    GleapLog.w("The file could not be downloaded", error);
                }
                final Downloaded result = file;
                GleapMainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        finish(current, result, generation, owner);
                    }
                });
            }
        });
    }

    private void finish(int current, Downloaded result, int generation, String owner) {
        if (current != download) {
            // Replaced by a later file meanwhile.
            delete(result);
            return;
        }
        downloading = false;
        // A logout or another user meanwhile: the file is not theirs.
        GleapSessionController controller = GleapSessionController.getInstance();
        GleapSession session = controller != null ? controller.getUserSession() : null;
        if (result == null || controller == null || !controller.isCurrentGeneration(generation) || session == null
                || owner == null || !owner.equals(session.getId()) || session.validFileAccessToken(0) == null) {
            delete(result);
            return;
        }
        try {
            viewer.show(result.file, result.mimeType);
        } catch (Exception error) {
            GleapLog.w("The file could not be opened", error);
        }
    }

    private Downloaded fetch(String url, String name, String token) throws IOException {
        HttpURLConnection conn = GleapHttp.open(url, GleapHttp.UPLOAD_READ_TIMEOUT_MS);
        try {
            conn.setRequestMethod("GET");
            // The file session stays with the files host: no redirect, no cache, and no other
            // credential (the files host is not the API).
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestProperty("X-File-Session", token);
            if (!GleapHttp.isSuccess(conn.getResponseCode())) {
                return null;
            }
            String mimeType = mimeType(conn.getHeaderField("Content-Type"), name);
            String fileName = fileName(name, conn.getHeaderField("Content-Disposition"), mimeType);
            // Only the latest file is kept.
            deleteRecursively(folder);
            File fileFolder = new File(folder, UUID.randomUUID().toString());
            if (!fileFolder.mkdirs()) {
                return null;
            }
            File file = new File(fileFolder, fileName);
            InputStream in = conn.getInputStream();
            OutputStream out = new FileOutputStream(file);
            try {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (IOException error) {
                deleteRecursively(fileFolder);
                throw error;
            } finally {
                out.close();
                in.close();
            }
            return new Downloaded(file, mimeType);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * The type the file is opened as: the server's, else the one of its name's extension.
     */
    static String mimeType(String contentType, String name) {
        String type = contentType != null ? contentType.split(";")[0].trim().toLowerCase(Locale.ROOT) : "";
        if (!type.isEmpty() && !type.equals(OCTET_STREAM)) {
            return type;
        }
        String clean = cleanFileName(name);
        int dot = clean.lastIndexOf('.');
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String guessed = dot >= 0 && map != null
                ? map.getMimeTypeFromExtension(clean.substring(dot + 1).toLowerCase(Locale.ROOT)) : null;
        return guessed != null ? guessed : OCTET_STREAM;
    }

    /**
     * The name the file is opened and saved under: the attachment's name, else the server's
     * (Content-Disposition); with the extension of its type when it has none.
     */
    static String fileName(String name, String contentDisposition, String mimeType) {
        String base = cleanFileName(name);
        if (base.isEmpty() && contentDisposition != null) {
            Matcher match = DISPOSITION_NAME.matcher(contentDisposition);
            if (match.find()) {
                base = cleanFileName(match.group(1) != null ? match.group(1) : match.group(2));
            }
        }
        if (base.isEmpty()) {
            base = "file";
        }
        if (EXTENSION.matcher(base).find()) {
            return base;
        }
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String extension = map != null && mimeType != null ? map.getExtensionFromMimeType(mimeType) : null;
        return extension != null && !extension.isEmpty() ? base + "." + extension : base;
    }

    /**
     * The last part of {@code name} (a link's text can be a URL), without characters a file
     * name cannot hold.
     */
    static String cleanFileName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        int separator = Math.max(trimmed.lastIndexOf('/'), Math.max(trimmed.lastIndexOf('\\'), trimmed.lastIndexOf(':')));
        String last = trimmed.substring(separator + 1).replaceAll("[\\p{Cntrl}]", "");
        if (last.equals(".") || last.equals("..")) {
            return "";
        }
        return last.length() > 120 ? last.substring(last.length() - 120) : last;
    }

    /**
     * Logout: the files the user opened go with them.
     */
    static void deleteDownloads() {
        File known = knownFolder;
        if (known != null) {
            deleteRecursively(known);
        }
    }

    private static void delete(Downloaded result) {
        if (result != null) {
            deleteRecursively(result.file.getParentFile());
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static final class Downloaded {
        final File file;
        final String mimeType;

        Downloaded(File file, String mimeType) {
            this.file = file;
            this.mimeType = mimeType;
        }
    }

    // Tests only.
    static void setExecutorForTesting(Executor testExecutor) {
        executor = testExecutor;
    }
}
