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

/**
 * The SDK's persistent storage (SharedPreferences "gleap-secure"): the session id and hash and the
 * identified user. Values are encrypted with an AndroidKeyStore key (API 23+); without a usable
 * key they are stored as plain text.
 */
class GleapPreferencesHelper implements KeyValueStore {
    private static final String PREFS_NAME = "gleap-secure";
    private static final String KEYSTORE_ALIAS = "gleap_prefs_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    /**
     * Turns bytes into storable text and back (Base64 without line breaks).
     */
    interface TextCodec {
        String encode(byte[] data);

        byte[] decode(String text);
    }

    /**
     * AES/GCM with a random IV per value; the stored text is Base64(IV + ciphertext).
     */
    static final class ValueCipher {
        private static final String TRANSFORMATION = "AES/GCM/NoPadding";
        private static final int GCM_IV_LENGTH = 12;
        private static final int GCM_TAG_LENGTH = 128;

        private final SecretKey secretKey;
        private final TextCodec codec;

        ValueCipher(SecretKey secretKey, TextCodec codec) {
            this.secretKey = secretKey;
            this.codec = codec;
        }

        String encrypt(String plaintext) throws Exception {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Prepend IV to ciphertext.
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return codec.encode(buffer.array());
        }

        String decrypt(String encoded) throws Exception {
            byte[] data = codec.decode(encoded);
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
    }

    private static final TextCodec ANDROID_BASE64 = new TextCodec() {
        @Override
        public String encode(byte[] data) {
            return Base64.encodeToString(data, Base64.NO_WRAP);
        }

        @Override
        public byte[] decode(String text) {
            return Base64.decode(text, Base64.NO_WRAP);
        }
    };

    private final SharedPreferences prefs;
    // null: no usable key, values are stored as plain text.
    private final ValueCipher cipher;

    private static GleapPreferencesHelper instance;

    private GleapPreferencesHelper(Context context) {
        this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), keystoreCipher());
    }

    GleapPreferencesHelper(SharedPreferences prefs, ValueCipher cipher) {
        this.prefs = prefs;
        this.cipher = cipher;
    }

    static synchronized GleapPreferencesHelper getInstance(Context context) {
        if (instance == null) {
            instance = new GleapPreferencesHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void putString(String key, String value) {
        if (value == null) {
            prefs.edit().remove(key).apply();
            return;
        }
        if (cipher != null) {
            try {
                prefs.edit().putString(key, cipher.encrypt(value)).apply();
                return;
            } catch (Exception e) {
                // Fall through to plaintext.
            }
        }
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public String getString(String key, String defaultValue) {
        String stored = prefs.getString(key, null);
        if (stored == null) {
            return defaultValue;
        }
        if (cipher != null) {
            try {
                return cipher.decrypt(stored);
            } catch (Exception e) {
                // Could be plaintext from before encryption was enabled, or corrupted.
                return defaultValue;
            }
        }
        return stored;
    }

    @Override
    public void putFloat(String key, float value) {
        putString(key, String.valueOf(value));
    }

    @Override
    public float getFloat(String key, float defaultValue) {
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

    @Override
    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    @Override
    public void clear() {
        prefs.edit().clear().apply();
    }

    private static ValueCipher keystoreCipher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return new ValueCipher(getOrCreateKey(), ANDROID_BASE64);
            } catch (Exception e) {
                // Keystore corrupted or unavailable — fall back to plaintext.
            }
        }
        return null;
    }

    private static SecretKey getOrCreateKey() throws Exception {
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
