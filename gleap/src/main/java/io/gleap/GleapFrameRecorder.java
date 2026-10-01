package io.gleap;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.View;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Records the app's windows into an H.264 MP4 (API 26+): the main thread captures a frame
 * about 4 times a second (2 to 8, see {@link GleapCaptureGeometry#nextFrameInterval}) with
 * {@link GleapWindowCapture} at the recording's scale; the frames are drawn onto the encoder's
 * input surface and muxed on a background thread. No audio.
 * <p>
 * The output size is fixed when the recording starts (long edge at most 1280, even); after a
 * rotation or a window resize the frames are letterboxed into it. While no activity of the app
 * is shown the recording pauses, and the pause is left out of the video.
 */
@TargetApi(Build.VERSION_CODES.O)
final class GleapFrameRecorder {
    interface Listener {
        /**
         * The recording ended (main thread).
         */
        void onRecordingFinished(Result result);
    }

    static final class Result {
        final File file;
        final boolean success;
        final long durationMs;
        final int width;
        final int height;
        final Date startedAt;
        final Date endedAt;
        final String error;
        // Stopped before the first frame was encoded (nothing went wrong).
        final boolean empty;

        Result(File file, boolean success, long durationMs, int width, int height, Date startedAt, Date endedAt,
               String error, boolean empty) {
            this.file = file;
            this.success = success;
            this.durationMs = durationMs;
            this.width = width;
            this.height = height;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.error = error;
            this.empty = empty;
        }
    }

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 1000000;
    // The fastest adaptive rate: the bit budget per frame never exceeds ~1 Mbps.
    private static final int FRAME_RATE = 8;
    // The SDK asks for a key frame every 2 s of video (at any frame rate); the encoder's own
    // interval is only a backstop, so the two do not double up.
    private static final long KEY_FRAME_INTERVAL_US = 2000000L;
    private static final int ENCODER_KEY_FRAME_INTERVAL_SEC = 10;
    // A frame whose copies never came back is given up after this long.
    private static final long FRAME_WATCHDOG_MS = 3000;
    private static final long DRAIN_TIMEOUT_US = 10000;
    private static final long END_OF_STREAM_TIMEOUT_MS = 3000;
    // Encoder timestamps further off the clock than this are replaced by the SDK's own.
    private static final long CLOCK_TOLERANCE_US = 5000000;
    // Frames the encoder may hold before it outputs them (it never holds this many).
    private static final int MAX_PENDING_TIMESTAMPS = 60;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final GleapWindowCapture.BitmapPool pool = new GleapWindowCapture.BitmapPool();
    private final File file;
    private final long maxDurationMs;
    private final Listener listener;
    private final Set<View> excludedRoots;

    // Main thread.
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private HandlerThread thread;
    private Handler encoderHandler;
    private boolean running;
    private boolean paused;
    private long intervalMs = GleapCaptureGeometry.TARGET_FRAME_INTERVAL_MS;
    private long recordedMs;
    private long lastTickAt;
    private int frameSequence;
    private int frameInFlight = -1;
    private long frameInFlightSince;
    private boolean keptUp = true;
    private Date startedAt;
    private volatile int outputWidth;
    private volatile int outputHeight;

    // Encoder thread.
    private MediaCodec codec;
    private Surface inputSurface;
    private MediaMuxer muxer;
    private int track = -1;
    private boolean muxerStarted;
    private boolean softwareCanvas;
    private boolean ownClock;
    private boolean clockChecked;
    private long firstTimeUs = -1;
    private long lastTimeUs = -1;
    private long previousTimeUs = -1;
    private long lastKeyFrameUs = Long.MIN_VALUE;
    // A key frame was asked for and has not come out yet: not asked for again meanwhile.
    private boolean keyFrameRequested;
    private int framesWritten;
    private volatile boolean released;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    // When each submitted frame was posted (µs, monotonic): the timestamps when the encoder's are off.
    private final ArrayDeque<Long> submittedAt = new ArrayDeque<>();
    // Pauses as {start, end} in µs of the monotonic clock. Guarded by itself.
    private final List<long[]> pauses = new ArrayList<>();
    private long pauseStartedUs = -1;

    GleapFrameRecorder(File file, int maxDurationSec, Set<View> excludedRoots, Listener listener) {
        this.file = file;
        this.maxDurationMs = maxDurationSec * 1000L;
        this.excludedRoots = excludedRoots;
        this.listener = listener;
    }

    /**
     * Starts recording the activity (main thread).
     *
     * @return false when there is nothing to record (no laid out window)
     */
    boolean start(Activity current) {
        View decor = current != null && current.getWindow() != null ? current.getWindow().peekDecorView() : null;
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            return false;
        }
        final int areaWidth = decor.getWidth();
        final int areaHeight = decor.getHeight();
        int[] planned = GleapCaptureGeometry.encoderSize(areaWidth, areaHeight, GleapCaptureGeometry.RECORDING_MAX_EDGE, 2);
        outputWidth = planned[0];
        outputHeight = planned[1];

        activity = new WeakReference<>(current);
        thread = new HandlerThread("gleap-capture-encoder");
        thread.start();
        encoderHandler = new Handler(thread.getLooper());
        encoderHandler.post(new Runnable() {
            @Override
            public void run() {
                prepareEncoder(areaWidth, areaHeight);
            }
        });

        startedAt = new Date();
        running = true;
        lastTickAt = SystemClock.uptimeMillis();
        main.post(tick);
        return true;
    }

    /**
     * The activity to record; null while none is shown (the recording pauses).
     */
    void setActivity(Activity current) {
        activity = new WeakReference<>(current);
    }

    /**
     * The recorded time so far, without pauses.
     */
    long recordedMs() {
        return recordedMs;
    }

    boolean isRunning() {
        return running;
    }

    /**
     * Stops and finishes the file; the listener gets the result (main thread).
     */
    void stop() {
        if (!running) {
            return;
        }
        running = false;
        main.removeCallbacks(tick);
        endPause();
        final long duration = recordedMs;
        final Date endedAt = new Date();
        final Date started = startedAt;
        encoderHandler.post(new Runnable() {
            @Override
            public void run() {
                final Result result = finish(duration, started, endedAt);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.onRecordingFinished(result);
                        } catch (Throwable error) {
                            GleapErrors.report(error, "onRecordingFinished");
                        }
                    }
                });
            }
        });
        thread.quitSafely();
    }

    /**
     * Stops without a result and deletes the file (main thread).
     */
    void cancel() {
        running = false;
        main.removeCallbacks(tick);
        if (encoderHandler == null) {
            return;
        }
        encoderHandler.post(new Runnable() {
            @Override
            public void run() {
                releaseEncoder();
                if (muxer != null) {
                    try {
                        muxer.release();
                    } catch (Throwable ignore) {
                    }
                    muxer = null;
                }
                deleteFile();
                pool.clear();
            }
        });
        thread.quitSafely();
    }

    // ---------------------------------------------------------------------------------------------
    // Main thread: capturing

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                onTick();
            } catch (Throwable error) {
                GleapLog.w("A recording frame failed", error);
                if (running) {
                    main.postDelayed(tick, intervalMs);
                }
            }
        }
    };

    private void onTick() {
        if (!running) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        Activity current = activity.get();
        if (current == null || current.isFinishing()) {
            // The app is in the background (or between two activities): pause.
            if (!paused) {
                paused = true;
                recordedMs += now - lastTickAt;
                startPause();
            }
            lastTickAt = now;
            main.postDelayed(tick, intervalMs);
            return;
        }
        if (paused) {
            paused = false;
            endPause();
        } else {
            recordedMs += now - lastTickAt;
        }
        lastTickAt = now;

        if (recordedMs >= maxDurationMs) {
            stop();
            return;
        }

        if (frameInFlight >= 0) {
            if (now - frameInFlightSince < FRAME_WATCHDOG_MS) {
                // The last frame is still being copied or encoded: skip this one.
                keptUp = false;
                main.postDelayed(tick, intervalMs);
                return;
            }
            frameInFlight = -1;
        }

        final int sequence = ++frameSequence;
        frameInFlight = sequence;
        frameInFlightSince = now;
        GleapWindowCapture.capture(current, captureScale(current), excludedRoots, encoderHandler, pool,
                new GleapWindowCapture.Callback() {
                    @Override
                    public void onFrame(GleapWindowCapture.Frame frame) {
                        onFrameCaptured(sequence, frame);
                    }
                });
        long mainThreadMs = SystemClock.uptimeMillis() - now;
        intervalMs = GleapCaptureGeometry.nextFrameInterval(intervalMs, mainThreadMs, keptUp);
        keptUp = true;
        main.postDelayed(tick, intervalMs);
    }

    // Frames are captured at the size they are encoded at.
    private float captureScale(Activity current) {
        View decor = current.getWindow() != null ? current.getWindow().peekDecorView() : null;
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            return 1f;
        }
        float scale = Math.min(outputWidth / (float) decor.getWidth(), outputHeight / (float) decor.getHeight());
        return Math.max(0.05f, Math.min(1f, scale));
    }

    private void startPause() {
        synchronized (pauses) {
            pauseStartedUs = nowUs();
        }
    }

    private void endPause() {
        synchronized (pauses) {
            if (pauseStartedUs >= 0) {
                pauses.add(new long[]{pauseStartedUs, nowUs()});
                pauseStartedUs = -1;
            }
        }
    }

    private static long nowUs() {
        return System.nanoTime() / 1000;
    }

    // ---------------------------------------------------------------------------------------------
    // Encoder thread

    private void onFrameCaptured(final int sequence, GleapWindowCapture.Frame frame) {
        try {
            if (frame != null && !released && codec != null) {
                encode(frame);
            }
        } catch (Throwable error) {
            GleapLog.w("Could not encode a recording frame", error);
        } finally {
            if (frame != null) {
                frame.release(released ? null : pool);
            }
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (frameInFlight == sequence) {
                        frameInFlight = -1;
                    }
                }
            });
        }
    }

    private void prepareEncoder(int areaWidth, int areaHeight) {
        try {
            codec = createEncoder(areaWidth, areaHeight);
            muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            softwareCanvas = prefersSoftwareCanvas();
        } catch (Throwable error) {
            GleapLog.w("Could not start the recording encoder", error);
            releaseEncoder();
            main.post(new Runnable() {
                @Override
                public void run() {
                    failEarly("The video encoder could not be started.");
                }
            });
        }
    }

    // The encoder did not start: the recording ends right away.
    private void failEarly(final String error) {
        if (!running) {
            return;
        }
        running = false;
        main.removeCallbacks(tick);
        final Date started = startedAt;
        encoderHandler.post(new Runnable() {
            @Override
            public void run() {
                released = true;
                if (muxer != null) {
                    try {
                        muxer.release();
                    } catch (Throwable ignore) {
                    }
                    muxer = null;
                }
                deleteFile();
                pool.clear();
            }
        });
        thread.quitSafely();
        try {
            listener.onRecordingFinished(new Result(file, false, 0, outputWidth, outputHeight, started, new Date(), error, false));
        } catch (Throwable reportError) {
            GleapErrors.report(reportError, "onRecordingFinished");
        }
    }

    /**
     * The device's default H.264 encoder, else any other (software ones first) that accepts
     * the recording's size.
     */
    private MediaCodec createEncoder(int areaWidth, int areaHeight) throws Exception {
        String triedName = null;
        try {
            MediaCodec encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            triedName = encoder.getName();
            if (startEncoder(encoder, areaWidth, areaHeight)) {
                return encoder;
            }
        } catch (Throwable error) {
            GleapLog.w("The default video encoder is not available", error);
        }

        List<MediaCodecInfo> candidates = new ArrayList<>();
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder() && supports(info, MIME_TYPE) && !info.getName().equals(triedName)) {
                candidates.add(info);
            }
        }
        // Software encoders first: the hardware one (the default) already failed.
        List<MediaCodecInfo> ordered = new ArrayList<>();
        for (MediaCodecInfo info : candidates) {
            if (isSoftware(info)) {
                ordered.add(info);
            }
        }
        for (MediaCodecInfo info : candidates) {
            if (!isSoftware(info)) {
                ordered.add(info);
            }
        }
        for (MediaCodecInfo info : ordered) {
            try {
                MediaCodec encoder = MediaCodec.createByCodecName(info.getName());
                if (startEncoder(encoder, areaWidth, areaHeight)) {
                    return encoder;
                }
            } catch (Throwable error) {
                GleapLog.w("The video encoder " + info.getName() + " is not available", error);
            }
        }
        throw new IllegalStateException("No H.264 encoder could be started");
    }

    private boolean startEncoder(MediaCodec encoder, int areaWidth, int areaHeight) {
        try {
            int[] size = encoderSize(encoder, areaWidth, areaHeight);
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, size[0], size[1]);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, ENCODER_KEY_FRAME_INTERVAL_SEC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface surface = encoder.createInputSurface();
            encoder.start();
            inputSurface = surface;
            outputWidth = size[0];
            outputHeight = size[1];
            return true;
        } catch (Throwable error) {
            GleapLog.w("Could not configure the video encoder " + safeName(encoder), error);
            try {
                encoder.release();
            } catch (Throwable ignore) {
            }
            return false;
        }
    }

    // The size the encoder takes: its alignment, and a smaller size if it cannot do the planned one.
    private static int[] encoderSize(MediaCodec encoder, int areaWidth, int areaHeight) {
        int alignment = 2;
        MediaCodecInfo.VideoCapabilities video = null;
        try {
            video = encoder.getCodecInfo().getCapabilitiesForType(MIME_TYPE).getVideoCapabilities();
            alignment = Math.max(alignment, Math.max(video.getWidthAlignment(), video.getHeightAlignment()));
        } catch (Throwable ignore) {
        }
        int[] size = GleapCaptureGeometry.encoderSize(areaWidth, areaHeight, GleapCaptureGeometry.RECORDING_MAX_EDGE, alignment);
        if (video == null) {
            return size;
        }
        int[] limits = {GleapCaptureGeometry.RECORDING_MAX_EDGE, 960, 720, 640};
        for (int limit : limits) {
            for (int align : new int[]{alignment, 16}) {
                int[] candidate = GleapCaptureGeometry.encoderSize(areaWidth, areaHeight, limit, align);
                try {
                    if (video.isSizeSupported(candidate[0], candidate[1])) {
                        return candidate;
                    }
                } catch (Throwable ignore) {
                }
            }
        }
        return size;
    }

    private static boolean supports(MediaCodecInfo info, String mimeType) {
        for (String type : info.getSupportedTypes()) {
            if (type.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSoftware(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isSoftwareOnly();
        }
        String name = info.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("omx.google.") || name.startsWith("c2.android.");
    }

    private static String safeName(MediaCodec encoder) {
        try {
            return encoder.getName();
        } catch (Throwable error) {
            return "";
        }
    }

    // The hardware canvas draws garbage on the encoder surface of some devices.
    private static boolean prefersSoftwareCanvas() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.ROOT) : "";
        if (manufacturer.contains("xiaomi") || manufacturer.contains("motorola")) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && "T606".equalsIgnoreCase(Build.SOC_MODEL);
    }

    private void encode(GleapWindowCapture.Frame frame) {
        if (inputSurface == null || !frame.hasContent()) {
            return;
        }
        requestKeyFrameIfDue();
        Canvas canvas = lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawColor(Color.BLACK);
            float[] box = GleapCaptureGeometry.letterbox(frame.width, frame.height, outputWidth, outputHeight);
            frame.draw(canvas, box[0], box[1], box[2] / frame.width);
        } finally {
            long postedAt = nowUs();
            inputSurface.unlockCanvasAndPost(canvas);
            submittedAt.addLast(postedAt);
            while (submittedAt.size() > MAX_PENDING_TIMESTAMPS) {
                submittedAt.pollFirst();
            }
        }
        drain(false);
    }

    private Canvas lockCanvas() {
        if (!softwareCanvas) {
            try {
                return inputSurface.lockHardwareCanvas();
            } catch (Throwable error) {
                GleapLog.w("The hardware canvas is not available for the recording", error);
                softwareCanvas = true;
            }
        }
        try {
            return inputSurface.lockCanvas(null);
        } catch (Throwable error) {
            GleapLog.w("Could not draw a recording frame", error);
            return null;
        }
    }

    // A key frame at least every 2 s of video, also at 2 fps.
    private void requestKeyFrameIfDue() {
        if (keyFrameRequested || lastKeyFrameUs == Long.MIN_VALUE || lastTimeUs < 0) {
            return;
        }
        // Asked for one frame ahead: the frame drawn now comes out about one interval later.
        long frameIntervalUs = previousTimeUs >= 0 ? Math.max(0, lastTimeUs - previousTimeUs) : 0;
        if (lastTimeUs + frameIntervalUs - lastKeyFrameUs < KEY_FRAME_INTERVAL_US) {
            return;
        }
        try {
            Bundle parameters = new Bundle();
            parameters.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            codec.setParameters(parameters);
            keyFrameRequested = true;
        } catch (Throwable ignore) {
        }
    }

    private void drain(boolean endOfStream) {
        long deadline = SystemClock.uptimeMillis() + END_OF_STREAM_TIMEOUT_MS;
        while (true) {
            int index = codec.dequeueOutputBuffer(bufferInfo, endOfStream ? DRAIN_TIMEOUT_US : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream || SystemClock.uptimeMillis() > deadline) {
                    return;
                }
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                }
                continue;
            }
            if (index < 0) {
                continue;
            }
            ByteBuffer data = codec.getOutputBuffer(index);
            boolean codecConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            if (data != null && bufferInfo.size > 0 && !codecConfig && muxerStarted) {
                long timeUs = videoTimeUs(bufferInfo.presentationTimeUs);
                bufferInfo.presentationTimeUs = timeUs;
                data.position(bufferInfo.offset);
                data.limit(bufferInfo.offset + bufferInfo.size);
                muxer.writeSampleData(track, data, bufferInfo);
                framesWritten++;
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                    lastKeyFrameUs = timeUs;
                    keyFrameRequested = false;
                }
            }
            codec.releaseOutputBuffer(index, false);
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                return;
            }
        }
    }

    /**
     * The video time of an encoded frame: its capture time since the first frame, minus the
     * pauses before it; strictly increasing.
     */
    private long videoTimeUs(long encoderTimeUs) {
        Long submitted = submittedAt.pollFirst();
        if (!clockChecked) {
            clockChecked = true;
            // The input surface stamps frames with the monotonic clock; if this device does not,
            // the SDK's own submit times are used.
            ownClock = submitted == null || Math.abs(encoderTimeUs - submitted) > CLOCK_TOLERANCE_US;
        }
        long captureUs = ownClock && submitted != null ? submitted : encoderTimeUs;
        if (firstTimeUs < 0) {
            firstTimeUs = captureUs;
        }
        long pausedUs = 0;
        synchronized (pauses) {
            for (long[] pause : pauses) {
                if (pause[1] <= captureUs && pause[0] >= firstTimeUs) {
                    pausedUs += pause[1] - pause[0];
                }
            }
        }
        long timeUs = Math.max(0, captureUs - firstTimeUs - pausedUs);
        if (timeUs <= lastTimeUs) {
            timeUs = lastTimeUs + 1;
        }
        previousTimeUs = lastTimeUs;
        lastTimeUs = timeUs;
        return timeUs;
    }

    private Result finish(long durationMs, Date started, Date endedAt) {
        boolean success = false;
        String error = null;
        try {
            if (codec != null) {
                codec.signalEndOfInputStream();
                drain(true);
            }
        } catch (Throwable drainError) {
            GleapLog.w("Could not finish the recording", drainError);
        }
        releaseEncoder();
        try {
            if (muxer != null && muxerStarted && framesWritten > 0) {
                muxer.stop();
                success = true;
            } else {
                error = "No frame was recorded.";
            }
        } catch (Throwable stopError) {
            error = "The recording could not be finished.";
            GleapLog.w(error, stopError);
        }
        if (muxer != null) {
            try {
                muxer.release();
            } catch (Throwable ignore) {
            }
            muxer = null;
        }
        pool.clear();
        if (!success) {
            deleteFile();
        }
        return new Result(file, success, durationMs, outputWidth, outputHeight, started, endedAt, error,
                !success && framesWritten == 0);
    }

    private void releaseEncoder() {
        released = true;
        if (codec != null) {
            try {
                codec.stop();
            } catch (Throwable ignore) {
            }
            try {
                codec.release();
            } catch (Throwable ignore) {
            }
            codec = null;
        }
        if (inputSurface != null) {
            try {
                inputSurface.release();
            } catch (Throwable ignore) {
            }
            inputSurface = null;
        }
    }

    private void deleteFile() {
        if (file.exists() && !file.delete()) {
            GleapLog.w("Could not delete the recording");
        }
    }
}
