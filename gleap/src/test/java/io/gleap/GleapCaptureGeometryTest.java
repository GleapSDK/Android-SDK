package io.gleap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The sizes of screenshots and recordings: the long edge limits, the even (aligned) encoder sizes
 * H.264 needs, the letterbox after a rotation, and the frame rate limits.
 */
public class GleapCaptureGeometryTest {
    @Test
    public void screenshotsKeepTheirSizeUpToTheLimitAndShrinkAboveIt() {
        assertArrayEquals(new int[]{1080, 2400}, GleapCaptureGeometry.fitLongEdge(1080, 2400, 2560));
        assertArrayEquals(new int[]{2560, 1600}, GleapCaptureGeometry.fitLongEdge(2560, 1600, 2560));
        // A 12.9" tablet: 2732 x 2048 → long edge 2560.
        assertArrayEquals(new int[]{2560, 1919}, GleapCaptureGeometry.fitLongEdge(2732, 2048, 2560));
        assertArrayEquals(new int[]{1, 1}, GleapCaptureGeometry.fitLongEdge(0, 0, 2560));
    }

    @Test
    public void encoderSizesAreEvenAlignedAndAtMost1280() {
        int[] phone = GleapCaptureGeometry.encoderSize(1080, 2400, 1280, 2);
        assertArrayEquals(new int[]{576, 1280}, phone);

        // Odd results are rounded down to even sizes.
        int[] odd = GleapCaptureGeometry.encoderSize(1081, 2399, 1280, 2);
        assertEquals(0, odd[0] % 2);
        assertEquals(0, odd[1] % 2);
        assertTrue(odd[1] <= 1280);

        // An encoder that needs multiples of 16.
        int[] aligned = GleapCaptureGeometry.encoderSize(1080, 2400, 1280, 16);
        assertArrayEquals(new int[]{576, 1280}, aligned);
        int[] tablet = GleapCaptureGeometry.encoderSize(2560, 1600, 1280, 16);
        assertArrayEquals(new int[]{1280, 800}, tablet);
        int[] foldable = GleapCaptureGeometry.encoderSize(2208, 1840, 1280, 16);
        assertEquals(0, foldable[0] % 16);
        assertEquals(0, foldable[1] % 16);
        assertTrue(foldable[0] <= 1280 && foldable[1] <= 1280);

        // Small windows are never upscaled and never 0.
        assertArrayEquals(new int[]{640, 360}, GleapCaptureGeometry.encoderSize(640, 360, 1280, 2));
        assertArrayEquals(new int[]{16, 16}, GleapCaptureGeometry.encoderSize(3, 3, 1280, 16));
    }

    @Test
    public void aRotatedFrameIsLetterboxedIntoTheFixedOutput() {
        // Portrait recording (576 x 1280), the phone turned to landscape (2400 x 1080).
        float[] box = GleapCaptureGeometry.letterbox(2400, 1080, 576, 1280);
        assertEquals(576f, box[2], 0.01f);
        assertEquals(259.2f, box[3], 0.01f);
        assertEquals(0f, box[0], 0.01f);
        // Centered vertically.
        assertEquals((1280 - 259.2f) / 2, box[1], 0.01f);

        // Same aspect: the frame fills the output.
        float[] same = GleapCaptureGeometry.letterbox(1080, 2400, 576, 1280);
        assertArrayEquals(new float[]{0f, 0f, 576f, 1280f}, same, 0.01f);

        // A narrower window (multi-window) gets bars left and right.
        float[] narrow = GleapCaptureGeometry.letterbox(540, 2400, 576, 1280);
        assertEquals(1280f, narrow[3], 0.01f);
        assertEquals((576 - narrow[2]) / 2, narrow[0], 0.01f);
    }

    @Test
    public void theFrameRateStaysBetweenTwoAndEightFramesASecond() {
        long interval = GleapCaptureGeometry.TARGET_FRAME_INTERVAL_MS;
        // Expensive frames (more than 30 % of the interval on the main thread) slow down to 2 fps.
        for (int i = 0; i < 10; i++) {
            interval = GleapCaptureGeometry.nextFrameInterval(interval, 200, true);
        }
        assertEquals(GleapCaptureGeometry.MAX_FRAME_INTERVAL_MS, interval);

        // Frames that do not keep up slow down as well.
        assertTrue(GleapCaptureGeometry.nextFrameInterval(250, 10, false) > 250);

        // Cheap frames speed up to at most 8 fps.
        interval = GleapCaptureGeometry.TARGET_FRAME_INTERVAL_MS;
        for (int i = 0; i < 20; i++) {
            interval = GleapCaptureGeometry.nextFrameInterval(interval, 1, true);
        }
        assertEquals(GleapCaptureGeometry.MIN_FRAME_INTERVAL_MS, interval);

        // Moderate cost after a slow phase: back towards 4 fps.
        assertTrue(GleapCaptureGeometry.nextFrameInterval(500, 60, true) < 500);
        assertEquals(250, GleapCaptureGeometry.nextFrameInterval(250, 40, true));
    }

    @Test
    public void theTimerAndTheDurationLimit() {
        assertEquals("00:00", GleapCaptureGeometry.formatClock(0));
        assertEquals("01:05", GleapCaptureGeometry.formatClock(65));
        assertEquals("03:00", GleapCaptureGeometry.formatClock(180));

        assertEquals(60, GleapCaptureGeometry.clampDuration(0));
        assertEquals(5, GleapCaptureGeometry.clampDuration(1));
        assertEquals(180, GleapCaptureGeometry.clampDuration(600));
        assertEquals(45, GleapCaptureGeometry.clampDuration(45));
    }
}
