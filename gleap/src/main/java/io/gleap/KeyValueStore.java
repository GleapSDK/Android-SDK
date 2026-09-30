package io.gleap;

/**
 * Persistent string storage for the session and the identified user (see
 * {@link GleapPreferencesHelper}).
 */
interface KeyValueStore {
    /**
     * Stores the value; null removes the key.
     */
    void putString(String key, String value);

    String getString(String key, String defaultValue);

    void putFloat(String key, float value);

    float getFloat(String key, float defaultValue);

    void remove(String key);

    /**
     * Removes every key.
     */
    void clear();
}
