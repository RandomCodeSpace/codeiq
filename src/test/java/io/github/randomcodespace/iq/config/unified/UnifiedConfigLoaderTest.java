package io.github.randomcodespace.iq.config.unified;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedConfigLoaderTest {

    private static Path fixture(String name) {
        return Paths.get("src/test/resources/config-unified/" + name);
    }

    @Test
    void missingFileProducesEmptyOverlay() {
        CodeIqUnifiedConfig cfg = UnifiedConfigLoader.load(Paths.get("does/not/exist.yml"));
        // Empty overlay = every section present with null/default-empty values.
        assertEquals(CodeIqUnifiedConfig.empty(), cfg);
    }

    @Test
    void minimalFileSetsOnlyDeclaredFields() {
        CodeIqUnifiedConfig cfg = UnifiedConfigLoader.load(fixture("minimal.yml"));
        assertEquals("my-service", cfg.project().name());
        assertEquals(2000, cfg.indexing().batchSize());
        // Unset fields stay null (indicating "inherit from lower layer")
        assertNull(cfg.indexing().cacheDir());
        assertNull(cfg.serving().port());
    }

    @Test
    void fullFileRoundTripsEveryField() {
        CodeIqUnifiedConfig cfg = UnifiedConfigLoader.load(fixture("full.yml"));
        assertEquals("demo", cfg.project().name());
        assertEquals(2, cfg.project().modules().size());
        assertEquals("services/api", cfg.project().modules().get(0).path());
        assertEquals("maven", cfg.project().modules().get(0).type());
        assertEquals(9090, cfg.serving().port());
        assertEquals("127.0.0.1", cfg.serving().bindAddress());
        assertEquals(true, cfg.serving().readOnly());
        assertEquals(".code-iq/graph/graph.db", cfg.serving().neo4j().dir());
        assertEquals(2048, cfg.serving().neo4j().heapMaxMb());
        assertEquals(10000, cfg.mcp().limits().perToolTimeoutMs());
        assertEquals(List.of("run_cypher"), cfg.mcp().tools().disabled());
        assertEquals(Boolean.TRUE, cfg.detectors().overrides().get("SpringRestDetector").enabled());
        assertEquals(Boolean.FALSE, cfg.detectors().overrides().get("QuarkusRestDetector").enabled());
    }

    @Test
    void malformedFileThrowsWithFileAnchor() {
        Path f = fixture("malformed.yml");
        ConfigLoadException e = assertThrows(ConfigLoadException.class,
                () -> UnifiedConfigLoader.load(f));
        assertTrue(e.getMessage().contains("malformed.yml"),
                "error must name the file, got: " + e.getMessage());
        assertTrue(e.getMessage().contains("batchSize"),
                "error must name the offending field, got: " + e.getMessage());
    }
}
