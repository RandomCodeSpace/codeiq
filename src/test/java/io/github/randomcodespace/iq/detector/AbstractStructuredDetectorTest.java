package io.github.randomcodespace.iq.detector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AbstractStructuredDetectorTest {

    /** Concrete test subclass. */
    static class TestStructuredDetector extends AbstractStructuredDetector {
        @Override
        public String getName() {
            return "test-structured";
        }

        @Override
        public Set<String> getSupportedLanguages() {
            return Set.of("yaml", "json");
        }

        @Override
        public DetectorResult detect(DetectorContext ctx) {
            return DetectorResult.empty();
        }
    }

    private final TestStructuredDetector detector = new TestStructuredDetector();

    // --- getMap tests ---

    @Test
    void getMapWithValidNestedMap() {
        Map<String, Object> nested = Map.of("key", "value");
        Map<String, Object> container = Map.of("child", nested);

        Map<String, Object> result = detector.getMap(container, "child");
        assertEquals("value", result.get("key"));
    }

    @Test
    void getMapWithMissingKey() {
        Map<String, Object> container = Map.of("other", "value");

        Map<String, Object> result = detector.getMap(container, "child");
        assertTrue(result.isEmpty());
    }

    @Test
    void getMapWithWrongType() {
        Map<String, Object> container = Map.of("child", "not-a-map");

        Map<String, Object> result = detector.getMap(container, "child");
        assertTrue(result.isEmpty());
    }

    @Test
    void getMapWithNonMapContainer() {
        Map<String, Object> result = detector.getMap("not-a-map", "key");
        assertTrue(result.isEmpty());
    }

    // --- getList tests ---

    @Test
    void getListWithValidList() {
        Map<String, Object> container = Map.of("items", List.of("a", "b", "c"));

        List<Object> result = detector.getList(container, "items");
        assertEquals(3, result.size());
        assertEquals("a", result.getFirst());
    }

    @Test
    void getListWithMissingKey() {
        Map<String, Object> container = Map.of("other", "value");

        List<Object> result = detector.getList(container, "items");
        assertTrue(result.isEmpty());
    }

    @Test
    void getListWithWrongType() {
        Map<String, Object> container = Map.of("items", "not-a-list");

        List<Object> result = detector.getList(container, "items");
        assertTrue(result.isEmpty());
    }

    // --- getString tests ---

    @Test
    void getStringWithValidString() {
        Map<String, Object> container = Map.of("name", "hello");

        assertEquals("hello", detector.getString(container, "name"));
    }

    @Test
    void getStringWithMissingKey() {
        Map<String, Object> container = Map.of("other", "value");

        assertNull(detector.getString(container, "name"));
    }

    @Test
    void getStringWithWrongType() {
        Map<String, Object> container = Map.of("name", 42);

        assertNull(detector.getString(container, "name"));
    }

    @Test
    void getStringOrDefaultReturnsDefault() {
        Map<String, Object> container = Map.of("other", "value");

        assertEquals("fallback", detector.getStringOrDefault(container, "name", "fallback"));
    }

    @Test
    void getStringOrDefaultReturnsValue() {
        Map<String, Object> container = Map.of("name", "found");

        assertEquals("found", detector.getStringOrDefault(container, "name", "fallback"));
    }

    // --- getInt tests ---

    @Test
    void getIntWithValidInt() {
        Map<String, Object> container = Map.of("port", 8080);

        assertEquals(8080, detector.getInt(container, "port", 0));
    }

    @Test
    void getIntWithMissingKey() {
        Map<String, Object> container = Map.of("other", "value");

        assertEquals(3000, detector.getInt(container, "port", 3000));
    }

    @Test
    void getIntWithWrongType() {
        Map<String, Object> container = Map.of("port", "not-a-number");

        assertEquals(3000, detector.getInt(container, "port", 3000));
    }

    @Test
    void getIntWithDoubleValue() {
        Map<String, Object> container = Map.of("port", 8080.5);

        assertEquals(8080, detector.getInt(container, "port", 0));
    }

    // --- asMap tests ---

    @Test
    void asMapWithValidMap() {
        Map<String, Object> original = Map.of("key", "value");

        Map<String, Object> result = detector.asMap(original);
        assertEquals("value", result.get("key"));
    }

    @Test
    void asMapWithNonMap() {
        Map<String, Object> result = detector.asMap("not-a-map");
        assertTrue(result.isEmpty());
    }

    @Test
    void asMapWithNull() {
        Map<String, Object> result = detector.asMap(null);
        assertTrue(result.isEmpty());
    }
}
