package io.gleap;

import java.util.Locale;

/**
 * The size and timing math of screenshots and recordings (no Android types, so it is unit tested).
 */
final class GleapCaptureGeometry {
    // Screenshots are sent with at most this long edge.
    static final int SCREENSHOT_MAX_EDGE = 2560;
    // Recordings are encoded with at most this long edge.
    static final int RECORDING_MAX_EDGE = 1280;

    // Recording frame rate: 4 fps, between 2 and 8 fps depending on what a frame costs.
    static final long TARGET_FRAME_INTERVAL_MS = 250;
    static final long MIN_FRAME_INTERVAL_MS = 125;
    static final long MAX_FRAME_INTERVAL_MS = 500;
    // A frame may take at most this share of the frame interval on the main thread.
    static final double MAIN_THREAD_BUDGET = 0.3;
    // Below this share a faster frame rate is tried.
    static final double MAIN_THREAD_SPARE = 0.08;

    private GleapCaptureGeometry() {
    }

    /**
     * The scale that makes the long edge of {@code width x height} at most {@code maxEdge}; never
     * more than 1 (no upscaling).
     */
    static float scaleForLongEdge(int width, int height, int maxEdge) {
        int longEdge = Math.max(width, height);
        if (longEdge <= 0 || longEdge <= maxEdge) {
            return 1f;
        }
        return maxEdge / (float) longEdge;
    }

    /**
     * {@code width x height} scaled so the long edge is at most {@code maxEdge}, at least 1 x 1.
     */
    static int[] fitLongEdge(int width, int height, int maxEdge) {
        float scale = scaleForLongEdge(width, height, maxEdge);
        return new int[]{Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale))};
    }

    /**
     * The encoder size for a recording of {@code width x height}: the long edge at most
     * {@code maxEdge}, both dimensions multiples of {@code alignment} (at least 2: H.264 needs
     * even sizes) and at least one alignment step.
     */
    static int[] encoderSize(int width, int height, int maxEdge, int alignment) {
        int align = Math.max(2, alignment);
        float scale = scaleForLongEdge(width, height, maxEdge);
        // A hair above the product, so float rounding never costs a whole alignment step.
        int scaledWidth = (int) Math.floor(Math.max(1, width) * (double) scale + 0.001);
        int scaledHeight = (int) Math.floor(Math.max(1, height) * (double) scale + 0.001);
        return new int[]{alignDown(scaledWidth, align), alignDown(scaledHeight, align)};
    }

    private static int alignDown(int value, int alignment) {
        return Math.max(alignment, value - value % alignment);
    }

    /**
     * Where a {@code sourceWidth x sourceHeight} frame goes in a {@code targetWidth x targetHeight}
     * output: scaled to fit and centered, with black bars on the other sides (letterbox).
     *
     * @return {left, top, width, height}
     */
    static float[] letterbox(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return new float[]{0, 0, Math.max(0, targetWidth), Math.max(0, targetHeight)};
        }
        float scale = Math.min(targetWidth / (float) sourceWidth, targetHeight / (float) sourceHeight);
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        return new float[]{(targetWidth - width) / 2f, (targetHeight - height) / 2f, width, height};
    }

    /**
     * The next recording frame interval: slower while a frame costs more than
     * {@link #MAIN_THREAD_BUDGET} of the interval on the main thread, faster while it costs less
     * than {@link #MAIN_THREAD_SPARE} and the frames keep up, back towards 4 fps otherwise.
     *
     * @param mainThreadMs what the last frame cost on the main thread
     * @param keptUp       whether the last frame was encoded before the next one was due
     */
    static long nextFrameInterval(long currentMs, long mainThreadMs, boolean keptUp) {
        long interval = clampInterval(currentMs);
        if (mainThreadMs > interval * MAIN_THREAD_BUDGET || !keptUp) {
            return clampInterval(Math.max(interval + 50, Math.round(interval * 1.5)));
        }
        if (mainThreadMs < interval * MAIN_THREAD_SPARE) {
            return clampInterval(interval - 25);
        }
        if (interval > TARGET_FRAME_INTERVAL_MS) {
            return clampInterval(interval - 25);
        }
        return interval;
    }

    /**
     * When the next frame may start: the frame interval, or later when frames cost more than
     * {@link #MAIN_THREAD_BUDGET} of the time on the main thread (frames are then skipped, also
     * below 2 fps).
     */
    static long nextFrameDelay(long intervalMs, long mainThreadMs) {
        long budgetDelay = (long) Math.ceil(Math.max(0, mainThreadMs) / MAIN_THREAD_BUDGET);
        return Math.max(clampInterval(intervalMs), budgetDelay);
    }

    private static long clampInterval(long intervalMs) {
        return Math.max(MIN_FRAME_INTERVAL_MS, Math.min(MAX_FRAME_INTERVAL_MS, intervalMs));
    }

    /**
     * mm:ss for the recording timer.
     */
    static String formatClock(long seconds) {
        long safe = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }

    /**
     * The recording limit: the request's value clamped to 5..180 s, 60 s when missing.
     */
    static int clampDuration(int requestedSec) {
        if (requestedSec <= 0) {
            return GleapCapture.DEFAULT_RECORDING_SEC;
        }
        return Math.max(GleapCapture.MIN_RECORDING_SEC, Math.min(GleapCapture.MAX_RECORDING_SEC, requestedSec));
    }
}
