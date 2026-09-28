package io.gleap;

import java.util.HashMap;
import java.util.Map;

/**
 * Test double for the SDK's persistent storage.
 */
class InMemoryKeyValueStore implements KeyValueStore {
    final Map<String, String> values = new HashMap<>();

    @Override
    public void putString(String key, String value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        String value = values.get(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public void putFloat(String key, float value) {
        putString(key, String.valueOf(value));
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        String value = values.get(key);
        return value != null ? Float.parseFloat(value) : defaultValue;
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public void clear() {
        values.clear();
    }
}
