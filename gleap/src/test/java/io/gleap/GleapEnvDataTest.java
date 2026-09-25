package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public class GleapEnvDataTest {
    @After
    public void resetEnvDataSettings() {
        Gleap.getInstance().setEnvDataPropsToIgnore(new String[0]);
        Gleap.getInstance().setDisableEnvData(false);
    }

    private static JSONObject envData() throws Exception {
        JSONObject envData = new JSONObject();
        envData.put("deviceName", "Pixel 9");
        envData.put("batteryLevel", 80);
        envData.put("sdkVersion", "18.1.0");
        return envData;
    }

    @Test
    public void keepsAllEnvDataByDefault() throws Exception {
        assertEquals(3, PhoneMeta.removeIgnoredProps(envData()).length());
    }

    @Test
    public void removesOnlyTheIgnoredProps() throws Exception {
        Gleap.getInstance().setEnvDataPropsToIgnore(new String[]{"deviceName", "batteryLevel", "unknownKey"});

        JSONObject envData = PhoneMeta.removeIgnoredProps(envData());

        assertFalse(envData.has("deviceName"));
        assertFalse(envData.has("batteryLevel"));
        assertEquals("18.1.0", envData.getString("sdkVersion"));
    }

    @Test
    public void eachCallReplacesThePreviousList() throws Exception {
        Gleap.getInstance().setEnvDataPropsToIgnore(new String[]{"deviceName"});
        Gleap.getInstance().setEnvDataPropsToIgnore(new String[]{"batteryLevel"});

        JSONObject envData = PhoneMeta.removeIgnoredProps(envData());
        assertTrue(envData.has("deviceName"));
        assertFalse(envData.has("batteryLevel"));

        Gleap.getInstance().setEnvDataPropsToIgnore(null);
        assertEquals(3, PhoneMeta.removeIgnoredProps(envData()).length());
    }

    @Test
    public void laterChangesToTheCallersArrayAreNotPickedUp() throws Exception {
        String[] propsToIgnore = new String[]{"deviceName"};
        Gleap.getInstance().setEnvDataPropsToIgnore(propsToIgnore);
        propsToIgnore[0] = "sdkVersion";

        JSONObject envData = PhoneMeta.removeIgnoredProps(envData());
        assertFalse(envData.has("deviceName"));
        assertTrue(envData.has("sdkVersion"));
    }

    @Test
    public void disableEnvDataCanBeToggled() {
        assertFalse(PhoneMeta.isEnvDataDisabled());

        Gleap.getInstance().setDisableEnvData(true);
        assertTrue(PhoneMeta.isEnvDataDisabled());

        Gleap.getInstance().setDisableEnvData(false);
        assertFalse(PhoneMeta.isEnvDataDisabled());
    }
}
