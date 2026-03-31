package io.github.randomcodespace.iq.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GraphStore Cypher aggregation methods (computeAggregateStats and category methods).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphStoreAggregateStatsTest {

    @Mock
    private GraphRepository repository;

    @Mock
    private GraphDatabaseService graphDb;

    private GraphStore store;

    @BeforeEach
    void setUp() {
        store = new GraphStore(repository, graphDb);
    }

    /**
     * Creates a mock Result backed by an iterator of rows.
     * Must be called OUTSIDE of any when() chain to avoid UnfinishedStubbing.
     */
    @SafeVarargs
    private static Result buildResult(Map<String, Object>... rows) {
        Result result = mock(Result.class);
        Iterator<Map<String, Object>> iter = Arrays.asList(rows).iterator();
        when(result.hasNext()).thenAnswer(inv -> iter.hasNext());
        when(result.next()).thenAnswer(inv -> iter.next());
        return result;
    }

    /**
     * Sets up a transaction where all queries return empty by default.
     */
    private Transaction setupEmptyTx() {
        Transaction tx = mock(Transaction.class);
        // Pre-build the empty result outside of when() chain
        Result emptyResult1 = buildResult();
        Result emptyResult2 = buildResult();
        Result emptyResult3 = buildResult();
        Result emptyResult4 = buildResult();
        Result emptyResult5 = buildResult();
        Result emptyResult6 = buildResult();
        Result emptyResult7 = buildResult();
        Result emptyResult8 = buildResult();
        Result emptyResult9 = buildResult();
        Result emptyResult10 = buildResult();
        Result emptyResult11 = buildResult();
        Result emptyResult12 = buildResult();
        Result emptyResult13 = buildResult();
        Result emptyResult14 = buildResult();
        Result emptyResult15 = buildResult();

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(anyString())).thenReturn(emptyResult1, emptyResult2, emptyResult3,
                emptyResult4, emptyResult5, emptyResult6, emptyResult7, emptyResult8,
                emptyResult9, emptyResult10, emptyResult11, emptyResult12, emptyResult13,
                emptyResult14, emptyResult15);
        when(tx.execute(anyString(), anyMap())).thenReturn(emptyResult1, emptyResult2);
        return tx;
    }

    // --- computeAggregateStats ---

    @Test
    void computeAggregateStatsShouldReturnAllCategories() {
        setupEmptyTx();

        Map<String, Object> stats = store.computeAggregateStats();

        assertNotNull(stats.get("graph"));
        assertNotNull(stats.get("languages"));
        assertNotNull(stats.get("frameworks"));
        assertNotNull(stats.get("infra"));
        assertNotNull(stats.get("connections"));
        assertNotNull(stats.get("auth"));
        assertNotNull(stats.get("architecture"));
        assertEquals(7, stats.size());
    }

    // --- computeAggregateCategoryStats ---

    @Test
    void computeAggregateCategoryStatsShouldReturnGraphCategory() {
        setupEmptyTx();

        Map<String, Object> graph = store.computeAggregateCategoryStats("graph");

        assertNotNull(graph);
        assertTrue(graph.containsKey("nodes"));
        assertTrue(graph.containsKey("edges"));
        assertTrue(graph.containsKey("files"));
    }

    @Test
    void computeAggregateCategoryStatsShouldReturnNullForUnknown() {
        assertNull(store.computeAggregateCategoryStats("nonexistent"));
    }

    @Test
    void computeAggregateCategoryStatsShouldBeCaseInsensitive() {
        setupEmptyTx();
        assertNotNull(store.computeAggregateCategoryStats("GRAPH"));
    }

    // --- countDistinctFiles ---

    @Test
    void countDistinctFilesShouldReturnCount() {
        Transaction tx = mock(Transaction.class);
        Result fileResult = buildResult(Map.of("cnt", 15L));
        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(contains("count(DISTINCT n.filePath)"))).thenReturn(fileResult);

        assertEquals(15L, store.countDistinctFiles());
    }

    @Test
    void countDistinctFilesShouldReturnZeroForEmptyGraph() {
        Transaction tx = mock(Transaction.class);
        Result emptyResult = buildResult();
        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(contains("count(DISTINCT n.filePath)"))).thenReturn(emptyResult);

        assertEquals(0L, store.countDistinctFiles());
    }

    // --- computeGraphStats ---

    @Test
    void graphStatsShouldIncludeNodeEdgeFileCounts() {
        Transaction tx = mock(Transaction.class);
        Result countResult = buildResult(Map.of("cnt", 100L));
        Result edgeResult = buildResult(Map.of("cnt", 50L));
        Result fileResult = buildResult(Map.of("cnt", 20L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(anyString())).thenReturn(countResult, edgeResult, fileResult);

        Map<String, Object> graph = store.computeAggregateCategoryStats("graph");

        assertEquals(100L, graph.get("nodes"));
        assertEquals(50L, graph.get("edges"));
        assertEquals(20L, graph.get("files"));
    }

    // --- computeLanguageStats ---

    @Test
    void languageStatsShouldAggregateByLanguage() {
        Transaction tx = mock(Transaction.class);
        Result langResult = buildResult(
                Map.of("lang", "java", "cnt", 50L),
                Map.of("lang", "python", "cnt", 30L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(contains("prop_language"))).thenReturn(langResult);

        Map<String, Object> langs = store.computeAggregateCategoryStats("languages");

        assertEquals(50L, langs.get("java"));
        assertEquals(30L, langs.get("python"));
    }

    @Test
    void languageStatsShouldSkipBlankLanguages() {
        Transaction tx = mock(Transaction.class);
        Result langResult = buildResult(
                Map.of("lang", "java", "cnt", 10L),
                Map.of("lang", "  ", "cnt", 5L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(contains("prop_language"))).thenReturn(langResult);

        Map<String, Object> langs = store.computeAggregateCategoryStats("languages");

        assertEquals(10L, langs.get("java"));
        assertEquals(1, langs.size());
    }

    // --- computeFrameworkStats ---

    @Test
    void frameworkStatsShouldAggregateByFramework() {
        Transaction tx = mock(Transaction.class);
        Result fwResult = buildResult(
                Map.of("fw", "spring_boot", "cnt", 25L),
                Map.of("fw", "django", "cnt", 10L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(contains("prop_framework"))).thenReturn(fwResult);

        Map<String, Object> fws = store.computeAggregateCategoryStats("frameworks");

        assertEquals(25L, fws.get("spring_boot"));
        assertEquals(10L, fws.get("django"));
    }

    // --- computeConnectionStats ---

    @Test
    void connectionStatsShouldIncludeRestGrpcWebsocket() {
        Transaction tx = mock(Transaction.class);
        Result restResult = buildResult(
                Map.of("method", "GET", "cnt", 5L),
                Map.of("method", "POST", "cnt", 3L));
        Result grpcResult = buildResult(Map.of("cnt", 2L));
        Result wsResult = buildResult(Map.of("cnt", 1L));
        Result prodResult = buildResult(Map.of("cnt", 4L));
        Result consResult = buildResult(Map.of("cnt", 6L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(anyString())).thenReturn(restResult, grpcResult, wsResult, prodResult, consResult);

        Map<String, Object> conn = store.computeAggregateCategoryStats("connections");

        @SuppressWarnings("unchecked")
        Map<String, Object> rest = (Map<String, Object>) conn.get("rest");
        assertEquals(8L, rest.get("total"));
        assertEquals(2L, conn.get("grpc"));
        assertEquals(1L, conn.get("websocket"));
        assertEquals(4L, conn.get("producers"));
        assertEquals(6L, conn.get("consumers"));
    }

    // --- computeAuthStats ---

    @Test
    void authStatsShouldAggregateGuardsAndAuthFrameworks() {
        Transaction tx = mock(Transaction.class);
        Result guardResult = buildResult(Map.of("authType", "jwt", "cnt", 5L));
        Result fwResult = buildResult(Map.of("fw", "auth:spring_security", "cnt", 3L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(anyString())).thenReturn(guardResult, fwResult);

        Map<String, Object> auth = store.computeAggregateCategoryStats("auth");

        assertEquals(5L, auth.get("jwt"));
        assertEquals(3L, auth.get("spring_security"));
    }

    // --- computeArchitectureStats ---

    @Test
    void architectureStatsShouldCountByKind() {
        Transaction tx = mock(Transaction.class);
        Result archResult = buildResult(
                Map.of("kind", "class", "cnt", 100L),
                Map.of("kind", "interface", "cnt", 20L),
                Map.of("kind", "method", "cnt", 500L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(anyString(), anyMap())).thenReturn(archResult);

        Map<String, Object> arch = store.computeAggregateCategoryStats("architecture");

        assertEquals(100L, arch.get("classes"));
        assertEquals(20L, arch.get("interfaces"));
        assertEquals(500L, arch.get("methods"));
    }

    @Test
    void architectureStatsShouldExcludeZeroCounts() {
        Transaction tx = mock(Transaction.class);
        Result archResult = buildResult(Map.of("kind", "class", "cnt", 0L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(anyString(), anyMap())).thenReturn(archResult);

        Map<String, Object> arch = store.computeAggregateCategoryStats("architecture");

        assertFalse(arch.containsKey("classes"));
    }

    // --- computeInfraStats ---

    @Test
    void infraStatsShouldIncludeDatabasesMessagingCloud() {
        Transaction tx = mock(Transaction.class);
        Result dbResult = buildResult(Map.of("dbType", "postgresql", "cnt", 3L));
        Result msgResult = buildResult(Map.of("protocol", "kafka", "cnt", 2L));
        Result cloudResult = buildResult(Map.of("resType", "s3_bucket", "cnt", 1L));

        when(graphDb.beginTx()).thenReturn(tx);
        when(tx.execute(anyString())).thenReturn(dbResult, msgResult, cloudResult);

        Map<String, Object> infra = store.computeAggregateCategoryStats("infra");

        @SuppressWarnings("unchecked")
        Map<String, Long> databases = (Map<String, Long>) infra.get("databases");
        assertEquals(3L, databases.get("PostgreSQL"));

        @SuppressWarnings("unchecked")
        Map<String, Long> messaging = (Map<String, Long>) infra.get("messaging");
        assertEquals(2L, messaging.get("kafka"));
    }

    // --- Empty graph edge cases ---

    @Test
    void allCategoriesShouldHandleEmptyGraph() {
        setupEmptyTx();

        Map<String, Object> stats = store.computeAggregateStats();

        for (String key : List.of("graph", "languages", "frameworks", "infra",
                "connections", "auth", "architecture")) {
            assertNotNull(stats.get(key), key + " should not be null");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) stats.get("graph");
        assertEquals(0L, graph.get("nodes"));
        assertEquals(0L, graph.get("edges"));
        assertEquals(0L, graph.get("files"));
    }
}
