package io.gleap;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import gleap.io.gleap.BuildConfig;

/**
 * Capture requests ("show me the issue"): the app's switches for them, the views it masks, and
 * what the SDK reports it can do (to the widget, on the WebSocket and with the pings).
 * <p>
 * Nothing here runs on its own: the capture code only starts when a request arrives, from the
 * widget ({@link GleapCaptureCoordinator}) or from the server ({@link GleapCaptureLogs}).
 */
final class GleapCapture {
    static final int CAPABILITIES_VERSION = 1;
    static final String PLATFORM = "android";
    // Native in-app frame capture (screenshots and recordings alike).
    static final String METHOD_FRAMES = "frames";

    static final String CAP_SCREENSHOT = "capture.screenshot";
    static final String CAP_RECORDING = "capture.recording";
    static final String CAP_LOGS = "capture.logs";

    static final int MIN_RECORDING_SEC = 5;
    static final int DEFAULT_RECORDING_SEC = 60;
    static final int MAX_RECORDING_SEC = 180;

    // Server ids are ObjectIds; anything else is never put into a request path.
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final String PREFS = "gleap-capture";
    private static final String DEVICE_ID_KEY = "deviceId";
    private static final String CAPTURE_DIR = "gleap-capture";
    // The recordings of the running capture (absolute paths): never deleted as leftovers.
    private static final Set<String> filesInUse = Collections.synchronizedSet(new HashSet<String>());

    private static volatile boolean captureEnabled = true;
    private static volatile boolean remoteLogCollectionEnabled = true;
    private static volatile GleapLogFlushHandler logFlushHandler;
    // The views the app masked; weak, so a masked view never outlives its screen. Guarded by itself.
    private static final Map<View, Boolean> maskedViews = new WeakHashMap<>();
    private static String deviceId;

    private GleapCapture() {
    }

    static boolean isCaptureEnabled() {
        return captureEnabled;
    }

    static void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
    }

    static boolean isRemoteLogCollectionEnabled() {
        return remoteLogCollectionEnabled;
    }

    static void setRemoteLogCollectionEnabled(boolean enabled) {
        remoteLogCollectionEnabled = enabled;
    }

    static GleapLogFlushHandler getLogFlushHandler() {
        return logFlushHandler;
    }

    static void setLogFlushHandler(GleapLogFlushHandler handler) {
        logFlushHandler = handler;
    }

    static void maskView(View view) {
        if (view == null) {
            return;
        }
        synchronized (maskedViews) {
            maskedViews.put(view, Boolean.TRUE);
        }
    }

    static void unmaskView(View view) {
        if (view == null) {
            return;
        }
        synchronized (maskedViews) {
            maskedViews.remove(view);
        }
    }

    /**
     * The masked views that are still alive, as an identity set.
     */
    static Set<View> maskedViews() {
        synchronized (maskedViews) {
            if (maskedViews.isEmpty()) {
                return Collections.emptySet();
            }
            Set<View> views = Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
            views.addAll(maskedViews.keySet());
            views.remove(null);
            return views;
        }
    }

    /**
     * Recordings are frames of the app's own windows, encoded with MediaCodec: PixelCopy of a
     * window needs API 26.
     */
    static boolean isRecordingSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    static String sdkType() {
        try {
            APPLICATIONTYPE type = GleapBug.getInstance().getApplicationType();
            return type != null ? type.name() : APPLICATIONTYPE.NATIVE.name();
        } catch (Throwable ignore) {
            return APPLICATIONTYPE.NATIVE.name();
        }
    }

    static String sdkVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * The capability flags the SDK announces on the WebSocket and with the pings.
     */
    static List<String> caps(boolean captureEnabled, boolean recordingSupported, boolean logsEnabled) {
        List<String> caps = new ArrayList<>();
        if (captureEnabled) {
            caps.add(CAP_SCREENSHOT);
            if (recordingSupported) {
                caps.add(CAP_RECORDING);
            }
        }
        if (logsEnabled) {
            caps.add(CAP_LOGS);
        }
        return caps;
    }

    static List<String> currentCaps() {
        return caps(captureEnabled, isRecordingSupported(), remoteLogCollectionEnabled);
    }

    static JSONArray currentCapsJson() {
        JSONArray caps = new JSONArray();
        for (String cap : currentCaps()) {
            caps.put(cap);
        }
        return caps;
    }

    /**
     * The value of the WebSocket url's {@code caps} parameter, e.g.
     * {@code capture.screenshot,capture.recording,capture.logs}; empty when nothing is supported.
     */
    static String capsQueryValue(List<String> caps) {
        StringBuilder value = new StringBuilder();
        for (String cap : caps) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(cap);
        }
        return value.toString();
    }

    /**
     * The capture-capabilities message data for the widget. With capture disabled nothing is
     * offered, so the widget only offers the upload.
     */
    static JSONObject capabilities(boolean captureEnabled, boolean recordingSupported, String sdkType,
                                   String sdkVersion) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("version", CAPABILITIES_VERSION);
        data.put("platform", PLATFORM);
        data.put("sdkType", sdkType);
        data.put("sdkVersion", sdkVersion);
        data.put("screenshot", captureEnabled);
        boolean recording = captureEnabled && recordingSupported;
        data.put("recording", recording);
        if (recording) {
            data.put("recordingMethod", METHOD_FRAMES);
            data.put("maxRecordingSec", MAX_RECORDING_SEC);
        }
        data.put("annotate", true);
        data.put("microphone", false);
        return data;
    }

    static JSONObject currentCapabilities() throws JSONException {
        return capabilities(captureEnabled, isRecordingSupported(), sdkType(), sdkVersion());
    }

    static boolean isValidRequestId(String id) {
        return id != null && REQUEST_ID.matcher(id).matches();
    }

    /**
     * Whether {@code url} has the scheme, host and port of {@code expectedUrl} (the widget url).
     * Used to accept capture commands only from the Gleap messenger page.
     */
    static boolean sameOrigin(String url, String expectedUrl) {
        if (url == null || expectedUrl == null) {
            return false;
        }
        try {
            URL actual = new URL(url.trim());
            URL expected = new URL(expectedUrl.trim());
            String scheme = actual.getProtocol().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                return false;
            }
            if (!scheme.equals(expected.getProtocol().toLowerCase(Locale.ROOT))) {
                return false;
            }
            String host = actual.getHost();
            if (host == null || host.isEmpty() || !host.equalsIgnoreCase(expected.getHost())) {
                return false;
            }
            return port(actual) == port(expected);
        } catch (Exception e) {
            return false;
        }
    }

    private static int port(URL url) {
        return url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
    }

    /**
     * A random id of this installation, sent when the SDK claims a capture request (so the
     * server can tell devices apart). Created on the first capture request.
     */
    static synchronized String deviceId() {
        if (deviceId != null) {
            return deviceId;
        }
        try {
            Application application = GleapInitializer.getApplication();
            if (application != null) {
                SharedPreferences prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String stored = prefs.getString(DEVICE_ID_KEY, null);
                if (stored == null || stored.isEmpty()) {
                    stored = UUID.randomUUID().toString();
                    prefs.edit().putString(DEVICE_ID_KEY, stored).apply();
                }
                deviceId = stored;
                return deviceId;
            }
        } catch (Throwable ignore) {
        }
        deviceId = UUID.randomUUID().toString();
        return deviceId;
    }

    /**
     * Where recordings are written while they are recorded, previewed and uploaded: app storage
     * the system never clears by itself (it may clear the cache when the device runs low on
     * storage, also between Stop and Send). The SDK deletes every recording itself: when it is
     * sent, retaken, cancelled or fails, and leftovers when the SDK starts.
     */
    static File captureDir(Context context) {
        File dir = new File(context.getNoBackupFilesDir(), CAPTURE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            GleapLog.w("Could not create the capture directory");
        }
        return dir;
    }

    /**
     * A recording of the running capture: kept until {@link #deleteCaptureFile} (the cleanup at
     * start leaves it alone).
     */
    static void useCaptureFile(File file) {
        if (file != null) {
            filesInUse.add(file.getAbsolutePath());
        }
    }

    /**
     * Deletes a recording of the running capture (sent, retaken, cancelled, failed). Any thread.
     */
    static void deleteCaptureFile(File file) {
        if (file == null) {
            return;
        }
        filesInUse.remove(file.getAbsolutePath());
        if (file.exists() && !file.delete()) {
            GleapLog.w("Could not delete " + file.getName());
        }
    }

    /**
     * Deletes the recordings left behind, e.g. by a process that ended while it recorded or sent
     * one (when the SDK starts).
     */
    static void deleteCaptureFiles(Context context) {
        try {
            if (context != null) {
                deleteLeftovers(new File(context.getNoBackupFilesDir(), CAPTURE_DIR));
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Deletes the files in {@code dir} that are not a recording of the running capture.
     *
     * @return how many were deleted
     */
    static int deleteLeftovers(File dir) {
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (filesInUse.contains(file.getAbsolutePath())) {
                continue;
            }
            if (file.delete()) {
                deleted++;
            } else {
                GleapLog.w("Could not delete " + file.getName());
            }
        }
        return deleted;
    }
}
