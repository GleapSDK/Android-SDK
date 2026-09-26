package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Applies the widget color scheme (dark / light) to the flow config.
 * <p>
 * The widget has one background color, set in the dashboard. Every surface
 * derives dark vs. light from it (YIQ below 160 = dark, white text), so a
 * color scheme only swaps that background: when the active scheme doesn't
 * match the dashboard background, the light or dark background is used
 * instead. Primary, header and button colors stay unchanged.
 * <p>
 * The scheme comes from {@link Gleap#setColorScheme(String, String, String)}
 * and falls back to the dashboard's flowConfig.colorScheme. "auto" follows the
 * app's night mode — read from the current activity, so
 * AppCompatDelegate.setDefaultNightMode is respected — and switches live.
 */
class GleapThemeHelper {
    static final String COLOR_SCHEME_DEFAULT = "default";
    static final String COLOR_SCHEME_AUTO = "auto";
    static final String COLOR_SCHEME_LIGHT = "light";
    static final String COLOR_SCHEME_DARK = "dark";

    static final String DEFAULT_LIGHT_BACKGROUND = "#ffffff";
    static final String DEFAULT_DARK_BACKGROUND = "#18181b";

    private static GleapThemeHelper instance;

    // Runtime override (Gleap.setColorScheme). null = use the dashboard setting.
    private volatile String colorScheme = null;
    private volatile String lightBackgroundColor = null;
    private volatile String darkBackgroundColor = null;

    // The app's night mode as last seen. null = not read yet.
    private volatile Boolean nightMode = null;
    // The background the UI was last rendered with, to detect a change.
    private volatile String appliedBackgroundColor = null;

    private Application application;
    private boolean started = false;

    private GleapThemeHelper() {
    }

    static synchronized GleapThemeHelper getInstance() {
        if (instance == null) {
            instance = new GleapThemeHelper();
        }
        return instance;
    }

    /**
     * Sets the runtime color scheme. "default" or null removes the override, so
     * the dashboard setting applies again. Invalid background colors are ignored.
     */
    void setColorScheme(String colorScheme, String lightBackgroundColor, String darkBackgroundColor) {
        this.colorScheme = isScheme(colorScheme) ? colorScheme.toLowerCase(Locale.ROOT) : null;
        this.lightBackgroundColor = normalizeHexColor(lightBackgroundColor);
        this.darkBackgroundColor = normalizeHexColor(darkBackgroundColor);

        // The night mode may have changed while nothing was listening yet.
        this.nightMode = null;
        notifyIfChanged();
    }

    /**
     * Starts following the app's night mode. Safe to call more than once.
     */
    synchronized void start(Application application) {
        if (started || application == null) {
            return;
        }
        started = true;
        this.application = application;

        try {
            application.registerComponentCallbacks(new ComponentCallbacks() {
                @Override
                public void onConfigurationChanged(@NonNull Configuration configuration) {
                    // The application configuration doesn't carry an
                    // AppCompat night mode override — prefer the activity,
                    // once it received the new configuration too.
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            checkNightMode(null);
                        }
                    });
                }

                @Override
                public void onLowMemory() {
                }
            });

            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
                }

                @Override
                public void onActivityStarted(@NonNull Activity activity) {
                    checkNightMode(activity);
                }

                @Override
                public void onActivityResumed(@NonNull Activity activity) {
                    checkNightMode(activity);
                }

                @Override
                public void onActivityPaused(@NonNull Activity activity) {
                }

                @Override
                public void onActivityStopped(@NonNull Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
                }

                @Override
                public void onActivityDestroyed(@NonNull Activity activity) {
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    /**
     * Re-reads the night mode (from the given context, else the current
     * activity, else the application) and re-themes the UI when the
     * resulting background changed.
     */
    void checkNightMode(Context context) {
        Boolean previous = this.nightMode;
        boolean current = readNightMode(context);
        this.nightMode = current;
        if (previous == null || previous != current) {
            notifyIfChanged();
        }
    }

    /**
     * The background color the widget renders with for the given flow config.
     */
    String getBackgroundColor(JSONObject flowConfig) {
        return resolveBackgroundColor(flowConfig, colorScheme, lightBackgroundColor, darkBackgroundColor, isNightMode());
    }

    /**
     * The flow config as sent to the widget: a copy with the themed background
     * color, or the given config itself when nothing changes. Never mutates it.
     */
    JSONObject applyToFlowConfig(JSONObject flowConfig) {
        return applyToFlowConfig(flowConfig, colorScheme, lightBackgroundColor, darkBackgroundColor, isNightMode());
    }

    private boolean isNightMode() {
        Boolean nightMode = this.nightMode;
        if (nightMode == null) {
            nightMode = readNightMode(null);
            this.nightMode = nightMode;
        }
        return nightMode;
    }

    private boolean readNightMode(Context context) {
        try {
            if (context == null) {
                context = ActivityUtil.getCurrentActivity();
            }
            if (context == null) {
                context = application;
            }
            if (context == null) {
                return false;
            }
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return uiMode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Error | Exception ignore) {
            return false;
        }
    }

    /**
     * Re-renders the notifications, the modal and the open widget when the
     * themed background differs from the one they were rendered with.
     */
    private void notifyIfChanged() {
        try {
            JSONObject plainConfig = GleapConfig.getInstance().getPlainConfig();
            if (plainConfig == null) {
                // Nothing rendered yet — the config load picks the scheme up.
                return;
            }

            String backgroundColor = GleapConfig.getInstance().getBackgroundColor();
            if (backgroundColor == null || backgroundColor.equals(appliedBackgroundColor)) {
                return;
            }
            appliedBackgroundColor = backgroundColor;

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        GleapInvisibleActivityManger.getInstance().refreshColorScheme();
                        GleapMainActivity.refreshColorScheme();
                    } catch (Error | Exception ignore) {
                    }
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    // ------------------------------------------------------------------
    // Swap rule — pure functions, shared with the unit tests.
    // ------------------------------------------------------------------

    static boolean isScheme(String colorScheme) {
        if (colorScheme == null) {
            return false;
        }
        String scheme = colorScheme.toLowerCase(Locale.ROOT);
        return scheme.equals(COLOR_SCHEME_AUTO) || scheme.equals(COLOR_SCHEME_LIGHT) || scheme.equals(COLOR_SCHEME_DARK);
    }

    /**
     * The effective scheme: the runtime scheme if it is auto, light or dark, else
     * the dashboard's flowConfig.colorScheme, else "default".
     */
    static String effectiveColorScheme(JSONObject flowConfig, String runtimeColorScheme) {
        if (isScheme(runtimeColorScheme)) {
            return runtimeColorScheme.toLowerCase(Locale.ROOT);
        }
        String configured = flowConfig != null ? flowConfig.optString("colorScheme", null) : null;
        if (isScheme(configured)) {
            return configured.toLowerCase(Locale.ROOT);
        }
        return COLOR_SCHEME_DEFAULT;
    }

    /**
     * The runtime color if valid, else the flow config field if valid, else the default.
     */
    static String effectiveBackgroundColor(JSONObject flowConfig, String key, String runtimeColor, String defaultColor) {
        String color = normalizeHexColor(runtimeColor);
        if (color == null && flowConfig != null) {
            color = normalizeHexColor(flowConfig.optString(key, null));
        }
        return color != null ? color : defaultColor;
    }

    static String configuredBackgroundColor(JSONObject flowConfig) {
        String backgroundColor = flowConfig != null ? flowConfig.optString("backgroundColor", "") : "";
        return backgroundColor.isEmpty() ? DEFAULT_LIGHT_BACKGROUND : backgroundColor;
    }

    /**
     * Resolves the background color for the given state:
     * <pre>
     * active = effective scheme ("auto" resolved via nightMode); "default" => unchanged
     * unchanged when (active == dark) == isDark(configured background)
     * else the dark or light background
     * </pre>
     */
    static String resolveBackgroundColor(JSONObject flowConfig, String runtimeColorScheme, String runtimeLightBackgroundColor, String runtimeDarkBackgroundColor, boolean nightMode) {
        String configuredBackgroundColor = configuredBackgroundColor(flowConfig);

        String scheme = effectiveColorScheme(flowConfig, runtimeColorScheme);
        if (scheme.equals(COLOR_SCHEME_DEFAULT)) {
            return configuredBackgroundColor;
        }

        boolean dark = scheme.equals(COLOR_SCHEME_AUTO) ? nightMode : scheme.equals(COLOR_SCHEME_DARK);
        if (dark == isDarkColor(configuredBackgroundColor)) {
            // The dashboard color already fits — keep the brand look.
            return configuredBackgroundColor;
        }

        if (dark) {
            return effectiveBackgroundColor(flowConfig, "darkBackgroundColor", runtimeDarkBackgroundColor, DEFAULT_DARK_BACKGROUND);
        }
        return effectiveBackgroundColor(flowConfig, "lightBackgroundColor", runtimeLightBackgroundColor, DEFAULT_LIGHT_BACKGROUND);
    }

    static JSONObject applyToFlowConfig(JSONObject flowConfig, String runtimeColorScheme, String runtimeLightBackgroundColor, String runtimeDarkBackgroundColor, boolean nightMode) {
        if (flowConfig == null) {
            return null;
        }

        String backgroundColor = resolveBackgroundColor(flowConfig, runtimeColorScheme, runtimeLightBackgroundColor, runtimeDarkBackgroundColor, nightMode);
        if (backgroundColor.equals(configuredBackgroundColor(flowConfig))) {
            return flowConfig;
        }

        try {
            JSONObject themedFlowConfig = new JSONObject(flowConfig.toString());
            themedFlowConfig.put("backgroundColor", backgroundColor);
            return themedFlowConfig;
        } catch (Exception ignore) {
            return flowConfig;
        }
    }

    /**
     * Whether the widget renders the color as dark — the same YIQ threshold as
     * the widget's calculateContrast. Unparsable colors count as light.
     */
    static boolean isDarkColor(String color) {
        int[] rgb = parseHexColor(color);
        if (rgb == null) {
            return false;
        }
        double yiq = ((rgb[0] * 299d) + (rgb[1] * 587d) + (rgb[2] * 114d)) / 1000d;
        return yiq < 160d;
    }

    /**
     * Returns #rrggbb for #rgb or #rrggbb input, else null.
     */
    static String normalizeHexColor(String color) {
        if (color == null) {
            return null;
        }
        String value = color.trim();
        if (value.length() != 4 && value.length() != 7) {
            return null;
        }
        int[] rgb = parseHexColor(value);
        if (rgb == null) {
            return null;
        }
        return String.format(Locale.ROOT, "#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    // Parses #rgb, #rrggbb and #rrggbbaa (alpha ignored) into {r, g, b}.
    private static int[] parseHexColor(String color) {
        if (color == null) {
            return null;
        }
        String value = color.trim();
        if (!value.startsWith("#")) {
            return null;
        }
        String hex = value.substring(1);
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                return null;
            }
        }

        if (hex.length() == 3) {
            int[] rgb = new int[3];
            for (int i = 0; i < 3; i++) {
                int digit = Character.digit(hex.charAt(i), 16);
                rgb[i] = digit * 16 + digit;
            }
            return rgb;
        }

        if (hex.length() == 6 || hex.length() == 8) {
            return new int[]{
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
            };
        }

        return null;
    }
}
