package io.github.randomcodespace.iq.graph;

import io.github.randomcodespace.iq.flow.FlowDataSource;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade service over the Neo4j graph backend.
 * All graph access goes through this service — never access GraphRepository directly
 * from controllers or other services.
 */
@Service
@ConditionalOnBean(GraphRepository.class)
public class GraphStore implements FlowDataSource {

    private final GraphRepository repository;
    private final GraphDatabaseService graphDb;

    public GraphStore(GraphRepository repository, GraphDatabaseService graphDb) {
        this.repository = repository;
        this.graphDb = graphDb;
    }

    public CodeNode save(CodeNode node) {
        return repository.save(node);
    }

    public List<CodeNode> saveAll(Iterable<CodeNode> nodes) {
        return repository.saveAll(nodes);
    }

    public Optional<CodeNode> findById(String id) {
        return repository.findById(id);
    }

    public List<CodeNode> findAll() {
        return repository.findAll();
    }

    public List<CodeNode> findByKind(NodeKind kind) {
        return repository.findByKind(kind.getValue());
    }

    public List<CodeNode> findByLayer(String layer) {
        return repository.findByLayer(layer);
    }

    public List<CodeNode> findByModule(String module) {
        return repository.findByModule(module);
    }

    public List<CodeNode> findByFilePath(String filePath) {
        return repository.findByFilePath(filePath);
    }

    public List<CodeNode> search(String text) {
        return repository.search(text);
    }

    public List<CodeNode> search(String text, int limit) {
        return repository.search(text, limit);
    }

    public List<CodeNode> findNeighbors(String nodeId) {
        return repository.findNeighbors(nodeId);
    }

    public List<CodeNode> findOutgoingNeighbors(String nodeId) {
        return repository.findOutgoingNeighbors(nodeId);
    }

    public List<CodeNode> findIncomingNeighbors(String nodeId) {
        return repository.findIncomingNeighbors(nodeId);
    }

    public long count() {
        return repository.count();
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    public void deleteById(String id) {
        repository.deleteById(id);
    }

    // --- Graph traversal queries ---

    public List<String> findShortestPath(String source, String target) {
        return repository.findShortestPath(source, target);
    }

    public List<CodeNode> findEgoGraph(String center, int radius) {
        return repository.findEgoGraph(center, radius);
    }

    public List<CodeNode> traceImpact(String nodeId, int depth) {
        return repository.traceImpact(nodeId, depth);
    }

    public List<List<String>> findCycles(int limit) {
        return repository.findCycles(limit);
    }

    public List<CodeNode> findConsumers(String targetId) {
        return repository.findConsumers(targetId);
    }

    public List<CodeNode> findProducers(String targetId) {
        return repository.findProducers(targetId);
    }

    public List<CodeNode> findCallers(String targetId) {
        return repository.findCallers(targetId);
    }

    public List<CodeNode> findDependencies(String moduleId) {
        return repository.findDependencies(moduleId);
    }

    public List<CodeNode> findDependents(String moduleId) {
        return repository.findDependents(moduleId);
    }

    public List<CodeNode> findByKindPaginated(String kind, int offset, int limit) {
        return repository.findByKindPaginated(kind, offset, limit);
    }

    public List<CodeNode> findAllPaginated(int offset, int limit) {
        return repository.findAllPaginated(offset, limit);
    }

    public long countByKind(String kind) {
        return repository.countByKind(kind);
    }

    // --- Aggregation queries via embedded Neo4j API (bypasses SDN entity mapping) ---

    public long countEdges() {
        try (Transaction tx = graphDb.beginTx()) {
            var result = tx.execute("MATCH ()-[r:RELATES_TO]->() RETURN count(r) AS cnt");
            if (result.hasNext()) {
                return ((Number) result.next().get("cnt")).longValue();
            }
            return 0;
        }
    }

    public List<Map<String, Object>> countNodesByKind() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            var result = tx.execute("MATCH (n:CodeNode) RETURN n.kind AS kind, count(n) AS cnt");
            while (result.hasNext()) {
                var row = result.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", row.get("kind"));
                m.put("cnt", ((Number) row.get("cnt")).longValue());
                rows.add(m);
            }
        }
        return rows;
    }

    public List<Map<String, Object>> countNodesByLayer() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            var result = tx.execute(
                    "MATCH (n:CodeNode) WHERE n.layer IS NOT NULL RETURN n.layer AS layer, count(n) AS cnt");
            while (result.hasNext()) {
                var row = result.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("layer", row.get("layer"));
                m.put("cnt", ((Number) row.get("cnt")).longValue());
                rows.add(m);
            }
        }
        return rows;
    }

    public List<Map<String, Object>> findEdgesPaginated(int offset, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            var result = tx.execute(
                    "MATCH (s:CodeNode)-[r:RELATES_TO]->(t:CodeNode) "
                            + "RETURN r.id AS id, r.kind AS kind, r.sourceId AS sourceId, t.id AS targetId "
                            + "SKIP $offset LIMIT $limit",
                    Map.of("offset", offset, "limit", limit));
            while (result.hasNext()) {
                var row = result.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", row.get("id"));
                m.put("kind", row.get("kind"));
                m.put("sourceId", row.get("sourceId"));
                m.put("targetId", row.get("targetId"));
                rows.add(m);
            }
        }
        return rows;
    }

    public List<Map<String, Object>> findEdgesByKindPaginated(String kind, int offset, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Transaction tx = graphDb.beginTx()) {
            var result = tx.execute(
                    "MATCH (s:CodeNode)-[r:RELATES_TO]->(t:CodeNode) WHERE r.kind = $kind "
                            + "RETURN r.id AS id, r.kind AS kind, r.sourceId AS sourceId, t.id AS targetId "
                            + "SKIP $offset LIMIT $limit",
                    Map.of("kind", kind, "offset", offset, "limit", limit));
            while (result.hasNext()) {
                var row = result.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", row.get("id"));
                m.put("kind", row.get("kind"));
                m.put("sourceId", row.get("sourceId"));
                m.put("targetId", row.get("targetId"));
                rows.add(m);
            }
        }
        return rows;
    }

    public long countEdgesByKind(String kind) {
        try (Transaction tx = graphDb.beginTx()) {
            var result = tx.execute(
                    "MATCH ()-[r:RELATES_TO]->() WHERE r.kind = $kind RETURN count(r) AS cnt",
                    Map.of("kind", kind));
            if (result.hasNext()) {
                return ((Number) result.next().get("cnt")).longValue();
            }
            return 0;
        }
    }

    public List<CodeNode> findNodesWithoutIncoming(List<String> kinds, int offset, int limit) {
        return repository.findNodesWithoutIncoming(kinds, offset, limit);
    }
}
