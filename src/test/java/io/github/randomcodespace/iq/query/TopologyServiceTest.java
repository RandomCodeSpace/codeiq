package io.github.randomcodespace.iq.query;

import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class TopologyServiceTest {

    private TopologyService service;
    private List<CodeNode> nodes;
    private List<CodeEdge> edges;

    @BeforeEach
    void setUp() {
        service = new TopologyService();
        nodes = new ArrayList<>();
        edges = new ArrayList<>();

        // Build a multi-service topology:
        // order-service -> auth-service (CALLS)
        // order-service -> kafka:order.created (PRODUCES)
        // notification-service -> kafka:order.created (CONSUMES)
        // order-service -> postgres:orders_db (QUERIES)

        // Service nodes
        nodes.add(makeService("order-service", "maven", 2, 1));
        nodes.add(makeService("auth-service", "maven", 1, 0));
        nodes.add(makeService("notification-service", "npm", 0, 0));

        // Endpoints in services
        CodeNode ep1 = makeNode("ep:order:get", NodeKind.ENDPOINT, "GET /orders", "order-service");
        CodeNode ep2 = makeNode("ep:order:create", NodeKind.ENDPOINT, "POST /orders", "order-service");
        CodeNode ep3 = makeNode("ep:auth:login", NodeKind.ENDPOINT, "POST /login", "auth-service");
        CodeNode ent1 = makeNode("ent:order", NodeKind.ENTITY, "Order", "order-service");
        CodeNode db1 = makeNode("db:orders", NodeKind.DATABASE_CONNECTION, "PostgreSQL:orders_db", "order-service");
        CodeNode topic1 = makeNode("topic:created", NodeKind.TOPIC, "order.created", "order-service");
        CodeNode guard1 = makeNode("guard:jwt", NodeKind.GUARD, "JwtGuard", "auth-service");
        CodeNode handler1 = makeNode("handler:notify", NodeKind.METHOD, "handleOrderCreated", "notification-service");

        nodes.addAll(List.of(ep1, ep2, ep3, ent1, db1, topic1, guard1, handler1));

        // Cross-service edges
        edges.add(makeEdge("e1", EdgeKind.CALLS, ep1.getId(), ep3)); // order calls auth
        edges.add(makeEdge("e2", EdgeKind.PRODUCES, ep2.getId(), topic1)); // order produces
        edges.add(makeEdge("e3", EdgeKind.CONSUMES, handler1.getId(), topic1)); // notification consumes
        edges.add(makeEdge("e4", EdgeKind.QUERIES, ep1.getId(), db1)); // order queries db

        // Intra-service edge (should NOT appear in cross-service connections)
        edges.add(makeEdge("e5", EdgeKind.CALLS, ep1.getId(), ep2));
    }

    @Test
    void getTopologyReturnsServicesAndConnections() {
        Map<String, Object> result = service.getTopology(nodes, edges);

        assertNotNull(result);
        List<Map<String, Object>> services = (List<Map<String, Object>>) result.get("services");
        assertEquals(3, services.size());

        List<Map<String, Object>> connections = (List<Map<String, Object>>) result.get("connections");
        assertFalse(connections.isEmpty());
    }

    @Test
    void serviceDetailReturnsComponents() {
        Map<String, Object> result = service.serviceDetail("order-service", nodes, edges);

        assertEquals("order-service", result.get("name"));
        List<?> endpoints = (List<?>) result.get("endpoints");
        assertEquals(2, endpoints.size());
        List<?> entities = (List<?>) result.get("entities");
        assertEquals(1, entities.size());
    }

    @Test
    void serviceDependenciesReturnsOutgoing() {
        Map<String, Object> result = service.serviceDependencies("order-service", nodes, edges);

        assertEquals("order-service", result.get("service"));
        assertTrue(((Number) result.get("count")).intValue() > 0);
    }

    @Test
    void serviceDependentsReturnsIncoming() {
        Map<String, Object> result = service.serviceDependents("auth-service", nodes, edges);

        assertEquals("auth-service", result.get("service"));
        // order-service calls auth-service
        assertTrue(((Number) result.get("count")).intValue() >= 1);
    }

    @Test
    void blastRadiusTracesDownstream() {
        Map<String, Object> result = service.blastRadius("ep:order:get", nodes, edges);

        assertEquals("ep:order:get", result.get("source"));
        assertNotNull(result.get("affected_services"));
        assertNotNull(result.get("affected_nodes"));
    }

    @Test
    void findPathBetweenServices() {
        List<Map<String, Object>> result = service.findPath(
                "order-service", "auth-service", nodes, edges);

        assertFalse(result.isEmpty());
        assertEquals("order-service", result.getFirst().get("from"));
        assertEquals("auth-service", result.getFirst().get("to"));
    }

    @Test
    void findPathReturnsEmptyWhenNoPath() {
        List<Map<String, Object>> result = service.findPath(
                "auth-service", "notification-service", nodes, edges);

        assertTrue(result.isEmpty());
    }

    @Test
    void findBottlenecksReturnsSortedByConnections() {
        List<Map<String, Object>> result = service.findBottlenecks(nodes, edges);

        assertFalse(result.isEmpty());
        // First should have the most connections
        int firstTotal = ((Number) result.getFirst().get("total_connections")).intValue();
        for (var entry : result) {
            assertTrue(firstTotal >= ((Number) entry.get("total_connections")).intValue());
        }
    }

    @Test
    void findCircularDepsDetectsCycles() {
        // Add a cycle: auth -> notification -> order -> auth
        CodeNode authNode = findByIdPrefix("ep:auth");
        CodeNode notifyNode = findByIdPrefix("handler:notify");
        CodeNode orderNode = findByIdPrefix("ep:order:get");

        edges.add(makeEdge("e6", EdgeKind.CALLS, authNode.getId(), notifyNode));
        edges.add(makeEdge("e7", EdgeKind.CALLS, notifyNode.getId(), orderNode));

        List<List<String>> cycles = service.findCircularDeps(nodes, edges);
        // Should detect the cycle
        assertFalse(cycles.isEmpty());
    }

    @Test
    void findCircularDepsReturnsEmptyWhenNoCycles() {
        List<List<String>> cycles = service.findCircularDeps(nodes, edges);
        assertTrue(cycles.isEmpty());
    }

    @Test
    void findDeadServicesFindsOrphans() {
        List<Map<String, Object>> result = service.findDeadServices(nodes, edges);

        // At least one service should have no incoming connections
        assertFalse(result.isEmpty());
        // notification-service has no incoming cross-service connections
        // (order-service has incoming via CONSUMES from notification-service)
        boolean hasNotification = result.stream()
                .anyMatch(r -> "notification-service".equals(r.get("service")));
        assertTrue(hasNotification);
    }

    @Test
    void findNodeExactMatchPriority() {
        List<Map<String, Object>> result = service.findNode("Order", nodes);

        assertFalse(result.isEmpty());
        // Exact match should come first
        assertEquals("Order", result.getFirst().get("label"));
    }

    @Test
    void findNodePartialMatch() {
        List<Map<String, Object>> result = service.findNode("order", nodes);

        assertFalse(result.isEmpty());
    }

    @Test
    void findNodeReturnsEmptyForBlankQuery() {
        assertTrue(service.findNode("", nodes).isEmpty());
        assertTrue(service.findNode(null, nodes).isEmpty());
    }

    // --- Helper methods ---

    private CodeNode makeService(String name, String buildTool, int endpoints, int entities) {
        CodeNode svc = new CodeNode("service:" + name, NodeKind.SERVICE, name);
        svc.setFilePath(".");
        Map<String, Object> props = new HashMap<>();
        props.put("build_tool", buildTool);
        props.put("endpoint_count", endpoints);
        props.put("entity_count", entities);
        svc.setProperties(props);
        return svc;
    }

    private CodeNode makeNode(String id, NodeKind kind, String label, String serviceName) {
        CodeNode node = new CodeNode(id, kind, label);
        node.setFilePath(serviceName + "/src/file.java");
        Map<String, Object> props = new HashMap<>();
        props.put("service", serviceName);
        node.setProperties(props);
        return node;
    }

    private CodeEdge makeEdge(String id, EdgeKind kind, String sourceId, CodeNode target) {
        return new CodeEdge(id, kind, sourceId, target);
    }

    private CodeNode findByIdPrefix(String prefix) {
        return nodes.stream()
                .filter(n -> n.getId().startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }
}
