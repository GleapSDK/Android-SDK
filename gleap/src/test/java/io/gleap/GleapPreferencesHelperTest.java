package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Base64;

import javax.crypto.KeyGenerator;

/**
 * The storage for the session id/hash and the identified user: encrypted at rest.
 */
public class GleapPreferencesHelperTest {
    private static final GleapPreferencesHelper.TextCodec JVM_BASE64 = new GleapPreferencesHelper.TextCodec() {
        @Override
        public String encode(byte[] data) {
            return Base64.getEncoder().encodeToString(data);
        }

        @Override
        public byte[] decode(String text) {
            return Base64.getDecoder().decode(text);
        }
    };

    private FakeSharedPreferences prefs;
    private GleapPreferencesHelper store;

    @Before
    public void setUp() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        prefs = new FakeSharedPreferences();
        store = new GleapPreferencesHelper(prefs, new GleapPreferencesHelper.ValueCipher(generator.generateKey(), JVM_BASE64));
    }

    @Test
    public void valuesAreStoredEncrypted() {
        store.putString("session_hash", "secret-hash");

        String stored = (String) prefs.values.get("session_hash");
        assertNotEquals("secret-hash", stored);
        assertFalse(stored.contains("secret-hash"));
        // 12 byte IV + ciphertext + 16 byte GCM tag.
        assertEquals(12 + "secret-hash".length() + 16, Base64.getDecoder().decode(stored).length);
        assertEquals("secret-hash", store.getString("session_hash", ""));
    }

    @Test
    public void everyWriteUsesAFreshIv() {
        store.putString("a", "same value");
        String first = (String) prefs.values.get("a");
        store.putString("a", "same value");

        assertNotEquals(first, prefs.values.get("a"));
        assertEquals("same value", store.getString("a", ""));
    }

    @Test
    public void nullRemovesTheKey() {
        store.putString("email", "user@example.com");
        store.putString("email", null);

        assertFalse(prefs.contains("email"));
        assertEquals("default", store.getString("email", "default"));
    }

    @Test
    public void valuesThatCannotBeDecryptedReturnTheDefault() {
        prefs.values.put("session_id", "written-in-plain-text");

        assertEquals("", store.getString("session_id", ""));
    }

    @Test
    public void withoutAKeyValuesAreStoredAsPlainText() {
        GleapPreferencesHelper plain = new GleapPreferencesHelper(prefs, null);
        plain.putString("session_id", "id-1");

        assertEquals("id-1", prefs.values.get("session_id"));
        assertEquals("id-1", plain.getString("session_id", ""));
    }

    @Test
    public void floatsRoundTripAndInvalidOnesReturnTheDefault() {
        store.putFloat("value", 12.5f);
        assertEquals(12.5f, store.getFloat("value", 0), 0);

        store.putString("sla", "not a number");
        assertEquals(3f, store.getFloat("sla", 3f), 0);
        assertEquals(1f, store.getFloat("missing", 1f), 0);
    }

    @Test
    public void clearRemovesEverything() {
        store.putString("session_id", "id-1");
        store.putString("session_hash", "hash-1");
        store.clear();

        assertTrue(prefs.values.isEmpty());
        assertNull(store.getString("session_id", null));
    }
}
