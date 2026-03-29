package io.github.randomcodespace.iq.flow;

import io.github.randomcodespace.iq.flow.FlowModels.FlowDiagram;
import io.github.randomcodespace.iq.graph.GraphStore;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for FlowEngine -- verifies each view generates valid diagrams.
 */
class FlowEngineTest {

    private GraphStore store;
    private FlowEngine engine;

    @BeforeEach
    void setUp() {
        store = mock(GraphStore.class);
        engine = new FlowEngine(store);
        // Default: return empty lists
        when(store.findAll()).thenReturn(List.of());
        when(store.findByKind(NodeKind.ENDPOINT)).thenReturn(List.of());
        when(store.findByKind(NodeKind.ENTITY)).thenReturn(List.of());
        when(store.findByKind(NodeKind.CLASS)).thenReturn(List.of());
        when(store.findByKind(NodeKind.METHOD)).thenReturn(List.of());
        when(store.findByKind(NodeKind.COMPONENT)).thenReturn(List.of());
        when(store.findByKind(NodeKind.TOPIC)).thenReturn(List.of());
        when(store.findByKind(NodeKind.QUEUE)).thenReturn(List.of());
        when(store.findByKind(NodeKind.DATABASE_CONNECTION)).thenReturn(List.of());
        when(store.findByKind(NodeKind.GUARD)).thenReturn(List.of());
        when(store.findByKind(NodeKind.MIDDLEWARE)).thenReturn(List.of());
        when(store.findByKind(NodeKind.INFRA_RESOURCE)).thenReturn(List.of());
        when(store.findByKind(NodeKind.AZURE_RESOURCE)).thenReturn(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"overview", "ci", "deploy", "runtime", "auth"})
    void generateEmptyGraphProducesValidDiagram(String view) {
        FlowDiagram diagram = engine.generate(view);
        assertNotNull(diagram);
        assertNotNull(diagram.view());
        assertEquals(view, diagram.view());
        assertNotNull(diagram.subgraphs());
        assertNotNull(diagram.edges());
        assertNotNull(diagram.looseNodes());
    }

    @Test
    void generateUnknownViewThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> engine.generate("nonexistent"));
    }

    @Test
    void generateAllReturns5Views() {
        Map<String, FlowDiagram> all = engine.generateAll();
        assertEquals(5, all.size());
        assertTrue(all.containsKey("overview"));
        assertTrue(all.containsKey("ci"));
        assertTrue(all.containsKey("deploy"));
        assertTrue(all.containsKey("runtime"));
        assertTrue(all.containsKey("auth"));
    }

    @Test
    void overviewWithEndpointsCreatesAppSubgraph() {
        var endpoint = createNode("ep:test:endpoint:getUser", "GET /users", NodeKind.ENDPOINT);
        when(store.findByKind(NodeKind.ENDPOINT)).thenReturn(List.of(endpoint));

        FlowDiagram diagram = engine.generate("overview");
        assertFalse(diagram.subgraphs().isEmpty(), "Should have at least one subgraph");
        var appSg = diagram.subgraphs().stream()
                .filter(sg -> "app".equals(sg.id()))
                .findFirst();
        assertTrue(appSg.isPresent(), "Should have 'app' subgraph");
        assertFalse(appSg.get().nodes().isEmpty());
    }

    @Test
    void overviewWithCiNodesCreatesCiSubgraph() {
        var workflow = createNode("gha:ci:workflow:build", "Build", NodeKind.MODULE);
        var job = createNode("gha:ci:job:test", "Test", NodeKind.METHOD);
        when(store.findAll()).thenReturn(List.of(workflow, job));

        FlowDiagram diagram = engine.generate("overview");
        var ciSg = diagram.subgraphs().stream()
                .filter(sg -> "ci".equals(sg.id()))
                .findFirst();
        assertTrue(ciSg.isPresent(), "Should have 'ci' subgraph");
        assertEquals("ci", ciSg.get().drillDownView());
    }

    @Test
    void overviewWithGuardsAndEndpointsCreatesProtectsEdge() {
        var guard = createNode("guard:jwt", "JWT Guard", NodeKind.GUARD);
        var endpoint = createNode("ep:api:getUser", "GET /users", NodeKind.ENDPOINT);
        when(store.findByKind(NodeKind.GUARD)).thenReturn(List.of(guard));
        when(store.findByKind(NodeKind.ENDPOINT)).thenReturn(List.of(endpoint));

        FlowDiagram diagram = engine.generate("overview");
        assertTrue(diagram.edges().stream()
                        .anyMatch(e -> "protects".equals(e.label()) && "thick".equals(e.style())),
                "Should have protects edge");
    }

    @Test
    void ciViewGroupsJobsByWorkflow() {
        var workflow = createNode("gha:ci:workflow:build", "Build CI", NodeKind.MODULE);
        var job1 = createNode("gha:ci:job:lint", "Lint", NodeKind.METHOD);
        job1.setModule("gha:ci:workflow:build");
        var job2 = createNode("gha:ci:job:test", "Test", NodeKind.METHOD);
        job2.setModule("gha:ci:workflow:build");
        when(store.findAll()).thenReturn(List.of(workflow, job1, job2));

        FlowDiagram diagram = engine.generate("ci");
        assertEquals("ci", diagram.view());
        assertEquals("TD", diagram.direction());
        // Should have a subgraph for the workflow
        assertTrue(diagram.subgraphs().stream()
                .anyMatch(sg -> sg.id().contains("gha")));
    }

    @Test
    void deployViewGroupsByTechnology() {
        var k8sNode = createNode("k8s:default:deployment:api", "API Deployment", NodeKind.INFRA_RESOURCE);
        var dockerNode = createNode("compose:web:service", "Web Service", NodeKind.INFRA_RESOURCE);
        when(store.findAll()).thenReturn(List.of(k8sNode, dockerNode));
        when(store.findByKind(NodeKind.INFRA_RESOURCE)).thenReturn(List.of(k8sNode, dockerNode));

        FlowDiagram diagram = engine.generate("deploy");
        assertEquals("deploy", diagram.view());
        assertTrue(diagram.subgraphs().stream().anyMatch(sg -> "k8s".equals(sg.id())));
        assertTrue(diagram.subgraphs().stream().anyMatch(sg -> "compose".equals(sg.id())));
    }

    @Test
    void runtimeViewGroupsByLayer() {
        var endpoint = createNode("ep:api:getUser", "GET /users", NodeKind.ENDPOINT);
        endpoint.getProperties().put("layer", "backend");
        var entity = createNode("entity:User", "User", NodeKind.ENTITY);
        when(store.findByKind(NodeKind.ENDPOINT)).thenReturn(List.of(endpoint));
        when(store.findByKind(NodeKind.ENTITY)).thenReturn(List.of(entity));

        FlowDiagram diagram = engine.generate("runtime");
        assertEquals("runtime", diagram.view());
        assertTrue(diagram.subgraphs().stream().anyMatch(sg -> "backend".equals(sg.id())));
        assertTrue(diagram.subgraphs().stream().anyMatch(sg -> "data".equals(sg.id())));
    }

    @Test
    void authViewShowsCoverage() {
        var guard = createNode("guard:jwt", "JWT Guard", NodeKind.GUARD);
        guard.getProperties().put("auth_type", "jwt");
        var protectedEp = createNode("ep:api:secure", "GET /secure", NodeKind.ENDPOINT);
        var unprotectedEp = createNode("ep:api:public", "GET /public", NodeKind.ENDPOINT);

        // Create a protects edge
        var protectsEdge = new CodeEdge("edge:protects:1", EdgeKind.PROTECTS, guard.getId(), protectedEp);
        guard.setEdges(new ArrayList<>(List.of(protectsEdge)));

        when(store.findByKind(NodeKind.GUARD)).thenReturn(List.of(guard));
        when(store.findByKind(NodeKind.ENDPOINT)).thenReturn(List.of(protectedEp, unprotectedEp));
        when(store.findAll()).thenReturn(List.of(guard, protectedEp, unprotectedEp));

        FlowDiagram diagram = engine.generate("auth");
        assertEquals("auth", diagram.view());
        assertNotNull(diagram.stats().get("coverage_pct"));
        assertEquals(1, diagram.stats().get("protected"));
        assertEquals(1, diagram.stats().get("unprotected"));
    }

    @Test
    void renderMermaidFormat() {
        FlowDiagram diagram = engine.generate("overview");
        String mermaid = engine.render(diagram, "mermaid");
        assertTrue(mermaid.startsWith("graph "));
    }

    @Test
    void renderJsonFormat() {
        FlowDiagram diagram = engine.generate("overview");
        String json = engine.render(diagram, "json");
        assertTrue(json.contains("\"view\""));
        assertTrue(json.contains("\"overview\""));
    }

    @Test
    void renderUnknownFormatThrows() {
        FlowDiagram diagram = engine.generate("overview");
        assertThrows(IllegalArgumentException.class, () -> engine.render(diagram, "xml"));
    }

    @Test
    void getParentContextReturnsNullForUnknownNode() {
        assertNull(engine.getParentContext("nonexistent_node_id"));
    }

    @Test
    void getChildrenReturnsNullForUnknownNode() {
        assertNull(engine.getChildren("overview", "nonexistent_node_id"));
    }

    @Test
    void determinismTwoRunsProduceSameOutput() {
        var endpoint = createNode("ep:api:getUser", "GET /users", NodeKind.ENDPOINT);
        var entity = createNode("entity:User", "User", NodeKind.ENTITY);
        var guard = createNode("guard:jwt", "JWT Guard", NodeKind.GUARD);
        when(store.findByKind(NodeKind.ENDPOINT)).thenReturn(List.of(endpoint));
        when(store.findByKind(NodeKind.ENTITY)).thenReturn(List.of(entity));
        when(store.findByKind(NodeKind.GUARD)).thenReturn(List.of(guard));

        for (String view : FlowEngine.AVAILABLE_VIEWS) {
            FlowDiagram d1 = engine.generate(view);
            FlowDiagram d2 = engine.generate(view);
            String json1 = engine.render(d1, "json");
            String json2 = engine.render(d2, "json");
            assertEquals(json1, json2, "Determinism check failed for view: " + view);
        }
    }

    private CodeNode createNode(String id, String label, NodeKind kind) {
        var node = new CodeNode();
        node.setId(id);
        node.setLabel(label);
        node.setKind(kind);
        node.setProperties(new HashMap<>());
        node.setEdges(new ArrayList<>());
        return node;
    }
}
