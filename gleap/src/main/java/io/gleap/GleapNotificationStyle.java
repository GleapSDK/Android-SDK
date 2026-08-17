package io.gleap;

import static io.gleap.GleapHelper.convertDpToPixel;

import android.app.Activity;
import android.graphics.Color;
import android.icu.text.RelativeDateTimeFormatter;
import android.icu.util.ULocale;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Colors, radii and the relative-time label for the in-app notification cards.
 * These are the same values the web widget derives from the flow config in
 * injectStyledCSS, so a dark-themed project gets dark cards on every platform,
 * independent of the OS appearance.
 */
class GleapNotificationStyle {

    static int backgroundColor() {
        try {
            return Color.parseColor(GleapConfig.getInstance().getBackgroundColor());
        } catch (Exception exp) {
            return Color.WHITE;
        }
    }

    // YIQ >= 160 reads as a light background — the same threshold the web
    // widget's calculateContrast uses.
    static boolean isDarkTheme() {
        int background = backgroundColor();
        double yiq = ((Color.red(background) * 299d) + (Color.green(background) * 587d) + (Color.blue(background) * 114d)) / 1000d;
        return yiq < 160d;
    }

    static int contrastColor() {
        return isDarkTheme() ? Color.WHITE : Color.BLACK;
    }

    // Shifts every channel by `amount`, clamped — mirrors the web widget's
    // calculateShadeColor, which derives the muted text color from the
    // background.
    static int shadeOfColor(int color, int amount) {
        int red = Math.max(0, Math.min(255, Color.red(color) + amount));
        int green = Math.max(0, Math.min(255, Color.green(color) + amount));
        int blue = Math.max(0, Math.min(255, Color.blue(color) + amount));
        return Color.rgb(red, green, blue);
    }

    static int subTextColor() {
        return shadeOfColor(backgroundColor(), isDarkTheme() ? 100 : -120);
    }

    // A drop shadow alone cannot separate a dark card from a dark page, so the
    // card also carries a hairline in the direction the theme needs.
    static int hairlineColor() {
        if (isDarkTheme()) {
            return Color.argb(26, 255, 255, 255);
        }
        return Color.argb(10, 0, 0, 0);
    }

    // The card corner radius, derived from the project's border radius setting
    // exactly like the web widget's containerRadius.
    static int containerRadiusPx(Activity activity) {
        int containerRadius = Math.round(GleapConfig.getInstance().getBorderRadius() * 0.8f);
        return convertDpToPixel(containerRadius, activity);
    }

    // The bot's avatar is a rounded rectangle rather than a circle — the same
    // shape the dashboard and the messenger give it. Derived from the project's
    // radius so a squared-off widget theme keeps squared-off marks; 7dp at the
    // default 20 on the 32dp notification avatar.
    static int botAvatarRadiusPx(Activity activity, int avatarSizeDp) {
        int formItemRadius = Math.round(GleapConfig.getInstance().getBorderRadius() * 0.4f);
        int radius = Math.max(2, Math.round((formItemRadius * avatarSizeDp) / 36f));
        return convertDpToPixel(radius, activity);
    }

    // The stack (and with it every card) spans the same width on every device:
    // 90% of the smaller screen dimension, capped at 320dp — matching the web
    // widget and the iOS SDK.
    static int stackWidthPx(Activity activity) {
        try {
            android.util.DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
            int smallerDimension = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
            return Math.min((int) (smallerDimension * 0.9f), convertDpToPixel(320, activity));
        } catch (Exception exp) {
            return convertDpToPixel(320, activity);
        }
    }

    // The stack frame's one fixed height: tall enough for any expanded stack
    // (4 news-sized cards with gaps fit comfortably), so it never needs to be
    // resized when notifications come and go — resizing would re-anchor the
    // bottom-pinned cards mid-animation.
    static int stackFrameHeightPx(Activity activity) {
        try {
            android.util.DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
            return Math.max(displayMetrics.heightPixels, convertDpToPixel(1200, activity));
        } catch (Exception exp) {
            return convertDpToPixel(1200, activity);
        }
    }

    private static Date parseIsoDate(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }

        String[] patterns = new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                return format.parse(value);
            } catch (Exception exp) {
            }
        }
        return null;
    }

    /**
     * "now" / "5 minutes ago" label for a notification's age, localized through
     * the platform's ICU formatter. Returns null whenever a truthful label can't
     * be produced (no timestamp, an unparsable one, or an OS without the
     * formatter), so callers drop the label instead of printing a placeholder.
     *
     * The age is taken from sendAt rather than createdAt: a scheduled outbound
     * is written to the database long before it is delivered, and its creation
     * time would surface as an hours-old message the user just received.
     */
    static String relativeTimeLabel(String sendAt, String createdAt) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return null;
            }

            Date date = parseIsoDate(sendAt);
            if (date == null) {
                date = parseIsoDate(createdAt);
            }
            if (date == null) {
                return null;
            }

            RelativeDateTimeFormatter formatter;
            try {
                // The widget's language override, falling back to the device locale.
                String language = GleapConfig.getInstance().getLanguage();
                if (language != null && language.length() > 0) {
                    formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLanguageTag(language.replace("_", "-")));
                } else {
                    formatter = RelativeDateTimeFormatter.getInstance();
                }
            } catch (Exception exp) {
                formatter = RelativeDateTimeFormatter.getInstance();
            }

            // Clamped at 0: a notification scheduled a few seconds ahead (or a
            // client clock running behind the server's) must never read as
            // "in 1 minute". Under a minute collapses to "now" rather than
            // ticking "9 seconds ago".
            double seconds = Math.min(0d, (date.getTime() - System.currentTimeMillis()) / 1000d);
            if (seconds > -60d) {
                return formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW);
            }

            // Promote the value to the largest unit it still fills, so 90
            // minutes reads as "1 hour", not "90 minutes".
            double duration = Math.abs(seconds) / 60d;
            double[] amounts = new double[]{60d, 24d, 7d, 4.34524d, 12d, Double.POSITIVE_INFINITY};
            RelativeDateTimeFormatter.RelativeUnit[] units = new RelativeDateTimeFormatter.RelativeUnit[]{
                    RelativeDateTimeFormatter.RelativeUnit.MINUTES,
                    RelativeDateTimeFormatter.RelativeUnit.HOURS,
                    RelativeDateTimeFormatter.RelativeUnit.DAYS,
                    RelativeDateTimeFormatter.RelativeUnit.WEEKS,
                    RelativeDateTimeFormatter.RelativeUnit.MONTHS,
                    RelativeDateTimeFormatter.RelativeUnit.YEARS
            };
            for (int i = 0; i < amounts.length; i++) {
                if (duration < amounts[i]) {
                    return formatter.format(Math.round(duration), RelativeDateTimeFormatter.Direction.LAST, units[i]);
                }
                duration /= amounts[i];
            }
        } catch (Exception exp) {
        }

        return null;
    }
}
