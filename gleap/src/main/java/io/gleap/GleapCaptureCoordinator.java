package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;

/**
 * Runs the screenshots and recordings the widget asks for (capture-start). The widget cannot stay
 * open while the customer uses the app, so it is closed and the request is kept here:
 * <ol>
 * <li>The capture bar is shown on the app ({@link GleapCaptureBar}), on every activity the
 * customer goes to, until they tap Capture / Start recording (nothing is captured without that
 * tap) or Cancel.</li>
 * <li>Screenshot: the app's windows are captured ({@link GleapWindowCapture}) and the widget is
 * opened again on the conversation; its page gets capture-image and capture-state (after its ping)
 * and lets the customer mark up and send the screenshot.</li>
 * <li>Recording (API 26+): {@link GleapFrameRecorder} records until Stop, the time limit, or low
 * memory; the customer previews it ({@link GleapCapturePreview}) and sends, retakes or cancels it.
 * The SDK uploads it, completes the request, and opens the widget again with capture-state done.</li>
 * </ol>
 * Only one capture runs at a time. Everything here runs on the main thread; nothing is registered
 * or running while no capture runs.
 */
final class GleapCaptureCoordinator implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    // When the SDK looks for the app's activity itself, in case its resume was not seen.
    private static final long HOST_CHECK_MS = 3000;
    // Messages for the next widget page are dropped when the widget is not opened this soon.
    private static final long PENDING_TTL_MS = 10 * 60 * 1000;
    private static final long TIMER_TICK_MS = 500;
    private static final long SCREENSHOT_TIMEOUT_MS = 10000;
    private static final int JPEG_QUALITY = 85;
    private static final String RECORDING_NAME = "screen-recording.mp4";
    private static final String RECORDING_TYPE = "video/mp4";

    // How long the widget may take to open again after a capture before the app is told it closed.
    private static final long REOPEN_CHECK_MS = 8000;

    private static final GleapCaptureCoordinator instance = new GleapCaptureCoordinator();
    private static volatile boolean sessionActive;
    // The widget was closed for a capture without telling the app (no WidgetClosedCallback): to
    // the app it is one widget session until the widget opens again after the capture (no
    // WidgetOpenedCallback either). If it does not open again, the app is told it closed.
    private static volatile boolean widgetClosedForCapture;

    private enum State {
        IDLE,
        // The widget closes; the bar comes up once an activity of the app is shown.
        WAITING,
        BAR,
        CAPTURING,
        RECORDING,
        STOPPING,
        PREVIEW,
        UPLOADING
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private State state = State.IDLE;
    private GleapCaptureRequest request;
    private Application application;
    private WeakReference<Activity> host = new WeakReference<>(null);
    private GleapCaptureBar bar;
    private GleapCapturePreview preview;
    private GleapFrameRecorder recorder;
    private GleapFrameRecorder.Result recording;
    private HandlerThread captureThread;
    private final AtomicReference<Call> uploadCall = new AtomicReference<>();
    // Counts uploads, so the result of a cancelled one is ignored.
    private int uploadGeneration;
    private boolean lifecycleRegistered;
    private boolean memoryRegistered;

    // Messages for the next widget page: {request id, message}.
    private final List<String[]> pending = new ArrayList<>();
    private long pendingUntil;

    private GleapCaptureCoordinator() {
    }

    static GleapCaptureCoordinator getInstance() {
        return instance;
    }

    /**
     * A capture is running (the widget is closed for it): the SDK's in-app messages and
     * activation methods wait.
     */
    static boolean isSessionActive() {
        return sessionActive;
    }

    static boolean isWidgetClosedForCapture() {
        return widgetClosedForCapture;
    }

    /**
     * The widget opens (main thread): true when it opens again after a capture closed it, which
     * the app does not hear about.
     */
    boolean takeWidgetClosedForCapture() {
        boolean closed = widgetClosedForCapture;
        widgetClosedForCapture = false;
        main.removeCallbacks(reopenCheck);
        return closed;
    }

    /**
     * The widget could not be opened again after a capture: the app hears now that it closed.
     */
    void widgetNotReopened() {
        widgetClosedForCapture = true;
        tellAppWidgetClosed();
    }

    // The widget did not open again after the capture (e.g. no activity was shown).
    private final Runnable reopenCheck = new Runnable() {
        @Override
        public void run() {
            tellAppWidgetClosed();
        }
    };

    // The app hears now that the widget closed (held back when it closed for the capture).
    private void tellAppWidgetClosed() {
        main.removeCallbacks(reopenCheck);
        if (!widgetClosedForCapture) {
            return;
        }
        widgetClosedForCapture = false;
        try {
            if (GleapCallbacks.getInstance().getWidgetClosedCallback() != null) {
                GleapCallbacks.getInstance().getWidgetClosedCallback().invoke();
            }
        } catch (Throwable error) {
            GleapErrors.report(error, "widgetClosed");
        }
    }

    /**
     * Gleap.close() while the widget is closed for a capture (to the app it is open): ends the
     * capture without opening the widget again, and the app hears that the widget closed.
     */
    void closeByApp() {
        try {
            if (request != null && state != State.IDLE) {
                String id = request.id;
                discardRecording();
                postEvent(id, "released", null);
                queue(id, stateMessage(id, "cancelled", null));
                endSession();
            }
        } catch (Throwable error) {
            GleapErrors.report(error, "closeByApp");
        }
        tellAppWidgetClosed();
    }

    // ---------------------------------------------------------------------------------------------
    // The widget

    /**
     * The widget answered its ping: tells it what the SDK can capture.
     */
    void sendCapabilities(GleapMainActivity widget) {
        try {
            widget.sendMessage(GleapWidgetMessages.message("capture-capabilities", GleapCapture.currentCapabilities()));
        } catch (Throwable error) {
            GleapErrors.report(error, "sendCapabilities");
        }
    }

    /**
     * The widget answered its ping: hands it the results of a capture that ended while it was
     * closed (capture-image, capture-state).
     */
    void deliverPending(GleapMainActivity widget) {
        try {
            if (pending.isEmpty()) {
                return;
            }
            if (SystemClock.uptimeMillis() > pendingUntil) {
                pending.clear();
                return;
            }
            if (!widget.isTrustedWidgetPage()) {
                return;
            }
            for (String[] message : pending) {
                widget.sendMessage(message[1]);
            }
            pending.clear();
        } catch (Throwable error) {
            GleapErrors.report(error, "deliverPending");
        }
    }

    /**
     * capture-start, capture-cancel, capture-done and capture-editor from the widget. Only the
     * Gleap messenger page may send them.
     */
    void onWidgetMessage(GleapMainActivity widget, String name, JSONObject data) {
        try {
            if (!widget.isTrustedWidgetPage()) {
                GleapLog.w("Ignored " + name + ": it did not come from the Gleap messenger");
                return;
            }
            if ("capture-start".equals(name)) {
                onCaptureStart(widget, data);
            } else if ("capture-cancel".equals(name)) {
                onCaptureEnded(data);
            } else if ("capture-done".equals(name)) {
                onCaptureEnded(data);
            }
            // capture-editor: the widget is shown full screen anyway.
        } catch (Throwable error) {
            GleapErrors.report(error, name);
        }
    }

    private void onCaptureStart(GleapMainActivity widget, JSONObject data) {
        GleapCaptureRequest next = GleapCaptureRequest.fromStart(data);
        if (next == null) {
            return;
        }
        if (state != State.IDLE) {
            if (request != null && request.id.equals(next.id)) {
                // A repeated tap.
                return;
            }
            // Another request replaces the one running.
            releaseRunning();
        }
        // Refused right away: the widget hears it now and the request's claim is given back.
        if (!GleapCapture.isCaptureEnabled()) {
            refuse(widget, next.id, "unsupported", "Screen capture is turned off in this app.");
            return;
        }
        if (next.isRecording() && !GleapCapture.isRecordingSupported()) {
            refuse(widget, next.id, "unsupported", "Screen recording needs Android 8 or newer.");
            return;
        }
        Application app = GleapInitializer.getApplication();
        if (app == null) {
            refuse(widget, next.id, "failed", "The SDK is not initialized.");
            return;
        }

        application = app;
        request = next;
        main.removeCallbacks(reopenCheck);
        removePending(null);
        setState(State.WAITING);
        registerLifecycle();
        GleapOverlayManager.getInstance().setHiddenForCapture(true);
        GleapDetectorUtil.pauseDetectorsForCapture();
        bar = new GleapCaptureBar(next, next.isRecording() ? GleapCaptureBar.MODE_RECORD_READY : GleapCaptureBar.MODE_SCREENSHOT, barListener);
        sendNow(widget, stateMessage(next.id, "bar", null));

        // The customer goes back to the app: the widget closes (the app is not told), the bar
        // comes up.
        widgetClosedForCapture = true;
        widget.closeForCapture();
        main.postDelayed(hostCheck, HOST_CHECK_MS);
    }

    // capture-cancel / capture-done: the widget ended the request; nothing may stay behind.
    private void onCaptureEnded(JSONObject data) {
        String id = data != null ? data.optString("requestId", null) : null;
        if (id == null) {
            return;
        }
        removePending(id);
        if (request != null && request.id.equals(id) && state != State.IDLE) {
            discardRecording();
            endSession();
        }
    }

    // The bar comes up when an activity of the app resumes. Should that have been missed (the
    // activity was shown already), it comes up on the current one. Without one (the app went to
    // the background) it waits for the next activity; a slow device is no reason to fail.
    private final Runnable hostCheck = new Runnable() {
        @Override
        public void run() {
            if (state != State.WAITING) {
                return;
            }
            Activity current = ActivityUtil.getCurrentActivity();
            if (current != null && !ActivityUtil.isGleapActivity(current) && !current.isFinishing()) {
                onHostShown(current);
            }
        }
    };

    // ---------------------------------------------------------------------------------------------
    // Activities

    @Override
    public void onActivityResumed(Activity activity) {
        try {
            if (ActivityUtil.isGleapActivity(activity)) {
                onWidgetOpenedDuringCapture();
            } else {
                onHostShown(activity);
            }
        } catch (Throwable error) {
            GleapErrors.report(error, "capture onActivityResumed");
        }
    }

    private void onHostShown(Activity activity) {
        host = new WeakReference<>(activity);
        switch (state) {
            case WAITING:
                main.removeCallbacks(hostCheck);
                setState(State.BAR);
                bar.attach(activity);
                break;
            case BAR:
                bar.attach(activity);
                break;
            case RECORDING:
                bar.attach(activity);
                recorder.setActivity(activity);
                break;
            case PREVIEW:
            case UPLOADING:
                if (preview != null && !preview.isShownOn(activity)) {
                    preview.show(activity);
                }
                break;
            default:
                break;
        }
    }

    // The app (or the customer) opened the widget while the bar was shown: that ends the capture;
    // the widget learns it after its ping. A running upload goes on.
    private void onWidgetOpenedDuringCapture() {
        if (state == State.IDLE || state == State.WAITING || state == State.UPLOADING || request == null) {
            return;
        }
        String id = request.id;
        discardRecording();
        postEvent(id, "released", null);
        queue(id, stateMessage(id, "cancelled", null));
        endSession();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        try {
            if (activity != host.get()) {
                return;
            }
            if (bar != null && bar.isShownOn(activity)) {
                bar.detach();
            }
            if (state == State.RECORDING && recorder != null) {
                // Nothing of the app is recorded while it is not shown.
                recorder.setActivity(null);
            }
            if (preview != null && preview.isShownOn(activity)) {
                preview.dismiss();
            }
        } catch (Throwable error) {
            GleapErrors.report(error, "capture onActivityPaused");
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        try {
            if (bar != null && bar.isShownOn(activity)) {
                bar.detach();
            }
            if (preview != null && preview.isShownOn(activity)) {
                preview.dismiss();
            }
            if (host.get() == activity) {
                host = new WeakReference<>(null);
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    // ---------------------------------------------------------------------------------------------
    // Memory (registered while recording)

    @Override
    public void onTrimMemory(int level) {
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) {
            stopRecordingForMemory();
        }
    }

    @Override
    public void onLowMemory() {
        stopRecordingForMemory();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
    }

    private void stopRecordingForMemory() {
        try {
            if (state == State.RECORDING) {
                GleapLog.w("Memory is low: the recording stops and keeps what was recorded");
                stopRecording();
            }
        } catch (Throwable ignore) {
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The bar

    private final GleapCaptureBar.Listener barListener = new GleapCaptureBar.Listener() {
        @Override
        public void onCaptureTapped() {
            guard("capture", new Runnable() {
                @Override
                public void run() {
                    takeScreenshot();
                }
            });
        }

        @Override
        public void onStartTapped() {
            guard("startRecording", new Runnable() {
                @Override
                public void run() {
                    startRecording();
                }
            });
        }

        @Override
        public void onStopTapped() {
            guard("stopRecording", new Runnable() {
                @Override
                public void run() {
                    stopRecording();
                }
            });
        }

        @Override
        public void onCancelTapped() {
            guard("cancelCapture", new Runnable() {
                @Override
                public void run() {
                    cancelByCustomer();
                }
            });
        }
    };

    private void guard(String context, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            GleapErrors.report(error, context);
            if (request != null) {
                fail("Something went wrong on the device.");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Screenshot

    private void takeScreenshot() {
        final Activity activity = host.get();
        if (state != State.BAR || request == null || request.isRecording() || activity == null || activity.isFinishing()) {
            return;
        }
        if (!GleapCapture.isCaptureEnabled()) {
            unsupported("Screen capture is turned off in this app.");
            return;
        }
        View decor = activity.getWindow() != null ? activity.getWindow().peekDecorView() : null;
        if (decor == null || decor.getWidth() <= 0) {
            return;
        }
        setState(State.CAPTURING);
        bar.detach();

        final String requestId = request.id;
        final Date capturedAt = new Date();
        float scale = GleapCaptureGeometry.scaleForLongEdge(decor.getWidth(), decor.getHeight(), GleapCaptureGeometry.SCREENSHOT_MAX_EDGE);
        main.postDelayed(screenshotTimeout, SCREENSHOT_TIMEOUT_MS);
        GleapWindowCapture.capture(activity, scale, null, captureHandler(), null, new GleapWindowCapture.Callback() {
            @Override
            public void onFrame(GleapWindowCapture.Frame frame) {
                // On the capture thread: compose and encode.
                String dataUrl = null;
                int width = 0;
                int height = 0;
                try {
                    if (frame != null && frame.hasContent()) {
                        Bitmap image = frame.render();
                        width = image.getWidth();
                        height = image.getHeight();
                        dataUrl = jpegDataUrl(image);
                        image.recycle();
                    }
                } catch (Throwable error) {
                    GleapLog.w("Could not encode the screenshot", error);
                } finally {
                    if (frame != null) {
                        frame.release(null);
                    }
                }
                final String result = dataUrl;
                final int resultWidth = width;
                final int resultHeight = height;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        onScreenshotTaken(requestId, result, resultWidth, resultHeight, capturedAt);
                    }
                });
            }
        });
    }

    private final Runnable screenshotTimeout = new Runnable() {
        @Override
        public void run() {
            if (state == State.CAPTURING) {
                fail("The screenshot took too long.");
            }
        }
    };

    private void onScreenshotTaken(String requestId, String dataUrl, int width, int height, Date capturedAt) {
        main.removeCallbacks(screenshotTimeout);
        if (state != State.CAPTURING || request == null || !request.id.equals(requestId)) {
            return;
        }
        if (dataUrl == null) {
            fail("The screenshot could not be taken.");
            return;
        }
        try {
            JSONObject image = new JSONObject();
            image.put("requestId", requestId);
            image.put("dataUrl", dataUrl);
            image.put("width", width);
            image.put("height", height);
            image.put("method", GleapCapture.METHOD_FRAMES);
            image.put("platform", GleapCapture.PLATFORM);
            image.put("sdkType", GleapCapture.sdkType());
            image.put("sdkVersion", GleapCapture.sdkVersion());
            queue(requestId, GleapWidgetMessages.message("capture-image", image));
            queue(requestId, stateMessage(requestId, "preview", null));
        } catch (JSONException error) {
            fail("The screenshot could not be sent to the widget.");
            return;
        }
        if (request.attachLogs) {
            GleapCaptureLogs.getInstance().sendForCapture(requestId, request.include, null, capturedAt);
        }
        finishAndReopen();
    }

    // JPEG, quality 85, as a data url.
    private static String jpegDataUrl(Bitmap image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private Handler captureHandler() {
        if (captureThread == null) {
            captureThread = new HandlerThread("gleap-capture-screenshot");
            captureThread.start();
        }
        return new Handler(captureThread.getLooper());
    }

    // ---------------------------------------------------------------------------------------------
    // Recording

    private void startRecording() {
        Activity activity = host.get();
        if (state != State.BAR || request == null || !request.isRecording() || activity == null || activity.isFinishing()) {
            return;
        }
        if (!GleapCapture.isCaptureEnabled() || !GleapCapture.isRecordingSupported()) {
            unsupported("Screen recording is not available.");
            return;
        }
        File file = new File(GleapCapture.captureDir(activity.getApplicationContext()),
                "recording-" + request.id + "-" + System.currentTimeMillis() + ".mp4");
        GleapFrameRecorder next = new GleapFrameRecorder(file, request.maxDurationSec, null, recorderListener);
        if (!next.start(activity)) {
            // The screen is not laid out yet: the customer can tap again.
            return;
        }
        recorder = next;
        setState(State.RECORDING);
        recorder.setActivity(activity);
        registerMemory();
        bar.setMode(GleapCaptureBar.MODE_RECORDING);
        if (!bar.isShownOn(activity)) {
            bar.attach(activity);
        }
        main.post(timerTick);
    }

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (state != State.RECORDING || recorder == null || request == null) {
                return;
            }
            if (bar != null) {
                bar.setRecordedTime(recorder.recordedMs(), request.maxDurationSec);
            }
            main.postDelayed(this, TIMER_TICK_MS);
        }
    };

    private void stopRecording() {
        if (state != State.RECORDING || recorder == null) {
            return;
        }
        setState(State.STOPPING);
        main.removeCallbacks(timerTick);
        unregisterMemory();
        if (bar != null) {
            bar.detach();
        }
        recorder.stop();
    }

    private final GleapFrameRecorder.Listener recorderListener = new GleapFrameRecorder.Listener() {
        @Override
        public void onRecordingFinished(GleapFrameRecorder.Result result) {
            try {
                onRecordingFinishedInternal(result);
            } catch (Throwable error) {
                GleapErrors.report(error, "onRecordingFinished");
            }
        }
    };

    private void onRecordingFinishedInternal(GleapFrameRecorder.Result result) {
        main.removeCallbacks(timerTick);
        unregisterMemory();
        if ((state != State.RECORDING && state != State.STOPPING) || request == null) {
            // Cancelled meanwhile.
            delete(result.file);
            return;
        }
        recorder = null;
        if (!result.success && result.empty) {
            // Stopped right away: ready to record again.
            delete(result.file);
            setState(State.BAR);
            bar.setMode(GleapCaptureBar.MODE_RECORD_READY);
            Activity activity = host.get();
            if (activity != null) {
                bar.attach(activity);
            }
            return;
        }
        if (!result.success) {
            delete(result.file);
            fail(result.error != null ? result.error : "The recording failed.");
            return;
        }
        recording = result;
        setState(State.PREVIEW);
        if (bar != null) {
            bar.detach();
        }
        preview = new GleapCapturePreview(request, result.file, result.width, result.height, previewListener);
        Activity activity = host.get();
        if (activity != null && !activity.isFinishing()) {
            preview.show(activity);
        }
    }

    private final GleapCapturePreview.Listener previewListener = new GleapCapturePreview.Listener() {
        @Override
        public void onSendTapped() {
            guard("sendRecording", new Runnable() {
                @Override
                public void run() {
                    sendRecording();
                }
            });
        }

        @Override
        public void onRetakeTapped() {
            guard("retakeRecording", new Runnable() {
                @Override
                public void run() {
                    retake();
                }
            });
        }

        @Override
        public void onCancelTapped() {
            guard("cancelRecording", new Runnable() {
                @Override
                public void run() {
                    cancelByCustomer();
                }
            });
        }
    };

    private void retake() {
        if (state != State.PREVIEW) {
            return;
        }
        discardRecording();
        if (preview != null) {
            preview.dismiss();
            preview = null;
        }
        setState(State.BAR);
        bar.setMode(GleapCaptureBar.MODE_RECORD_READY);
        Activity activity = host.get();
        if (activity != null) {
            bar.attach(activity);
        }
    }

    private void sendRecording() {
        if (state != State.PREVIEW || recording == null || request == null) {
            return;
        }
        setState(State.UPLOADING);
        if (preview != null) {
            preview.setUploading(0);
        }
        final int generation = ++uploadGeneration;
        final GleapFrameRecorder.Result result = recording;
        final String requestId = request.id;
        GleapCaptureExecutor.execute(new Runnable() {
            @Override
            public void run() {
                int status = 0;
                boolean completed = false;
                try {
                    JSONObject uploaded = GleapCaptureApi.uploadFile(result.file, RECORDING_NAME, RECORDING_TYPE,
                            new GleapCaptureApi.ProgressListener() {
                                @Override
                                public void onProgress(final float progress) {
                                    main.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (generation == uploadGeneration && state == State.UPLOADING && preview != null) {
                                                // The upload is most of the work; completing is the rest.
                                                preview.setUploading(progress * 0.95f);
                                            }
                                        }
                                    });
                                }
                            }, uploadCall);
                    JSONArray urls = uploaded.optJSONArray("fileUrls");
                    String url = urls != null ? urls.optString(0, null) : null;
                    if (url == null || url.isEmpty()) {
                        throw new java.io.IOException("The upload returned no file");
                    }
                    GleapCaptureApi.Response response = GleapCaptureApi.complete(requestId, completeBody(url, result));
                    status = response.status;
                    completed = response.ok();
                } catch (Throwable error) {
                    GleapLog.w("Could not send the recording", error);
                }
                final int finalStatus = status;
                final boolean finalCompleted = completed;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        onRecordingSent(generation, requestId, finalCompleted, finalStatus);
                    }
                });
            }
        });
    }

    private static JSONObject completeBody(String url, GleapFrameRecorder.Result result) throws JSONException {
        JSONObject file = new JSONObject();
        file.put("url", url);
        file.put("name", RECORDING_NAME);
        file.put("type", RECORDING_TYPE);
        file.put("size", result.file.length());
        file.put("width", result.width);
        file.put("height", result.height);
        file.put("durationMs", result.durationMs);

        JSONObject body = new JSONObject();
        body.put("files", new JSONArray().put(file));
        body.put("method", GleapCapture.METHOD_FRAMES);
        body.put("platform", GleapCapture.PLATFORM);
        body.put("sdkType", GleapCapture.sdkType());
        body.put("sdkVersion", GleapCapture.sdkVersion());
        body.put("deviceId", GleapCapture.deviceId());
        if (result.startedAt != null) {
            body.put("recordingStartedAt", DateUtil.dateToString(result.startedAt));
        }
        if (result.endedAt != null) {
            body.put("recordingEndedAt", DateUtil.dateToString(result.endedAt));
        }
        return body;
    }

    private void onRecordingSent(int generation, String requestId, boolean completed, int status) {
        if (generation != uploadGeneration || state != State.UPLOADING || request == null || !request.id.equals(requestId)) {
            return;
        }
        if (completed || status == 409 || status == 410) {
            // Sent; or the request was answered on another device, cancelled or expired meanwhile.
            GleapFrameRecorder.Result result = recording;
            String message = completed
                    ? stateMessage(requestId, "done", null)
                    : stateMessage(requestId, "failed", "The request is closed.");
            if (completed && request.attachLogs && result != null) {
                GleapCaptureLogs.getInstance().sendForCapture(requestId, request.include, result.startedAt, result.endedAt);
            }
            discardRecording();
            if (GleapMainActivity.deliverToOpenWidget(message)) {
                endSession();
            } else {
                queue(requestId, message);
                finishAndReopen();
            }
            return;
        }
        // Not sent (e.g. offline): the customer can try again or cancel.
        setState(State.PREVIEW);
        if (preview != null) {
            preview.setError(request.label("failed"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Ending a capture

    private void cancelByCustomer() {
        if (request == null || state == State.IDLE) {
            return;
        }
        String id = request.id;
        discardRecording();
        postEvent(id, "released", null);
        queue(id, stateMessage(id, "cancelled", null));
        finishAndReopen();
    }

    private void unsupported(String reason) {
        if (request == null) {
            return;
        }
        String id = request.id;
        discardRecording();
        postEvent(id, "unsupported", reason);
        queue(id, stateMessage(id, "unsupported", reason));
        finishAndReopen();
    }

    private void fail(String reason) {
        if (request == null) {
            return;
        }
        String id = request.id;
        discardRecording();
        postEvent(id, "failed", reason);
        queue(id, stateMessage(id, "failed", reason));
        finishAndReopen();
    }

    // A new capture-start replaced this one.
    private void releaseRunning() {
        if (request != null) {
            postEvent(request.id, "released", null);
        }
        discardRecording();
        endSession();
    }

    // Stops recording and uploading and deletes the recording file.
    private void discardRecording() {
        uploadGeneration++;
        Call call = uploadCall.getAndSet(null);
        if (call != null) {
            call.cancel();
        }
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        if (recording != null) {
            delete(recording.file);
            recording = null;
        }
    }

    private void finishAndReopen() {
        String token = request != null ? request.ticketShareToken : null;
        endSession();
        // Back to the conversation; its page gets the pending messages after its ping.
        try {
            if (token != null && !token.isEmpty()) {
                Gleap.getInstance().openConversation(token);
            } else {
                Gleap.getInstance().open();
            }
        } catch (Throwable error) {
            GleapErrors.report(error, "reopenWidget");
        }
        if (widgetClosedForCapture) {
            main.removeCallbacks(reopenCheck);
            main.postDelayed(reopenCheck, REOPEN_CHECK_MS);
        }
    }

    private void endSession() {
        main.removeCallbacks(hostCheck);
        main.removeCallbacks(timerTick);
        main.removeCallbacks(screenshotTimeout);
        if (bar != null) {
            bar.detach();
            bar = null;
        }
        if (preview != null) {
            preview.dismiss();
            preview = null;
        }
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
        }
        unregisterMemory();
        unregisterLifecycle();
        request = null;
        host = new WeakReference<>(null);
        setState(State.IDLE);
        try {
            GleapOverlayManager.getInstance().setHiddenForCapture(false);
        } catch (Throwable ignore) {
        }
        try {
            GleapDetectorUtil.resumeDetectorsAfterCapture();
        } catch (Throwable ignore) {
        }
    }

    private void setState(State next) {
        state = next;
        sessionActive = next != State.IDLE;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers

    private void registerLifecycle() {
        if (!lifecycleRegistered && application != null) {
            application.registerActivityLifecycleCallbacks(this);
            lifecycleRegistered = true;
        }
    }

    private void unregisterLifecycle() {
        if (lifecycleRegistered && application != null) {
            application.unregisterActivityLifecycleCallbacks(this);
        }
        lifecycleRegistered = false;
    }

    private void registerMemory() {
        if (!memoryRegistered && application != null) {
            application.registerComponentCallbacks(this);
            memoryRegistered = true;
        }
    }

    private void unregisterMemory() {
        if (memoryRegistered && application != null) {
            application.unregisterComponentCallbacks(this);
        }
        memoryRegistered = false;
    }

    private void queue(String requestId, String message) {
        if (message == null) {
            return;
        }
        pending.add(new String[]{requestId, message});
        pendingUntil = SystemClock.uptimeMillis() + PENDING_TTL_MS;
    }

    // Drops the pending messages of a request (null: all of them).
    private void removePending(String requestId) {
        for (Iterator<String[]> it = pending.iterator(); it.hasNext(); ) {
            String[] message = it.next();
            if (requestId == null || requestId.equals(message[0])) {
                it.remove();
            }
        }
    }

    private static String stateMessage(String requestId, String state, String error) {
        try {
            JSONObject data = new JSONObject();
            data.put("requestId", requestId);
            data.put("state", state);
            if (error != null) {
                data.put("error", error);
            }
            return GleapWidgetMessages.message("capture-state", data);
        } catch (JSONException e) {
            return null;
        }
    }

    private static void refuse(GleapMainActivity widget, String requestId, String state, String reason) {
        postEvent(requestId, state, reason);
        sendNow(widget, stateMessage(requestId, state, reason));
    }

    private static void sendNow(GleapMainActivity widget, String message) {
        if (message != null) {
            widget.sendMessage(message);
        }
    }

    private static void postEvent(final String requestId, final String type, final String reason) {
        GleapCaptureExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    GleapCaptureApi.event(requestId, type, reason);
                } catch (Throwable error) {
                    GleapLog.w("Could not report the capture event " + type, error);
                }
            }
        });
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            GleapLog.w("Could not delete " + file.getName());
        }
    }
}
