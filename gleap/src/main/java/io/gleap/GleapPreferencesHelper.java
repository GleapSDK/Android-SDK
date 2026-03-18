package io.gleap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

class GleapPreferencesHelper {
    private static final String PREFS_NAME = "gleap-secure";
    private static final String KEYSTORE_ALIAS = "gleap_prefs_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SharedPreferences prefs;
    private final boolean encryptionAvailable;
    private SecretKey secretKey;

    private static GleapPreferencesHelper instance;

    private GleapPreferencesHelper(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean keyReady = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                this.secretKey = getOrCreateKey();
                keyReady = true;
            } catch (Exception e) {
                // Keystore corrupted or unavailable — fall back to plaintext.
            }
        }
        this.encryptionAvailable = keyReady;
    }

    static synchronized GleapPreferencesHelper getInstance(Context context) {
        if (instance == null) {
            instance = new GleapPreferencesHelper(context.getApplicationContext());
        }
        return instance;
    }

    void putString(String key, String value) {
        if (value == null) {
            prefs.edit().remove(key).apply();
            return;
        }
        if (encryptionAvailable) {
            try {
                prefs.edit().putString(key, encrypt(value)).apply();
                return;
            } catch (Exception e) {
                // Fall through to plaintext.
            }
        }
        prefs.edit().putString(key, value).apply();
    }

    String getString(String key, String defaultValue) {
        String stored = prefs.getString(key, null);
        if (stored == null) {
            return defaultValue;
        }
        if (encryptionAvailable) {
            try {
                return decrypt(stored);
            } catch (Exception e) {
                // Could be plaintext from before encryption was enabled, or corrupted.
                return defaultValue;
            }
        }
        return stored;
    }

    void putFloat(String key, float value) {
        putString(key, String.valueOf(value));
    }

    float getFloat(String key, float defaultValue) {
        String stored = getString(key, null);
        if (stored == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(stored);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    void clear() {
        prefs.edit().clear().apply();
    }

    // --- Encryption internals ---

    private String encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

        // Prepend IV to ciphertext.
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    private String decrypt(String base64Data) throws Exception {
        byte[] data = Base64.decode(base64Data, Base64.NO_WRAP);
        ByteBuffer buffer = ByteBuffer.wrap(data);

        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, "UTF-8");
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);
            return entry.getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGenerator.init(spec);
        }
        return keyGenerator.generateKey();
    }
}
