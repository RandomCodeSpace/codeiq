package io.github.randomcodespace.iq.cache;

import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisCacheTest {

    private AnalysisCache cache;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        cache = new AnalysisCache(tempDir.resolve("test-cache.db"));
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    @Test
    void isCachedReturnsFalseForUnknownHash() {
        assertFalse(cache.isCached("unknown-hash"));
    }

    @Test
    void storeAndRetrieveNodes() {
        CodeNode node = new CodeNode("test:file:class:MyClass", NodeKind.CLASS, "MyClass");
        node.setFilePath("src/MyClass.java");
        node.setModule("myModule");
        node.setLayer("backend");
        node.setAnnotations(List.of("@Entity"));
        node.setProperties(Map.of("framework", "spring"));

        cache.storeResults("hash123", "src/MyClass.java", "java",
                List.of(node), List.of());

        assertTrue(cache.isCached("hash123"));

        var result = cache.loadCachedResults("hash123");
        assertNotNull(result);
        assertEquals(1, result.nodes().size());
        assertEquals(0, result.edges().size());

        CodeNode loaded = result.nodes().getFirst();
        assertEquals("test:file:class:MyClass", loaded.getId());
        assertEquals(NodeKind.CLASS, loaded.getKind());
        assertEquals("MyClass", loaded.getLabel());
        assertEquals("src/MyClass.java", loaded.getFilePath());
        assertEquals("myModule", loaded.getModule());
        assertEquals("backend", loaded.getLayer());
        assertEquals(List.of("@Entity"), loaded.getAnnotations());
        assertEquals("spring", loaded.getProperties().get("framework"));
    }

    @Test
    void storeAndRetrieveEdges() {
        CodeNode source = new CodeNode("src:node", NodeKind.CLASS, "Source");
        CodeNode target = new CodeNode("tgt:node", NodeKind.METHOD, "Target");
        CodeEdge edge = new CodeEdge("edge1", EdgeKind.CALLS, "src:node", target);

        cache.storeResults("hash456", "src/file.java", "java",
                List.of(source, target), List.of(edge));

        var result = cache.loadCachedResults("hash456");
        assertNotNull(result);
        assertEquals(2, result.nodes().size());
        assertEquals(1, result.edges().size());

        CodeEdge loaded = result.edges().getFirst();
        assertEquals("edge1", loaded.getId());
        assertEquals(EdgeKind.CALLS, loaded.getKind());
        assertEquals("src:node", loaded.getSourceId());
    }

    @Test
    void removeFileDeletesCachedData() {
        CodeNode node = new CodeNode("n1", NodeKind.MODULE, "Mod");
        cache.storeResults("hashToDelete", "file.py", "python",
                List.of(node), List.of());

        assertTrue(cache.isCached("hashToDelete"));

        cache.removeFile("hashToDelete");

        assertFalse(cache.isCached("hashToDelete"));
        assertNull(cache.loadCachedResults("hashToDelete"));
    }

    @Test
    void recordRunAndGetLastCommit() {
        assertNull(cache.getLastCommit(), "No runs recorded yet");

        cache.recordRun("abc123", 50);

        assertEquals("abc123", cache.getLastCommit());

        cache.recordRun("def456", 60);

        // Should return the most recent
        assertEquals("def456", cache.getLastCommit());
    }

    @Test
    void getStatsReturnsCorrectCounts() {
        var stats = cache.getStats();
        assertEquals(0L, stats.get("cached_files"));
        assertEquals(0L, stats.get("cached_nodes"));
        assertEquals(0L, stats.get("cached_edges"));
        assertEquals(0L, stats.get("total_runs"));

        CodeNode node = new CodeNode("n1", NodeKind.CLASS, "C1");
        cache.storeResults("h1", "f1.java", "java", List.of(node), List.of());
        cache.recordRun("sha1", 1);

        stats = cache.getStats();
        assertEquals(1L, stats.get("cached_files"));
        assertEquals(1L, stats.get("cached_nodes"));
        assertEquals(0L, stats.get("cached_edges"));
        assertEquals(1L, stats.get("total_runs"));
    }

    @Test
    void clearDeletesAllData() {
        CodeNode node = new CodeNode("n1", NodeKind.CLASS, "C1");
        cache.storeResults("h1", "f1.java", "java", List.of(node), List.of());
        cache.recordRun("sha1", 1);

        cache.clear();

        var stats = cache.getStats();
        assertEquals(0L, stats.get("cached_files"));
        assertEquals(0L, stats.get("cached_nodes"));
        assertEquals(0L, stats.get("total_runs"));
    }

    @Test
    void upsertOverwritesPreviousData() {
        CodeNode node1 = new CodeNode("n1", NodeKind.CLASS, "Old");
        cache.storeResults("sameHash", "f1.java", "java", List.of(node1), List.of());

        CodeNode node2 = new CodeNode("n2", NodeKind.METHOD, "New");
        cache.storeResults("sameHash", "f1.java", "java", List.of(node2), List.of());

        var result = cache.loadCachedResults("sameHash");
        assertNotNull(result);
        assertEquals(1, result.nodes().size());
        assertEquals("n2", result.nodes().getFirst().getId());
    }

    @Test
    void loadCachedResultsReturnsNullForEmptyHash() {
        assertNull(cache.loadCachedResults("nonexistent"));
    }
}
