package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Applies the widget color scheme (dark / light) to the flow config.
 * <p>
 * The flow config's base colors (headerColor, headerColor2, headerColor3,
 * color, backgroundColor) are the LIGHT palette. The dashboard saves a DARK
 * palette (darkHeaderColor, darkHeaderColor2, darkHeaderColor3, darkColor,
 * darkBackgroundColor) when dark mode is enabled. In dark mode each valid dark
 * value replaces its base one; the SDK does no color math of its own. Without
 * any dark color (or a runtime dark background) the widget does not go dark
 * and keeps the dashboard colors.
 * <p>
 * The logo, header background image and composer glow have their own dark
 * fields too (darkLogo, darkBgImage, darkAurora), copied from the light ones
 * when dark mode is enabled in the dashboard. In dark mode a present dark field
 * replaces its base one as-is ("" = none in dark mode); an absent one (a config
 * saved before these fields existed) keeps the base value.
 * <p>
 * Theming only happens when dark / light mode is enabled in the dashboard
 * (flowConfig.colorScheme "auto", "light" or "dark"). Missing, unknown or
 * "default" means disabled: the widget keeps the dashboard colors, whatever
 * {@link Gleap#setColorScheme(String, String, String)} says. When enabled, the
 * runtime scheme overrides the dashboard's. "auto" follows the app's night
 * mode — read from the current activity, so AppCompatDelegate.setDefaultNightMode
 * is respected — and switches live.
 */
class GleapThemeHelper {
    static final String COLOR_SCHEME_DEFAULT = "default";
    static final String COLOR_SCHEME_AUTO = "auto";
    static final String COLOR_SCHEME_LIGHT = "light";
    static final String COLOR_SCHEME_DARK = "dark";

    static final String DEFAULT_LIGHT_BACKGROUND = "#ffffff";

    // Each base (light) palette key and the dark key that replaces it in dark mode.
    private static final String[][] DARK_PALETTE_KEYS = {
            {"headerColor", "darkHeaderColor"},
            {"headerColor2", "darkHeaderColor2"},
            {"headerColor3", "darkHeaderColor3"},
            {"color", "darkColor"},
            {"backgroundColor", "darkBackgroundColor"}
    };

    // Each base key (logo, header image, composer glow) and the dark key whose
    // value replaces it as-is in dark mode when present — no validation.
    private static final String[][] DARK_ASSET_KEYS = {
            {"logo", "darkLogo"},
            {"bgImage", "darkBgImage"},
            {"aurora", "darkAurora"}
    };

    // Created with the class: getInstance() is called from several threads.
    private static final GleapThemeHelper instance = new GleapThemeHelper();

    // Runtime override (Gleap.setColorScheme). null = use the dashboard setting.
    private volatile String colorScheme = null;
    private volatile String lightBackgroundColor = null;
    private volatile String darkBackgroundColor = null;

    // The app's night mode as last seen. null = not read yet.
    private volatile Boolean nightMode = null;
    // The palette, logo, header image and composer glow the UI was last
    // rendered with, to detect a change.
    private volatile String appliedTheme = null;

    private Application application;
    private boolean started = false;

    private GleapThemeHelper() {
    }

    static GleapThemeHelper getInstance() {
        return instance;
    }

    /**
     * Sets the runtime color scheme. "default" or null removes the override, so
     * the dashboard setting applies again. Invalid background colors are ignored.
     * Has no effect while dark / light mode is disabled in the dashboard.
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
                    GleapMainThread.post(new Runnable() {
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
     * resulting palette changed.
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
     * The themed value of a palette key (backgroundColor, color, headerColor,
     * headerColor2, headerColor3), or null when the scheme doesn't change it.
     */
    String getThemedColor(JSONObject flowConfig, String key) {
        return resolvePalette(flowConfig, colorScheme, lightBackgroundColor, darkBackgroundColor, isNightMode()).get(key);
    }

    /**
     * The themed value of logo, bgImage or aurora — the dark value as-is in
     * dark mode when present — or null when the scheme doesn't change it.
     */
    Object getThemedAsset(JSONObject flowConfig, String key) {
        return resolveDarkAssets(flowConfig, colorScheme, darkBackgroundColor, isNightMode()).get(key);
    }

    /**
     * The flow config as sent to the widget: a copy with the themed colors,
     * logo, header image and composer glow, or the given config itself when
     * nothing changes. Never mutates it.
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
     * themed palette, logo, header image or composer glow differs from what
     * they were rendered with.
     */
    private void notifyIfChanged() {
        try {
            JSONObject plainConfig = GleapConfig.getInstance().getPlainConfig();
            if (plainConfig == null) {
                // Nothing rendered yet — the config load picks the scheme up.
                return;
            }

            GleapConfig config = GleapConfig.getInstance();
            StringBuilder theme = new StringBuilder()
                    .append(config.getBackgroundColor()).append('|').append(config.getColor())
                    .append('|').append(config.getHeaderColor()).append('|').append(config.getHeaderColor2())
                    .append('|').append(config.getHeaderColor3());
            JSONObject themedFlowConfig = config.getThemedFlowConfig();
            for (String[] keys : DARK_ASSET_KEYS) {
                theme.append('|').append(themedFlowConfig != null ? themedFlowConfig.opt(keys[0]) : null);
            }
            if (theme.toString().equals(appliedTheme)) {
                return;
            }
            appliedTheme = theme.toString();

            GleapMainThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        GleapOverlayManager.getInstance().refreshColorScheme();
                        GleapMainActivity.refreshColorScheme();
                    } catch (Error | Exception ignore) {
                    }
                }
            });
        } catch (Error | Exception ignore) {
        }
    }

    // ------------------------------------------------------------------
    // Palette rule — pure functions, shared with the unit tests.
    // ------------------------------------------------------------------

    static boolean isScheme(String colorScheme) {
        if (colorScheme == null) {
            return false;
        }
        String scheme = colorScheme.toLowerCase(Locale.ROOT);
        return scheme.equals(COLOR_SCHEME_AUTO) || scheme.equals(COLOR_SCHEME_LIGHT) || scheme.equals(COLOR_SCHEME_DARK);
    }

    /**
     * The dashboard's flowConfig.colorScheme: "auto", "light" or "dark" when dark /
     * light mode is enabled there, else "default" (missing, unknown or "default").
     */
    static String dashboardColorScheme(JSONObject flowConfig) {
        String configured = flowConfig != null ? flowConfig.optString("colorScheme", null) : null;
        return isScheme(configured) ? configured.toLowerCase(Locale.ROOT) : COLOR_SCHEME_DEFAULT;
    }

    static String configuredBackgroundColor(JSONObject flowConfig) {
        String backgroundColor = flowConfig != null ? flowConfig.optString("backgroundColor", "") : "";
        return backgroundColor.isEmpty() ? DEFAULT_LIGHT_BACKGROUND : backgroundColor;
    }

    // The flow config field as #rrggbb, or null when missing or invalid.
    private static String validColor(JSONObject flowConfig, String key) {
        return flowConfig != null ? normalizeHexColor(flowConfig.optString(key, null)) : null;
    }

    /**
     * Whether there is a dark palette: a valid dark color in the flow config,
     * or a runtime dark background.
     */
    static boolean hasDarkPalette(JSONObject flowConfig, String runtimeDarkBackgroundColor) {
        if (normalizeHexColor(runtimeDarkBackgroundColor) != null) {
            return true;
        }
        for (String[] keys : DARK_PALETTE_KEYS) {
            if (validColor(flowConfig, keys[1]) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The scheme the widget renders with: "default" (the dashboard colors,
     * nothing themed), "light" or "dark". "default" whenever dark / light mode
     * is disabled in the dashboard, even with a runtime scheme. Otherwise the
     * runtime scheme (auto, light or dark) wins over the dashboard's; "auto"
     * resolves via nightMode. Dark needs a dark palette — without dark colors
     * it counts as "default".
     */
    static String activeColorScheme(JSONObject flowConfig, String runtimeColorScheme, String runtimeDarkBackgroundColor, boolean nightMode) {
        String scheme = dashboardColorScheme(flowConfig);
        if (scheme.equals(COLOR_SCHEME_DEFAULT)) {
            return COLOR_SCHEME_DEFAULT;
        }
        if (isScheme(runtimeColorScheme)) {
            scheme = runtimeColorScheme.toLowerCase(Locale.ROOT);
        }

        boolean dark = scheme.equals(COLOR_SCHEME_AUTO) ? nightMode : scheme.equals(COLOR_SCHEME_DARK);
        if (!dark) {
            return COLOR_SCHEME_LIGHT;
        }
        return hasDarkPalette(flowConfig, runtimeDarkBackgroundColor) ? COLOR_SCHEME_DARK : COLOR_SCHEME_DEFAULT;
    }

    /**
     * The palette values the active scheme sets on the flow config:
     * <pre>
     * default => nothing
     * light   => backgroundColor = runtime light background (if set)
     * dark    => each base key = its valid dark value (else the base value stays);
     *            backgroundColor = runtime dark background ?? darkBackgroundColor
     * </pre>
     */
    static Map<String, String> resolvePalette(JSONObject flowConfig, String runtimeColorScheme, String runtimeLightBackgroundColor, String runtimeDarkBackgroundColor, boolean nightMode) {
        Map<String, String> palette = new LinkedHashMap<>();

        String scheme = activeColorScheme(flowConfig, runtimeColorScheme, runtimeDarkBackgroundColor, nightMode);
        if (scheme.equals(COLOR_SCHEME_LIGHT)) {
            String lightBackground = normalizeHexColor(runtimeLightBackgroundColor);
            if (lightBackground != null) {
                palette.put("backgroundColor", lightBackground);
            }
        } else if (scheme.equals(COLOR_SCHEME_DARK)) {
            for (String[] keys : DARK_PALETTE_KEYS) {
                String color = validColor(flowConfig, keys[1]);
                if (color != null) {
                    palette.put(keys[0], color);
                }
            }
            String darkBackground = normalizeHexColor(runtimeDarkBackgroundColor);
            if (darkBackground != null) {
                palette.put("backgroundColor", darkBackground);
            }
        }

        return palette;
    }

    /**
     * The logo, header image and composer glow the active scheme sets on the
     * flow config: in dark mode each present dark key (darkLogo, darkBgImage,
     * darkAurora — not JSONObject.NULL) replaces its base key as-is, "" included;
     * an absent one keeps the base value. Light / default: nothing.
     */
    static Map<String, Object> resolveDarkAssets(JSONObject flowConfig, String runtimeColorScheme, String runtimeDarkBackgroundColor, boolean nightMode) {
        if (!COLOR_SCHEME_DARK.equals(activeColorScheme(flowConfig, runtimeColorScheme, runtimeDarkBackgroundColor, nightMode))) {
            return new LinkedHashMap<>();
        }
        return darkAssets(flowConfig);
    }

    private static Map<String, Object> darkAssets(JSONObject flowConfig) {
        Map<String, Object> assets = new LinkedHashMap<>();
        if (flowConfig == null) {
            return assets;
        }
        for (String[] keys : DARK_ASSET_KEYS) {
            Object value = flowConfig.opt(keys[1]);
            if (!JSONObject.NULL.equals(value)) {
                assets.put(keys[0], value);
            }
        }
        return assets;
    }

    /**
     * The background color for the given state (see {@link #resolvePalette}).
     */
    static String resolveBackgroundColor(JSONObject flowConfig, String runtimeColorScheme, String runtimeLightBackgroundColor, String runtimeDarkBackgroundColor, boolean nightMode) {
        String backgroundColor = resolvePalette(flowConfig, runtimeColorScheme, runtimeLightBackgroundColor, runtimeDarkBackgroundColor, nightMode).get("backgroundColor");
        return backgroundColor != null ? backgroundColor : configuredBackgroundColor(flowConfig);
    }

    static JSONObject applyToFlowConfig(JSONObject flowConfig, String runtimeColorScheme, String runtimeLightBackgroundColor, String runtimeDarkBackgroundColor, boolean nightMode) {
        if (flowConfig == null) {
            return null;
        }

        boolean dark = COLOR_SCHEME_DARK.equals(activeColorScheme(flowConfig, runtimeColorScheme, runtimeDarkBackgroundColor, nightMode));
        Map<String, String> palette = resolvePalette(flowConfig, runtimeColorScheme, runtimeLightBackgroundColor, runtimeDarkBackgroundColor, nightMode);
        if (palette.isEmpty() && !dark) {
            return flowConfig;
        }

        try {
            JSONObject themedFlowConfig = new JSONObject(flowConfig.toString());
            for (Map.Entry<String, String> entry : palette.entrySet()) {
                themedFlowConfig.put(entry.getKey(), entry.getValue());
            }
            if (dark) {
                // Taken from the copy, so the aurora object isn't shared with the cached config.
                for (Map.Entry<String, Object> entry : darkAssets(themedFlowConfig).entrySet()) {
                    themedFlowConfig.put(entry.getKey(), entry.getValue());
                }
            }
            return themedFlowConfig;
        } catch (Exception ignore) {
            return flowConfig;
        }
    }

    /**
     * Returns #rrggbb for #rgb or #rrggbb input, else null.
     */
    static String normalizeHexColor(String color) {
        if (color == null) {
            return null;
        }
        String hex = color.trim();
        if (!hex.startsWith("#") || (hex.length() != 4 && hex.length() != 7)) {
            return null;
        }
        hex = hex.substring(1);
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                return null;
            }
        }
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        return "#" + hex.toLowerCase(Locale.ROOT);
    }
}
