package io.github.randomcodespace.iq.detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DetectorRegistryTest {

    private DetectorRegistry registry;

    /** Simple stub detector for testing. */
    static class StubDetector implements Detector {
        private final String name;
        private final Set<String> languages;

        StubDetector(String name, Set<String> languages) {
            this.name = name;
            this.languages = languages;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> getSupportedLanguages() {
            return languages;
        }

        @Override
        public DetectorResult detect(DetectorContext ctx) {
            return DetectorResult.empty();
        }
    }

    @BeforeEach
    void setUp() {
        // Deliberately pass in unsorted order to verify sorting
        var d1 = new StubDetector("class-detector", Set.of("java", "python"));
        var d2 = new StubDetector("api-detector", Set.of("java", "typescript"));
        var d3 = new StubDetector("yaml-detector", Set.of("yaml"));

        registry = new DetectorRegistry(List.of(d1, d2, d3));
    }

    @Test
    void constructorSortsByName() {
        List<Detector> all = registry.allDetectors();
        assertEquals("api-detector", all.get(0).getName());
        assertEquals("class-detector", all.get(1).getName());
        assertEquals("yaml-detector", all.get(2).getName());
    }

    @Test
    void detectorsForLanguageReturnsCorrectSubset() {
        List<Detector> javaDetectors = registry.detectorsForLanguage("java");
        assertEquals(2, javaDetectors.size());

        List<String> names = javaDetectors.stream().map(Detector::getName).toList();
        assertTrue(names.contains("api-detector"));
        assertTrue(names.contains("class-detector"));
    }

    @Test
    void detectorsForLanguageYaml() {
        List<Detector> yamlDetectors = registry.detectorsForLanguage("yaml");
        assertEquals(1, yamlDetectors.size());
        assertEquals("yaml-detector", yamlDetectors.getFirst().getName());
    }

    @Test
    void detectorsForUnknownLanguageReturnsEmpty() {
        assertTrue(registry.detectorsForLanguage("rust").isEmpty());
    }

    @Test
    void allDetectorsReturnsSorted() {
        List<Detector> all = registry.allDetectors();
        assertEquals(3, all.size());
        for (int i = 0; i < all.size() - 1; i++) {
            assertTrue(all.get(i).getName().compareTo(all.get(i + 1).getName()) < 0);
        }
    }

    @Test
    void getByNameFindsDetector() {
        assertTrue(registry.get("class-detector").isPresent());
        assertEquals("class-detector", registry.get("class-detector").get().getName());
    }

    @Test
    void getByNameMissing() {
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    void countReturnsTotal() {
        assertEquals(3, registry.count());
    }
}
