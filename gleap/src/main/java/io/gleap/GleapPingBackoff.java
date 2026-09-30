package io.gleap;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * When the next ping may go out after failed ones (429, 5xx, other errors, network errors): 3 s,
 * then 6, 12, 24, 48 and at most 60 s after the failure, each ±20 % so devices do not retry in
 * step. When the server asks for a longer wait with Retry-After (seconds or an HTTP date), that
 * applies, up to 5 minutes. A delivered ping starts over. Thread-safe.
 */
final class GleapPingBackoff {
    static final long FIRST_DELAY_MS = 3000;
    static final long MAX_DELAY_MS = 60000;
    static final double JITTER = 0.2;
    static final long MAX_RETRY_AFTER_MS = 5 * 60 * 1000;

    // IMF-fixdate (the one servers send), then the obsolete RFC 850 and asctime formats.
    private static final String[] HTTP_DATE_PATTERNS = {
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy",
    };

    private int failures;
    // Before this time (elapsed realtime) no ping goes out; 0 when there is no backoff.
    private long retryAt;

    /**
     * A ping failed at {@code now}.
     *
     * @param retryAfterMs the server's Retry-After in ms, or a negative value without one
     * @param random       uniformly distributed in [0, 1), for the jitter
     * @return the delay until the next ping may go out
     */
    synchronized long onFailure(long now, long retryAfterMs, double random) {
        failures++;
        long delay = delay(failures, random);
        if (retryAfterMs > 0) {
            delay = Math.max(delay, Math.min(retryAfterMs, MAX_RETRY_AFTER_MS));
        }
        retryAt = now + delay;
        return delay;
    }

    /**
     * A ping was delivered: the next failure starts over at 3 s.
     */
    synchronized void onSuccess() {
        failures = 0;
        retryAt = 0;
    }

    /**
     * @return how long the next ping still has to wait at {@code now}, 0 when it may go out
     */
    synchronized long remaining(long now) {
        return retryAt > now ? retryAt - now : 0;
    }

    synchronized int failures() {
        return failures;
    }

    /**
     * Whether a failed ping is worth sending again: 408, 429 or a 5xx. Other error answers mean
     * the server will not take these events.
     */
    static boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    /**
     * The backoff after {@code failures} failed pings in a row: 3 s doubled per failure up to
     * 60 s, times a random factor between 0.8 and 1.2, never more than 60 s.
     */
    static long delay(int failures, double random) {
        int doublings = Math.min(Math.max(failures - 1, 0), 5);
        long base = Math.min(FIRST_DELAY_MS << doublings, MAX_DELAY_MS);
        double factor = 1 - JITTER + 2 * JITTER * Math.min(Math.max(random, 0), 1);
        return Math.min(Math.round(base * factor), MAX_DELAY_MS);
    }

    /**
     * Reads a Retry-After header: delay-seconds or an HTTP date.
     *
     * @param nowWallMs the current time (System.currentTimeMillis), for a date
     * @return the wait in ms (0 for a date in the past), or -1 without a valid value
     */
    static long parseRetryAfterMs(String value, long nowWallMs) {
        if (value == null) {
            return -1;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        if (isDigits(trimmed)) {
            // More than 9 digits is longer than the cap anyway (and could overflow).
            return trimmed.length() > 9 ? MAX_RETRY_AFTER_MS : Long.parseLong(trimmed) * 1000;
        }
        for (String pattern : HTTP_DATE_PATTERNS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            try {
                Date date = format.parse(trimmed);
                if (date != null) {
                    return Math.max(0, date.getTime() - nowWallMs);
                }
            } catch (ParseException ignore) {
            }
        }
        return -1;
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
