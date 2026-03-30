package io.github.randomcodespace.iq.detector;

import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // --- Tests for annotation-aware methods ---

    @DetectorInfo(
            name = "test-endpoint",
            category = "endpoints",
            description = "Test endpoint detector",
            languages = {"java"},
            nodeKinds = {NodeKind.ENDPOINT}
    )
    static class AnnotatedEndpointDetector implements Detector {
        @Override public String getName() { return "test-endpoint"; }
        @Override public Set<String> getSupportedLanguages() { return Set.of("java"); }
        @Override public DetectorResult detect(DetectorContext ctx) { return DetectorResult.empty(); }
    }

    @DetectorInfo(
            name = "test-entity",
            category = "entities",
            description = "Test entity detector",
            languages = {"java"},
            nodeKinds = {NodeKind.ENTITY}
    )
    static class AnnotatedEntityDetector implements Detector {
        @Override public String getName() { return "test-entity"; }
        @Override public Set<String> getSupportedLanguages() { return Set.of("java"); }
        @Override public DetectorResult detect(DetectorContext ctx) { return DetectorResult.empty(); }
    }

    @DetectorInfo(
            name = "test-auth",
            category = "auth",
            description = "Test auth detector",
            languages = {"java", "python"},
            nodeKinds = {NodeKind.GUARD}
    )
    static class AnnotatedAuthDetector implements Detector {
        @Override public String getName() { return "test-auth"; }
        @Override public Set<String> getSupportedLanguages() { return Set.of("java", "python"); }
        @Override public DetectorResult detect(DetectorContext ctx) { return DetectorResult.empty(); }
    }

    private DetectorRegistry annotatedRegistry;

    @BeforeEach
    void setUpAnnotated() {
        annotatedRegistry = new DetectorRegistry(List.of(
                new AnnotatedEndpointDetector(),
                new AnnotatedEntityDetector(),
                new AnnotatedAuthDetector()
        ));
    }

    @Test
    void detectorsForCategoryReturnsCorrectSubset() {
        List<Detector> endpoints = annotatedRegistry.detectorsForCategory("endpoints");
        assertEquals(1, endpoints.size());
        assertEquals("test-endpoint", endpoints.getFirst().getName());
    }

    @Test
    void detectorsForCategoryReturnsEmptyForUnknown() {
        assertTrue(annotatedRegistry.detectorsForCategory("nonexistent").isEmpty());
    }

    @Test
    void allCategoriesReturnsSorted() {
        List<String> categories = annotatedRegistry.allCategories();
        assertEquals(List.of("auth", "endpoints", "entities"), categories);
    }

    @Test
    void getInfoReturnsAnnotation() {
        Optional<DetectorInfo> info = annotatedRegistry.getInfo("test-auth");
        assertTrue(info.isPresent());
        assertEquals("auth", info.get().category());
        assertEquals("Test auth detector", info.get().description());
        assertArrayEquals(new String[]{"java", "python"}, info.get().languages());
    }

    @Test
    void getInfoReturnsEmptyForUnknown() {
        assertTrue(annotatedRegistry.getInfo("nonexistent").isEmpty());
    }

    @Test
    void byCategoryGroupsCorrectly() {
        Map<String, List<Detector>> grouped = annotatedRegistry.byCategory();
        assertEquals(3, grouped.size());
        assertTrue(grouped.containsKey("auth"));
        assertTrue(grouped.containsKey("endpoints"));
        assertTrue(grouped.containsKey("entities"));
        assertEquals(1, grouped.get("endpoints").size());
    }
}
