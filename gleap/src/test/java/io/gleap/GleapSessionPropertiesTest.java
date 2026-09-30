package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * What identifyUser sends, including the user hash for identity verification, and when an
 * identify is skipped because nothing changed.
 */
public class GleapSessionPropertiesTest {
    @Before
    public void setUp() {
        GleapConfig.resetForTesting();
        GleapConfig.getInstance().setLanguage("de");
    }

    @After
    public void tearDown() {
        GleapConfig.resetForTesting();
    }

    @Test
    public void theUserHashIsSentOnlyWhenSet() throws Exception {
        JSONObject withHash = new GleapSessionProperties("user-1", "Ada", "ada@example.com", "hmac-1").getJSONPayload();
        assertEquals("hmac-1", withHash.getString("userHash"));
        assertEquals("user-1", withHash.getString("userId"));

        assertFalse(new GleapSessionProperties("user-1", "Ada", "ada@example.com").getJSONPayload().has("userHash"));
        assertFalse(new GleapSessionProperties("user-1", "Ada", "ada@example.com", "").getJSONPayload().has("userHash"));
    }

    @Test
    public void thePayloadSkipsEmptyValuesAndFlattensCustomData() throws Exception {
        GleapSessionProperties user = new GleapSessionProperties("user-1");
        user.setEmail("");
        user.setValue(0);
        user.setPlan("pro");
        user.setCustomData(new JSONObject().put("tier", "gold"));

        JSONObject payload = user.getJSONPayload();

        assertFalse(payload.has("email"));
        assertFalse(payload.has("value"));
        assertFalse(payload.has("sla"));
        assertEquals("pro", payload.getString("plan"));
        assertEquals("gold", payload.getString("tier"));
        assertEquals("de", payload.getString("lang"));
    }

    @Test
    public void aSessionResponseIsReadWithoutTheLanguageInCustomData() throws Exception {
        GleapSessionProperties user = GleapSessionProperties.fromJSONObject(new JSONObject()
                .put("userId", "user-1").put("name", "Ada").put("email", "ada@example.com")
                .put("value", 3.5).put("sla", 2).put("companyName", "Acme")
                .put("customData", new JSONObject().put("tier", "gold").put("lang", "de")));

        assertEquals("user-1", user.getUserId());
        assertEquals("Ada", user.getName());
        assertEquals(3.5, user.getValue(), 0);
        assertEquals("Acme", user.getCompanyName());
        assertEquals("gold", user.getCustomData().getString("tier"));
        assertFalse(user.getCustomData().has("lang"));
        assertNull(GleapSessionProperties.fromJSONObject(null));
    }

    @Test
    public void identifyIsSkippedOnlyForTheSameUserData() throws Exception {
        GleapSessionProperties stored = new GleapSessionProperties("user-1", "Ada", "ada@example.com");
        stored.setCustomData(new JSONObject().put("tier", "gold"));

        GleapSessionProperties same = new GleapSessionProperties("user-1", "Ada", "ada@example.com");
        same.setCustomData(new JSONObject().put("tier", "gold"));
        assertTrue(stored.equals(same));

        GleapSessionProperties otherEmail = new GleapSessionProperties("user-1", "Ada", "ada@other.com");
        otherEmail.setCustomData(new JSONObject().put("tier", "gold"));
        assertFalse(stored.equals(otherEmail));

        GleapSessionProperties otherUser = new GleapSessionProperties("user-2", "Ada", "ada@example.com");
        otherUser.setCustomData(new JSONObject().put("tier", "gold"));
        assertFalse(stored.equals(otherUser));

        GleapSessionProperties otherCustomData = new GleapSessionProperties("user-1", "Ada", "ada@example.com");
        otherCustomData.setCustomData(new JSONObject().put("tier", "silver"));
        assertFalse(stored.equals(otherCustomData));

        assertFalse(stored.equals(null));
    }

    @Test
    public void theUserHashDoesNotTakePartInTheComparison() throws Exception {
        GleapSessionProperties stored = new GleapSessionProperties("user-1", "Ada", "ada@example.com", "hmac-old");
        stored.setCustomData(new JSONObject());
        GleapSessionProperties update = new GleapSessionProperties("user-1", "Ada", "ada@example.com", "hmac-new");
        update.setCustomData(new JSONObject());

        assertTrue(stored.equals(update));
    }
}
