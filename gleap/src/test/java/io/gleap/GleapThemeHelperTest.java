package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;

public class GleapThemeHelperTest {
    // The light (base) palette, with dark / light mode enabled in the dashboard
    // ("auto", as the dashboard saves it).
    private static JSONObject flowConfig(String backgroundColor) throws Exception {
        JSONObject flowConfig = new JSONObject();
        flowConfig.put("colorScheme", "auto");
        flowConfig.put("color", "#485bff");
        flowConfig.put("headerColor", "#111111");
        flowConfig.put("headerColor2", "#222222");
        flowConfig.put("headerColor3", "#333333");
        if (backgroundColor != null) {
            flowConfig.put("backgroundColor", backgroundColor);
        }
        return flowConfig;
    }

    // The light palette plus a complete dark palette, as the dashboard saves it.
    private static JSONObject flowConfigWithDarkPalette() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");
        flowConfig.put("darkHeaderColor", "#AAA");
        flowConfig.put("darkHeaderColor2", "#bbbbbb");
        flowConfig.put("darkHeaderColor3", "#cccccc");
        flowConfig.put("darkColor", "#FF8800");
        flowConfig.put("darkBackgroundColor", "#0a0a0a");
        return flowConfig;
    }

    // Dark / light mode disabled in the dashboard: no colorScheme.
    private static JSONObject disabled(JSONObject flowConfig) {
        flowConfig.remove("colorScheme");
        return flowConfig;
    }

    private static String resolve(JSONObject flowConfig, String runtimeScheme, boolean nightMode) {
        return GleapThemeHelper.resolveBackgroundColor(flowConfig, runtimeScheme, null, null, nightMode);
    }

    private static JSONObject apply(JSONObject flowConfig, String runtimeScheme) {
        return GleapThemeHelper.applyToFlowConfig(flowConfig, runtimeScheme, null, null, false);
    }

    @Test
    public void defaultSchemeKeepsTheDashboardColors() throws Exception {
        JSONObject flowConfig = disabled(flowConfigWithDarkPalette());
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, null, null, null, true));
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "default", null, "#121212", true));
        assertEquals("#101010", resolve(disabled(flowConfig("#101010")), "unknown", false));
        assertEquals("#ffffff", resolve(disabled(flowConfig(null)), null, true));
    }

    @Test
    public void aDisabledDashboardSchemeIgnoresTheRuntimeScheme() throws Exception {
        // Missing, "default" or unknown: dark / light mode disabled in the dashboard.
        for (String dashboardScheme : new String[]{null, "default", "unknown"}) {
            JSONObject flowConfig = disabled(flowConfigWithDarkPalette());
            putLightAssets(flowConfig);
            putDarkAssets(flowConfig);
            if (dashboardScheme != null) {
                flowConfig.put("colorScheme", dashboardScheme);
            }
            String original = flowConfig.toString();

            for (String runtimeScheme : new String[]{"dark", "auto", "light"}) {
                // A full dark palette, a runtime dark background and night mode — still not themed.
                assertEquals(GleapThemeHelper.COLOR_SCHEME_DEFAULT, GleapThemeHelper.activeColorScheme(flowConfig, runtimeScheme, "#121212", true));
                assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, runtimeScheme, "#eeeeee", "#121212", true));
                assertTrue(GleapThemeHelper.resolvePalette(flowConfig, runtimeScheme, "#eeeeee", "#121212", true).isEmpty());
                assertTrue(GleapThemeHelper.resolveDarkAssets(flowConfig, runtimeScheme, "#121212", true).isEmpty());
                assertEquals("#ffffff", GleapThemeHelper.resolveBackgroundColor(flowConfig, runtimeScheme, "#eeeeee", "#121212", true));
            }
            assertEquals(original, flowConfig.toString());
        }
    }

    @Test
    public void anEnabledDashboardSchemeLetsTheRuntimeSchemeApply() throws Exception {
        // Dashboard "auto" + runtime dark: dark, even in light mode.
        JSONObject flowConfig = flowConfigWithDarkPalette();
        assertEquals(GleapThemeHelper.COLOR_SCHEME_DARK, GleapThemeHelper.activeColorScheme(flowConfig, "dark", "#121212", false));
        JSONObject themed = GleapThemeHelper.applyToFlowConfig(flowConfig, "dark", null, "#121212", false);
        assertEquals("#121212", themed.getString("backgroundColor"));
        assertEquals("#ff8800", themed.getString("color"));
        assertEquals("#aaaaaa", themed.getString("headerColor"));

        // Dashboard "light" + runtime dark: dark too.
        flowConfig.put("colorScheme", "light");
        assertEquals(GleapThemeHelper.COLOR_SCHEME_DARK, GleapThemeHelper.activeColorScheme(flowConfig, "dark", null, false));
        assertEquals("#0a0a0a", resolve(flowConfig, "dark", false));
    }

    @Test
    public void darkAppliesTheFullDarkPalette() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();

        JSONObject themed = apply(flowConfig, "dark");
        assertEquals("#0a0a0a", themed.getString("backgroundColor"));
        assertEquals("#ff8800", themed.getString("color"));
        assertEquals("#aaaaaa", themed.getString("headerColor"));
        assertEquals("#bbbbbb", themed.getString("headerColor2"));
        assertEquals("#cccccc", themed.getString("headerColor3"));
        assertEquals(GleapThemeHelper.COLOR_SCHEME_DARK, GleapThemeHelper.activeColorScheme(flowConfig, "dark", null, false));

        // The original stays the light palette.
        assertEquals("#ffffff", flowConfig.getString("backgroundColor"));
        assertEquals("#485bff", flowConfig.getString("color"));
        assertEquals("#111111", flowConfig.getString("headerColor"));
    }

    @Test
    public void darkWithAPartialPaletteKeepsTheMissingBaseColors() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");
        flowConfig.put("darkColor", "#ff8800");

        JSONObject themed = apply(flowConfig, "dark");
        assertEquals("#ff8800", themed.getString("color"));
        assertEquals("#ffffff", themed.getString("backgroundColor"));
        assertEquals("#111111", themed.getString("headerColor"));
        assertEquals("#222222", themed.getString("headerColor2"));
        assertEquals("#333333", themed.getString("headerColor3"));

        // Base keys that are missing stay missing — no default colors.
        JSONObject sparse = new JSONObject();
        sparse.put("colorScheme", "auto");
        sparse.put("darkHeaderColor", "#aaaaaa");
        themed = apply(sparse, "dark");
        assertEquals("#aaaaaa", themed.getString("headerColor"));
        assertFalse(themed.has("backgroundColor"));
        assertFalse(themed.has("color"));
        assertFalse(themed.has("headerColor2"));
    }

    @Test
    public void invalidDarkValuesAreIgnored() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");
        flowConfig.put("darkHeaderColor", "#aaaaaa");
        flowConfig.put("darkHeaderColor2", "not a color");
        flowConfig.put("darkColor", "rgb(0,0,0)");
        flowConfig.put("darkBackgroundColor", "#12121280");

        JSONObject themed = apply(flowConfig, "dark");
        assertEquals("#aaaaaa", themed.getString("headerColor"));
        assertEquals("#222222", themed.getString("headerColor2"));
        assertEquals("#485bff", themed.getString("color"));
        assertEquals("#ffffff", themed.getString("backgroundColor"));
    }

    @Test
    public void darkWithoutAPaletteIsNotDark() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");
        flowConfig.put("darkColor", "invalid");

        assertFalse(GleapThemeHelper.hasDarkPalette(flowConfig, null));
        assertSame(flowConfig, apply(flowConfig, "dark"));
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "auto", null, null, true));
        assertEquals("#ffffff", resolve(flowConfig, "dark", false));
        assertEquals("#ffffff", resolve(flowConfig(null), "dark", false));
        assertEquals("#ffffff", resolve(null, "dark", false));
        // Treated exactly like no scheme.
        assertEquals(GleapThemeHelper.COLOR_SCHEME_DEFAULT, GleapThemeHelper.activeColorScheme(flowConfig, "dark", null, false));
        assertEquals(GleapThemeHelper.COLOR_SCHEME_DEFAULT, GleapThemeHelper.activeColorScheme(flowConfig, "auto", null, true));
        // An invalid runtime dark background doesn't make a palette either.
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "dark", null, "red", false));
    }

    @Test
    public void aRuntimeDarkBackgroundAloneSwapsTheBackground() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");

        assertTrue(GleapThemeHelper.hasDarkPalette(flowConfig, "#121212"));
        assertEquals(GleapThemeHelper.COLOR_SCHEME_DARK, GleapThemeHelper.activeColorScheme(flowConfig, "dark", "#121212", false));

        JSONObject themed = GleapThemeHelper.applyToFlowConfig(flowConfig, "dark", null, "#121212", false);
        assertEquals("#121212", themed.getString("backgroundColor"));
        assertEquals("#485bff", themed.getString("color"));
        assertEquals("#111111", themed.getString("headerColor"));
        assertEquals("#222222", themed.getString("headerColor2"));
        assertEquals("#333333", themed.getString("headerColor3"));
    }

    @Test
    public void lightKeepsTheBaseColors() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();

        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "light", null, "#121212", true));
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "auto", null, null, false));
        assertEquals(GleapThemeHelper.COLOR_SCHEME_LIGHT, GleapThemeHelper.activeColorScheme(flowConfig, "light", null, true));
        // A dark dashboard background is the light palette's background too.
        assertEquals("#101010", resolve(flowConfig("#101010"), "light", true));
    }

    @Test
    public void lightUsesTheRuntimeLightBackground() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();

        JSONObject themed = GleapThemeHelper.applyToFlowConfig(flowConfig, "light", "#EEE", null, true);
        assertEquals("#eeeeee", themed.getString("backgroundColor"));
        assertEquals("#485bff", themed.getString("color"));
        assertEquals("#111111", themed.getString("headerColor"));
        // Invalid runtime colors are ignored.
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "light", "white", null, true));
    }

    @Test
    public void runtimeOverridesWin() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();

        // The runtime dark background wins over the dashboard's dark background.
        JSONObject themed = GleapThemeHelper.applyToFlowConfig(flowConfig, "dark", null, "#121212", false);
        assertEquals("#121212", themed.getString("backgroundColor"));
        assertEquals("#ff8800", themed.getString("color"));
        // An invalid one falls back to the dashboard's.
        assertEquals("#0a0a0a", GleapThemeHelper.resolveBackgroundColor(flowConfig, "dark", null, "red", false));
        // Each runtime background only applies to its own scheme.
        assertEquals("#0a0a0a", GleapThemeHelper.resolveBackgroundColor(flowConfig, "dark", "#eeeeee", null, false));
        assertEquals("#ffffff", GleapThemeHelper.resolveBackgroundColor(flowConfig, "light", null, "#121212", true));

        // The runtime scheme wins over the dashboard's.
        flowConfig.put("colorScheme", "dark");
        assertEquals("#ffffff", resolve(flowConfig, "light", true));
        assertEquals("#0a0a0a", resolve(flowConfig, null, false));
    }

    @Test
    public void dashboardSchemeAppliesWithoutRuntimeOverride() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();
        flowConfig.put("colorScheme", "auto");

        assertEquals("#0a0a0a", resolve(flowConfig, null, true));
        assertEquals("#0a0a0a", resolve(flowConfig, "default", true));
        assertEquals("#ffffff", resolve(flowConfig, null, false));
    }

    @Test
    public void autoFollowsTheNightMode() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();
        assertEquals("#0a0a0a", resolve(flowConfig, "auto", true));
        assertEquals("#ffffff", resolve(flowConfig, "auto", false));
    }

    // Light logo, header image and composer glow.
    private static void putLightAssets(JSONObject flowConfig) throws Exception {
        flowConfig.put("logo", "https://example.com/logo.png");
        flowConfig.put("bgImage", "https://example.com/bg.png");
        flowConfig.put("aurora", new JSONObject("{\"colors\":[\"#111111\",\"#222222\"],\"source\":\"brand\",\"seed\":1}"));
    }

    // Dark logo (none), header image and composer glow, as the dashboard saves them.
    private static void putDarkAssets(JSONObject flowConfig) throws Exception {
        flowConfig.put("darkLogo", "");
        flowConfig.put("darkBgImage", "https://example.com/bg-dark.png");
        flowConfig.put("darkAurora", new JSONObject("{\"colors\":[\"#aaaaaa\",\"#bbbbbb\"],\"source\":\"custom\",\"seed\":7}"));
    }

    @Test
    public void darkSwapsTheLogoHeaderImageAndComposerGlow() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();
        putLightAssets(flowConfig);
        putDarkAssets(flowConfig);

        JSONObject themed = apply(flowConfig, "dark");
        // An empty dark logo means no logo in dark mode.
        assertEquals("", themed.getString("logo"));
        assertEquals("https://example.com/bg-dark.png", themed.getString("bgImage"));
        JSONObject aurora = themed.getJSONObject("aurora");
        assertEquals("custom", aurora.getString("source"));
        assertEquals(7, aurora.getInt("seed"));
        assertEquals("#aaaaaa", aurora.getJSONArray("colors").getString(0));
        assertEquals("#bbbbbb", aurora.getJSONArray("colors").getString(1));
        // Not shared with the cached config.
        assertNotSame(flowConfig.getJSONObject("darkAurora"), aurora);
        assertEquals("#0a0a0a", themed.getString("backgroundColor"));

        Map<String, Object> assets = GleapThemeHelper.resolveDarkAssets(flowConfig, "auto", null, true);
        assertEquals("", assets.get("logo"));
        assertEquals("https://example.com/bg-dark.png", assets.get("bgImage"));
        assertEquals("custom", ((JSONObject) assets.get("aurora")).getString("source"));

        // The original keeps the light values.
        assertEquals("https://example.com/logo.png", flowConfig.getString("logo"));
        assertEquals("https://example.com/bg.png", flowConfig.getString("bgImage"));
        assertEquals("brand", flowConfig.getJSONObject("aurora").getString("source"));
    }

    @Test
    public void absentDarkAssetKeysKeepTheBaseValues() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();
        putLightAssets(flowConfig);
        // Saved before the dark fields existed — or explicitly null.
        flowConfig.put("darkBgImage", JSONObject.NULL);

        JSONObject themed = apply(flowConfig, "dark");
        assertEquals("#0a0a0a", themed.getString("backgroundColor"));
        assertEquals("https://example.com/logo.png", themed.getString("logo"));
        assertEquals("https://example.com/bg.png", themed.getString("bgImage"));
        assertEquals("brand", themed.getJSONObject("aurora").getString("source"));
        assertTrue(GleapThemeHelper.resolveDarkAssets(flowConfig, "dark", null, false).isEmpty());

        // A present dark value is used even without a base value.
        JSONObject noBase = flowConfigWithDarkPalette();
        noBase.put("darkLogo", "https://example.com/logo-dark.png");
        themed = apply(noBase, "dark");
        assertEquals("https://example.com/logo-dark.png", themed.getString("logo"));
        assertFalse(themed.has("bgImage"));
        assertFalse(themed.has("aurora"));
    }

    @Test
    public void darkAssetsNeedADarkColorPalette() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");
        putLightAssets(flowConfig);
        putDarkAssets(flowConfig);

        assertSame(flowConfig, apply(flowConfig, "dark"));
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "auto", null, null, true));
        assertTrue(GleapThemeHelper.resolveDarkAssets(flowConfig, "dark", null, true).isEmpty());
        assertEquals("https://example.com/logo.png", flowConfig.getString("logo"));
    }

    @Test
    public void lightKeepsTheLogoHeaderImageAndComposerGlow() throws Exception {
        JSONObject flowConfig = flowConfigWithDarkPalette();
        putLightAssets(flowConfig);
        putDarkAssets(flowConfig);
        // Dashboard "light": without a runtime scheme (or with "default") it is light too.
        flowConfig.put("colorScheme", "light");

        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "light", null, null, true));
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "auto", null, null, false));
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, null, null, null, true));
        assertTrue(GleapThemeHelper.resolveDarkAssets(flowConfig, "light", null, true).isEmpty());
        assertTrue(GleapThemeHelper.resolveDarkAssets(flowConfig, "auto", null, false).isEmpty());
        assertTrue(GleapThemeHelper.resolveDarkAssets(flowConfig, "default", null, true).isEmpty());

        // A runtime light background still leaves them as they are.
        JSONObject themed = GleapThemeHelper.applyToFlowConfig(flowConfig, "light", "#eeeeee", null, true);
        assertEquals("https://example.com/logo.png", themed.getString("logo"));
        assertEquals("https://example.com/bg.png", themed.getString("bgImage"));
        assertEquals("brand", themed.getJSONObject("aurora").getString("source"));
    }

    @Test
    public void applyToFlowConfigHandlesNull() {
        assertNull(GleapThemeHelper.applyToFlowConfig(null, "dark", null, "#121212", true));
    }

    @Test
    public void normalizesHexColors() {
        assertEquals("#aabbcc", GleapThemeHelper.normalizeHexColor("#ABC"));
        assertEquals("#121212", GleapThemeHelper.normalizeHexColor(" #121212 "));
        assertNull(GleapThemeHelper.normalizeHexColor("#12121280"));
        assertNull(GleapThemeHelper.normalizeHexColor("121212"));
        assertNull(GleapThemeHelper.normalizeHexColor("#12g212"));
        assertNull(GleapThemeHelper.normalizeHexColor("#12"));
        assertNull(GleapThemeHelper.normalizeHexColor(null));
    }

    @Test
    public void setColorSchemeCanBeCalledBeforeInitialize() throws Exception {
        GleapConfig.resetForTesting();
        try {
            // No config yet — nothing is themed until the dashboard enables dark / light mode.
            Gleap.getInstance().setColorScheme("dark", null, "#121212");
            assertEquals("#ffffff", GleapConfig.getInstance().getBackgroundColor());

            // The config loads with dark / light mode enabled: the earlier call applies.
            GleapConfig.getInstance().initConfig(new JSONObject().put("flowConfig", flowConfig("#ffffff")));
            assertEquals("#121212", GleapConfig.getInstance().getBackgroundColor());

            // No dark palette: dark keeps the normal colors.
            Gleap.getInstance().setColorScheme("dark");
            assertEquals("#ffffff", GleapConfig.getInstance().getBackgroundColor());

            Gleap.getInstance().setColorScheme("default");
            assertEquals("#ffffff", GleapConfig.getInstance().getBackgroundColor());
        } finally {
            Gleap.getInstance().setColorScheme("default");
            GleapConfig.resetForTesting();
        }
    }

    @Test
    public void configGettersApplyTheDarkPalette() throws Exception {
        JSONObject flowConfig = new JSONObject();
        flowConfig.put("colorScheme", "auto");
        flowConfig.put("color", "#485bff");
        flowConfig.put("backgroundColor", "#ffffff");
        flowConfig.put("headerColor", "#111111");
        flowConfig.put("darkHeaderColor", "#aaaaaa");
        flowConfig.put("darkColor", "#ff8800");
        flowConfig.put("darkBackgroundColor", "#0a0a0a");
        flowConfig.put("bgImage", "https://example.com/bg.png");
        flowConfig.put("darkBgImage", "https://example.com/bg-dark.png");
        JSONObject plainConfig = new JSONObject();
        plainConfig.put("flowConfig", flowConfig);

        GleapConfig.resetForTesting();
        try {
            GleapConfig.getInstance().initConfig(plainConfig);
            GleapConfig config = GleapConfig.getInstance();

            Gleap.getInstance().setColorScheme("dark");
            assertEquals("#0a0a0a", config.getBackgroundColor());
            assertEquals("#ff8800", config.getColor());
            assertEquals("#aaaaaa", config.getHeaderColor());
            // headerColor2/3 fall back to the themed headerColor.
            assertEquals("#aaaaaa", config.getHeaderColor2());
            assertEquals("#ff8800", config.getThemedFlowConfig().getString("color"));
            // The loader's header image follows the scheme too.
            assertEquals("https://example.com/bg-dark.png", config.getBgImage());

            Gleap.getInstance().setColorScheme("dark", null, "#121212");
            assertEquals("#121212", config.getBackgroundColor());
            assertEquals("#ff8800", config.getColor());

            Gleap.getInstance().setColorScheme("light");
            assertEquals("#ffffff", config.getBackgroundColor());
            assertEquals("#485bff", config.getColor());
            assertEquals("#111111", config.getHeaderColor());
            assertEquals("#111111", config.getHeaderColor3());
            assertEquals("https://example.com/bg.png", config.getBgImage());
        } finally {
            Gleap.getInstance().setColorScheme("default");
            GleapConfig.resetForTesting();
        }
    }

    @Test
    public void configGettersKeepTheNormalColorsWithoutADarkPalette() throws Exception {
        JSONObject flowConfig = new JSONObject();
        flowConfig.put("colorScheme", "auto");
        flowConfig.put("color", "#485bff");
        flowConfig.put("backgroundColor", "#ffffff");
        flowConfig.put("headerColor", "#111111");
        flowConfig.put("bgImage", "https://example.com/bg.png");
        flowConfig.put("darkBgImage", "");
        JSONObject plainConfig = new JSONObject();
        plainConfig.put("flowConfig", flowConfig);

        GleapConfig.resetForTesting();
        try {
            GleapConfig.getInstance().initConfig(plainConfig);
            GleapConfig config = GleapConfig.getInstance();

            Gleap.getInstance().setColorScheme("dark");
            assertEquals("#ffffff", config.getBackgroundColor());
            assertEquals("#485bff", config.getColor());
            assertEquals("#111111", config.getHeaderColor());
            assertEquals("#ffffff", config.getThemedFlowConfig().getString("backgroundColor"));
            assertEquals("#485bff", config.getThemedFlowConfig().getString("color"));
            assertEquals("https://example.com/bg.png", config.getBgImage());
        } finally {
            Gleap.getInstance().setColorScheme("default");
            GleapConfig.resetForTesting();
        }
    }

    @Test
    public void configGettersIgnoreTheRuntimeSchemeWhenTheDashboardDisablesIt() throws Exception {
        JSONObject flowConfig = disabled(flowConfigWithDarkPalette());
        putLightAssets(flowConfig);
        putDarkAssets(flowConfig);
        JSONObject plainConfig = new JSONObject();
        plainConfig.put("flowConfig", flowConfig);

        GleapConfig.resetForTesting();
        try {
            GleapConfig.getInstance().initConfig(plainConfig);
            GleapConfig config = GleapConfig.getInstance();
            JSONObject cached = config.getPlainConfig().getJSONObject("flowConfig");
            String original = cached.toString();

            Gleap.getInstance().setColorScheme("dark", "#eeeeee", "#121212");
            assertEquals("#ffffff", config.getBackgroundColor());
            assertEquals("#485bff", config.getColor());
            assertEquals("#111111", config.getHeaderColor());
            assertEquals("#222222", config.getHeaderColor2());
            assertEquals("https://example.com/bg.png", config.getBgImage());
            // The config goes to the widget unchanged.
            assertSame(cached, config.getThemedFlowConfig());
            assertEquals(original, cached.toString());
        } finally {
            Gleap.getInstance().setColorScheme("default");
            GleapConfig.resetForTesting();
        }
    }
}
