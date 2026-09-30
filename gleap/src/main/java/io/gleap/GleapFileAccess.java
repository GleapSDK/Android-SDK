package io.gleap;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Authenticated conversation files (opt-in per project): the project's conversation files can
 * only be read with a file session, a short-lived token (15 minutes) that a verified identify
 * (with the user hash) hands out. The SDK keeps it in memory only, forwards it to the widget
 * (session-update), refreshes it before it expires by identifying again with the last identify,
 * revokes it on logout, and opens the conversation of a protected file from an emailed link
 * ({@link Gleap#openProtectedFileFromUrl(String)}).
 */
final class GleapFileAccess {
    // The file session is refreshed this long before it expires.
    static final long REFRESH_MARGIN_MS = 5 * 60 * 1000;
    // An app coming to the foreground refreshes at most this often.
    private static final long FOREGROUND_REFRESH_INTERVAL_MS = 60 * 1000;
    private static final Pattern FILE_ID = Pattern.compile("^[a-f0-9]{24}$");

    // Runs the revoke and location requests (the SDK's serial request thread).
    private static volatile Executor executor = GleapExecutor.SERIAL;
    // Bumped to cancel the scheduled refresh: a timer only runs while it holds the current value.
    private static final AtomicInteger refreshSchedule = new AtomicInteger();
    private static long lastForegroundRefresh;
    // The protected file of an emailed link, opened once there is a file session.
    private static String pendingFileId;
    private static boolean fileRequestInFlight;

    private GleapFileAccess() {
    }

    /**
     * @return true when the token is set and valid for at least {@code marginMs} more
     */
    static boolean isValid(String token, String expiresAt, long marginMs) {
        if (token == null || token.isEmpty() || expiresAt == null) {
            return false;
        }
        try {
            return DateUtil.stringToDate(expiresAt).getTime() - marginMs > System.currentTimeMillis();
        } catch (Exception invalidDate) {
            return false;
        }
    }

    /**
     * The file id of an emailed file link: its {@code gleapFile} query parameter, only when it
     * is a file id (24 lowercase hex characters).
     */
    static String fileIdFromUrl(String url) {
        if (url == null) {
            return null;
        }
        int fragment = url.indexOf('#');
        String withoutFragment = fragment < 0 ? url : url.substring(0, fragment);
        int query = withoutFragment.indexOf('?');
        if (query < 0) {
            return null;
        }
        for (String parameter : withoutFragment.substring(query + 1).split("&")) {
            int separator = parameter.indexOf('=');
            String key = decode(separator < 0 ? parameter : parameter.substring(0, separator));
            if ("gleapFile".equals(key)) {
                String value = separator < 0 ? null : decode(parameter.substring(separator + 1));
                return value != null && FILE_ID.matcher(value).matches() ? value : null;
            }
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception invalid) {
            return null;
        }
    }

    /**
     * After a session answer was applied: (re)schedules the refresh, opens a pending file and,
     * when the project requires a file session the session does not have, identifies again
     * with the last identify. An identify answer never asks for another identify.
     */
    static void onSessionAnswer(GleapSessionController controller, GleapSession session, boolean fromIdentify) {
        scheduleRefresh(controller, session);
        openPendingFile();
        if (!fromIdentify && session.isAuthenticatedFilesRequired() && session.validFileAccessToken(0) == null) {
            controller.requestFileAccessIdentify();
        }
    }

    private static void scheduleRefresh(final GleapSessionController controller, GleapSession session) {
        final int schedule = refreshSchedule.incrementAndGet();
        String token = session.validFileAccessToken(0);
        if (token == null || !controller.hasIdentifyHash()) {
            return;
        }
        long delay;
        try {
            delay = DateUtil.stringToDate(session.getFileAccessExpiresAt()).getTime()
                    - REFRESH_MARGIN_MS - System.currentTimeMillis();
        } catch (Exception invalidDate) {
            return;
        }
        GleapMainThread.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (refreshSchedule.get() == schedule) {
                    controller.refreshFileAccess();
                }
            }
        }, Math.max(0, delay));
    }

    /**
     * The refresh timer does not run while the device sleeps: when the app comes to the
     * foreground, a file session that is missing or expires within the refresh margin is
     * refreshed right away.
     */
    static void refreshIfExpiring() {
        GleapSessionController controller = GleapSessionController.getInstance();
        GleapSession session = controller != null ? controller.getUserSession() : null;
        if (session == null || !session.isAuthenticatedFilesRequired() || !controller.hasIdentifyHash()
                || session.validFileAccessToken(REFRESH_MARGIN_MS) != null) {
            return;
        }
        synchronized (GleapFileAccess.class) {
            long now = System.currentTimeMillis();
            if (now - lastForegroundRefresh < FOREGROUND_REFRESH_INTERVAL_MS) {
                return;
            }
            lastForegroundRefresh = now;
        }
        controller.refreshFileAccess();
    }

    /**
     * {@link Gleap#openProtectedFileFromUrl(String)}.
     */
    static boolean openFromUrl(String url) {
        String fileId = fileIdFromUrl(url);
        if (fileId == null) {
            return false;
        }
        synchronized (GleapFileAccess.class) {
            pendingFileId = fileId;
        }
        openPendingFile();
        refreshIfExpiring();
        return true;
    }

    /**
     * Asks the API for the conversation of the pending file (GET /files/{id}/location with the
     * file session) and opens it. Waits while there is no valid file session; any answer but
     * the conversation's share token drops the file.
     */
    static void openPendingFile() {
        final GleapSessionController controller = GleapSessionController.getInstance();
        if (controller == null) {
            return;
        }
        final String fileId;
        final String token;
        synchronized (GleapFileAccess.class) {
            GleapSession session = controller.getUserSession();
            token = session != null ? session.validFileAccessToken(0) : null;
            if (pendingFileId == null || fileRequestInFlight || token == null) {
                return;
            }
            fileId = pendingFileId;
            fileRequestInFlight = true;
        }
        final int generation = controller.currentGeneration();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String shareToken = null;
                try {
                    shareToken = requestConversation(fileId, token);
                } catch (Exception ignore) {
                }

                boolean nextFile;
                synchronized (GleapFileAccess.class) {
                    fileRequestInFlight = false;
                    // Dropped by a logout, or replaced by another link meanwhile.
                    boolean current = fileId.equals(pendingFileId);
                    nextFile = !current && pendingFileId != null;
                    if (current) {
                        pendingFileId = null;
                    } else {
                        shareToken = null;
                    }
                }

                GleapSession session = controller.getUserSession();
                if (shareToken != null && controller.isCurrentGeneration(generation) && session != null
                        && token.equals(session.getFileAccessToken())) {
                    Gleap.getInstance().openConversation(shareToken);
                }
                if (nextFile) {
                    openPendingFile();
                }
            }
        });
    }

    private static String requestConversation(String fileId, String token) throws Exception {
        HttpURLConnection conn = GleapHttp.open(GleapConfig.getInstance().getApiUrl() + "/files/" + fileId + "/location");
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-File-Session", token);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            JSONObject result = GleapHttp.readLastJsonLine(conn.getInputStream());
            String shareToken = result != null ? result.optString("shareToken", "") : "";
            return shareToken.isEmpty() ? null : shareToken;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Logout: cancels the refresh, drops a pending file and revokes the file session
     * (POST /files/session/revoke). The revoke is fire-and-forget: offline, the local logout
     * still drops the token.
     */
    static void onLogout(final String token) {
        refreshSchedule.incrementAndGet();
        synchronized (GleapFileAccess.class) {
            pendingFileId = null;
        }
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection conn = null;
                    try {
                        conn = GleapHttp.open(GleapConfig.getInstance().getApiUrl() + "/files/session/revoke");
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("X-File-Session", token);
                        conn.setDoOutput(true);
                        conn.getOutputStream().close();
                        conn.getResponseCode();
                    } catch (Exception ignore) {
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                }
            });
        } catch (Exception ignore) {
        }
    }

    // Tests only.
    static void setExecutorForTesting(Executor testExecutor) {
        executor = testExecutor != null ? testExecutor : GleapExecutor.SERIAL;
    }

    // Tests only.
    static void resetForTesting() {
        refreshSchedule.incrementAndGet();
        synchronized (GleapFileAccess.class) {
            pendingFileId = null;
            fileRequestInFlight = false;
            lastForegroundRefresh = 0;
        }
    }
}
