package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class GleapThemeHelperTest {
    private static JSONObject flowConfig(String backgroundColor) throws Exception {
        JSONObject flowConfig = new JSONObject();
        flowConfig.put("color", "#485bff");
        if (backgroundColor != null) {
            flowConfig.put("backgroundColor", backgroundColor);
        }
        return flowConfig;
    }

    private static String resolve(JSONObject flowConfig, String runtimeScheme, boolean nightMode) {
        return GleapThemeHelper.resolveBackgroundColor(flowConfig, runtimeScheme, null, null, nightMode);
    }

    @Test
    public void defaultSchemeKeepsTheDashboardColor() throws Exception {
        assertEquals("#ffffff", resolve(flowConfig("#ffffff"), null, true));
        assertEquals("#ffffff", resolve(flowConfig("#ffffff"), "default", true));
        assertEquals("#101010", resolve(flowConfig("#101010"), "unknown", false));
    }

    @Test
    public void swapsOnlyWhenTheDashboardColorDoesNotFit() throws Exception {
        // Light dashboard background.
        assertEquals("#18181b", resolve(flowConfig("#ffffff"), "dark", false));
        assertEquals("#ffffff", resolve(flowConfig("#ffffff"), "light", true));

        // Dark dashboard background keeps its brand look in dark mode.
        assertEquals("#101010", resolve(flowConfig("#101010"), "dark", false));
        assertEquals("#ffffff", resolve(flowConfig("#101010"), "light", true));
    }

    @Test
    public void autoFollowsTheNightMode() throws Exception {
        assertEquals("#18181b", resolve(flowConfig("#ffffff"), "auto", true));
        assertEquals("#ffffff", resolve(flowConfig("#ffffff"), "auto", false));
        assertEquals("#ffffff", resolve(flowConfig("#101010"), "auto", false));
    }

    @Test
    public void missingBackgroundCountsAsWhite() throws Exception {
        assertEquals("#ffffff", resolve(flowConfig(null), "light", false));
        assertEquals("#18181b", resolve(flowConfig(null), "dark", false));
        assertEquals("#18181b", resolve(null, "dark", false));
    }

    @Test
    public void dashboardSchemeAppliesWithoutRuntimeOverride() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");
        flowConfig.put("colorScheme", "auto");
        flowConfig.put("darkBackgroundColor", "#222");

        assertEquals("#222222", resolve(flowConfig, null, true));
        assertEquals("#222222", resolve(flowConfig, "default", true));
        // The runtime scheme wins over the dashboard one.
        assertEquals("#ffffff", resolve(flowConfig, "light", true));
    }

    @Test
    public void runtimeBackgroundColorsWinOverTheDashboardOnes() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");
        flowConfig.put("darkBackgroundColor", "#222222");

        assertEquals("#121212", GleapThemeHelper.resolveBackgroundColor(flowConfig, "dark", null, "#121212", false));
        // An invalid runtime color falls back to the dashboard one, an invalid dashboard one to the default.
        assertEquals("#222222", GleapThemeHelper.resolveBackgroundColor(flowConfig, "dark", null, "red", false));
        flowConfig.put("darkBackgroundColor", "rgb(0,0,0)");
        assertEquals("#18181b", GleapThemeHelper.resolveBackgroundColor(flowConfig, "dark", null, null, false));

        JSONObject darkFlowConfig = flowConfig("#000000");
        darkFlowConfig.put("lightBackgroundColor", "#fafafa");
        assertEquals("#fafafa", GleapThemeHelper.resolveBackgroundColor(darkFlowConfig, "light", null, null, true));
        assertEquals("#eeeeee", GleapThemeHelper.resolveBackgroundColor(darkFlowConfig, "light", "#EEE", null, true));
    }

    @Test
    public void applyToFlowConfigReturnsACopyAndKeepsTheOriginal() throws Exception {
        JSONObject flowConfig = flowConfig("#ffffff");

        JSONObject themed = GleapThemeHelper.applyToFlowConfig(flowConfig, "dark", null, null, false);
        assertEquals("#18181b", themed.getString("backgroundColor"));
        assertEquals("#485bff", themed.getString("color"));
        assertEquals("#ffffff", flowConfig.getString("backgroundColor"));

        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "light", null, null, true));
        assertSame(flowConfig, GleapThemeHelper.applyToFlowConfig(flowConfig, "default", null, null, true));
        assertNull(GleapThemeHelper.applyToFlowConfig(null, "dark", null, null, true));
    }

    @Test
    public void darkUsesTheWidgetsYiqThreshold() {
        assertTrue(GleapThemeHelper.isDarkColor("#000000"));
        assertTrue(GleapThemeHelper.isDarkColor("#18181b"));
        assertTrue(GleapThemeHelper.isDarkColor("#485bff"));
        assertFalse(GleapThemeHelper.isDarkColor("#ffffff"));
        assertFalse(GleapThemeHelper.isDarkColor("#fff"));
        assertFalse(GleapThemeHelper.isDarkColor("#a0a0a0"));
        assertTrue(GleapThemeHelper.isDarkColor("#9f9f9f"));
        assertFalse(GleapThemeHelper.isDarkColor("not a color"));
    }

    @Test
    public void normalizesHexColors() {
        assertEquals("#aabbcc", GleapThemeHelper.normalizeHexColor("#ABC"));
        assertEquals("#121212", GleapThemeHelper.normalizeHexColor(" #121212 "));
        assertNull(GleapThemeHelper.normalizeHexColor("#12121280"));
        assertNull(GleapThemeHelper.normalizeHexColor("121212"));
        assertNull(GleapThemeHelper.normalizeHexColor("#12g212"));
        assertNull(GleapThemeHelper.normalizeHexColor(null));
    }

    @Test
    public void setColorSchemeCanBeCalledBeforeInitialize() {
        Gleap.getInstance().setColorScheme("dark", null, "#121212");
        assertEquals("#121212", GleapConfig.getInstance().getBackgroundColor());

        Gleap.getInstance().setColorScheme("default");
        assertEquals("#ffffff", GleapConfig.getInstance().getBackgroundColor());
    }
}
