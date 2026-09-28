package io.gleap;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * attachCustomData adds to the custom data a ticket carries, like on iOS and in the JS SDK.
 */
public class CustomDataTest {
    private SdkTestEnvironment sdk;

    @Before
    public void setUp() {
        sdk = new SdkTestEnvironment();
    }

    @After
    public void tearDown() {
        sdk.tearDown();
    }

    @Test
    public void attachedDataIsMergedIntoTheCustomData() throws Exception {
        Gleap.getInstance().setCustomData("plan", "pro");
        Gleap.getInstance().attachCustomData(new JSONObject().put("cart", 2).put("coupon", "SPRING"));
        Gleap.getInstance().attachCustomData(new JSONObject().put("cart", 3));

        JSONObject customData = GleapBug.getInstance().getCustomData();
        assertEquals("pro", customData.getString("plan"));
        assertEquals(3, customData.getInt("cart"));
        assertEquals("SPRING", customData.getString("coupon"));
        assertEquals(3, customData.length());
    }

    @Test
    public void laterChangesToTheAppsObjectAreNotPickedUp() throws Exception {
        JSONObject cart = new JSONObject().put("items", 2);
        JSONObject data = new JSONObject().put("cart", cart);
        Gleap.getInstance().attachCustomData(data);

        data.put("coupon", "SPRING");
        cart.put("items", 5);

        JSONObject customData = GleapBug.getInstance().getCustomData();
        assertEquals(1, customData.length());
        assertEquals(2, customData.getJSONObject("cart").getInt("items"));
    }
}
