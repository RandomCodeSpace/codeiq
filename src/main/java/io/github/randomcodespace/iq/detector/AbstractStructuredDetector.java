package io.github.randomcodespace.iq.detector;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for structured data detectors (YAML/JSON/XML).
 * Provides defensive access methods for navigating parsed data structures.
 */
public abstract class AbstractStructuredDetector implements Detector {

    /**
     * Safely cast an object to {@code Map<String, Object>}.
     * Returns an empty map if the object is not a map.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    /**
     * Get a nested map from a container by key.
     * Returns an empty map if the key is missing or the value is not a map.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getMap(Object container, String key) {
        if (container instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof Map<?, ?> nested) {
                return (Map<String, Object>) nested;
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Get a list from a container by key.
     * Returns an empty list if the key is missing or the value is not a list.
     */
    @SuppressWarnings("unchecked")
    protected List<Object> getList(Object container, String key) {
        if (container instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                return (List<Object>) list;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Get a string from a container by key.
     * Returns null if the key is missing or the value is not a string.
     */
    protected String getString(Object container, String key) {
        if (container instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof String s) {
                return s;
            }
        }
        return null;
    }

    /**
     * Get a string from a container by key, with a default value.
     */
    protected String getStringOrDefault(Object container, String key, String defaultValue) {
        String value = getString(container, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get an integer from a container by key, with a default value.
     * Handles both Integer and Number types.
     */
    protected int getInt(Object container, String key, int defaultValue) {
        if (container instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return defaultValue;
    }
}
