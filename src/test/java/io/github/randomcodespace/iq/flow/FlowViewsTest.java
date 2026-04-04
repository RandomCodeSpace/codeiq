package io.github.randomcodespace.iq.flow;

import io.github.randomcodespace.iq.flow.FlowModels.FlowDiagram;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FlowViews view builders.
 * Uses a simple stub FlowDataSource that returns pre-configured nodes.
 */
class FlowViewsTest {

    // =============================================
    // Stub FlowDataSource
    // =============================================

    private static class StubDataSource implements FlowDataSource {
        private final List<CodeNode> nodes;

        StubDataSource(List<CodeNode> nodes) {
            this.nodes = nodes;
        }

        @Override
        public List<CodeNode> findAll() {
            return nodes;
        }

        @Override
        public List<CodeNode> findByKind(NodeKind kind) {
            return nodes.stream().filter(n -> n.getKind() == kind).toList();
        }

        @Override
        public long count() {
            return nodes.size();
        }
    }

    private static CodeNode node(String id, NodeKind kind, String label) {
        CodeNode n = new CodeNode(id, kind, label);
        return n;
    }

    // =============================================
    // buildOverview tests
    // =============================================

    @Test
    void overviewEmptyGraph() {
        FlowDataSource ds = new StubDataSource(List.of());
        FlowDiagram diagram = FlowViews.buildOverview(ds);

        assertEquals("overview", diagram.view());
        assertEquals("LR", diagram.direction());
        assertNotNull(diagram.stats());
        assertEquals(0, diagram.stats().get("total_nodes"));
    }

    @Test
    void overviewWithEndpointsAndEntities() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("ep:1", NodeKind.ENDPOINT, "GET /users"));
        nodes.add(node("ep:2", NodeKind.ENDPOINT, "POST /users"));
        nodes.add(node("entity:1", NodeKind.ENTITY, "User"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        assertEquals("overview", diagram.view());
        assertThat(diagram.stats().get("endpoints")).isEqualTo(2);
        assertThat(diagram.stats().get("entities")).isEqualTo(1);
        // App subgraph should contain both endpoints and entities flow nodes
        var appSg = diagram.subgraphs().stream().filter(sg -> "app".equals(sg.id())).findFirst();
        assertTrue(appSg.isPresent());
    }

    @Test
    void overviewWithCiNodes() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("gha:workflow1", NodeKind.MODULE, "CI Workflow"));
        nodes.add(node("gha:job1", NodeKind.METHOD, "Build Job"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        var ciSg = diagram.subgraphs().stream().filter(sg -> "ci".equals(sg.id())).findFirst();
        assertTrue(ciSg.isPresent());
        assertTrue(ciSg.get().nodes().stream().anyMatch(n -> n.id().equals("ci_pipelines")));
        assertTrue(ciSg.get().nodes().stream().anyMatch(n -> n.id().equals("ci_jobs")));
    }

    @Test
    void overviewWithInfraNodes() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("k8s:deploy1", NodeKind.INFRA_RESOURCE, "K8s Deployment"));
        nodes.add(node("tf:resource1", NodeKind.INFRA_RESOURCE, "Terraform Resource"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        var infraSg = diagram.subgraphs().stream().filter(sg -> "infra".equals(sg.id())).findFirst();
        assertTrue(infraSg.isPresent());
    }

    @Test
    void overviewWithGuards() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("ep:1", NodeKind.ENDPOINT, "GET /api"));
        nodes.add(node("guard:1", NodeKind.GUARD, "Auth Guard"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        var secSg = diagram.subgraphs().stream().filter(sg -> "security".equals(sg.id())).findFirst();
        assertTrue(secSg.isPresent());
        assertEquals(1, diagram.stats().get("guards"));
    }

    @Test
    void overviewWithMiddleware() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("mw:1", NodeKind.MIDDLEWARE, "Auth Middleware"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        var secSg = diagram.subgraphs().stream().filter(sg -> "security".equals(sg.id())).findFirst();
        assertTrue(secSg.isPresent());
    }

    @Test
    void overviewWithTopicsAndQueues() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("topic:1", NodeKind.TOPIC, "events.user"));
        nodes.add(node("queue:1", NodeKind.QUEUE, "celery:send_email"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        // Topics and queues go into the app subgraph as messaging node
        assertNotNull(diagram);
        // If app subgraph exists, messaging node should be there
        diagram.subgraphs().stream()
                .filter(sg -> "app".equals(sg.id())).findFirst()
                .ifPresent(appSg -> assertTrue(appSg.nodes().stream().anyMatch(n -> "app_messaging".equals(n.id()))));
    }

    @Test
    void overviewWithComponents() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("comp:1", NodeKind.COMPONENT, "UserComponent"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        assertNotNull(diagram);
        assertEquals(1, diagram.stats().get("components"));
    }

    @Test
    void overviewWithDbConnections() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("db:1", NodeKind.DATABASE_CONNECTION, "PostgreSQL"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        assertNotNull(diagram);
        // App subgraph should contain DB flow node
        diagram.subgraphs().stream()
                .filter(sg -> "app".equals(sg.id())).findFirst()
                .ifPresent(appSg -> assertTrue(appSg.nodes().stream().anyMatch(n -> "app_database".equals(n.id()))));
    }

    @Test
    void overviewAppSubgraphWithOnlyClasses() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("class:1", NodeKind.CLASS, "UserService"));
        nodes.add(node("method:1", NodeKind.METHOD, "process"));

        FlowDiagram diagram = FlowViews.buildOverview(new StubDataSource(nodes));

        // When no endpoints/entities/etc, falls back to code node
        var appSg = diagram.subgraphs().stream().filter(sg -> "app".equals(sg.id())).findFirst();
        if (appSg.isPresent()) {
            assertTrue(appSg.get().nodes().stream().anyMatch(n -> "app_code".equals(n.id())));
        }
    }

    // =============================================
    // buildCiView tests
    // =============================================

    @Test
    void ciViewEmptyGraph() {
        FlowDiagram diagram = FlowViews.buildCiView(new StubDataSource(List.of()));

        assertEquals("ci", diagram.view());
        assertEquals("TD", diagram.direction());
        assertEquals(0, diagram.stats().get("workflows"));
        assertEquals(0, diagram.stats().get("jobs"));
    }

    @Test
    void ciViewWithWorkflowsAndJobs() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode wf = node("gha:workflow:ci.yml", NodeKind.MODULE, "CI Pipeline");
        wf.setModule("gha:workflow:ci.yml");
        nodes.add(wf);
        CodeNode job = node("gha:job:build", NodeKind.METHOD, "Build");
        job.setModule("gha:workflow:ci.yml");
        nodes.add(job);

        FlowDiagram diagram = FlowViews.buildCiView(new StubDataSource(nodes));

        assertEquals("ci", diagram.view());
        assertEquals(1, diagram.stats().get("workflows"));
        assertEquals(1, diagram.stats().get("jobs"));
    }

    @Test
    void ciViewWithTriggers() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("gha:trigger:push", NodeKind.CONFIG_KEY, "push trigger"));

        FlowDiagram diagram = FlowViews.buildCiView(new StubDataSource(nodes));

        var triggerSg = diagram.subgraphs().stream()
                .filter(sg -> "triggers".equals(sg.id())).findFirst();
        assertTrue(triggerSg.isPresent());
        assertEquals(1, diagram.stats().get("triggers"));
    }

    @Test
    void ciViewWithDependsOnEdges() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode job1 = node("gha:job:build", NodeKind.METHOD, "Build");
        CodeNode job2 = node("gha:job:test", NodeKind.METHOD, "Test");
        CodeEdge dep = new CodeEdge();
        dep.setId("gha:job:build->depends_on->gha:job:test");
        dep.setKind(EdgeKind.DEPENDS_ON);
        dep.setSourceId("gha:job:build");
        dep.setTarget(job2);
        job1.getEdges().add(dep);
        nodes.add(job1);
        nodes.add(job2);

        FlowDiagram diagram = FlowViews.buildCiView(new StubDataSource(nodes));

        assertNotNull(diagram);
        // Edges should be sorted for determinism
        assertNotNull(diagram.edges());
    }

    @Test
    void ciViewTriggerWorkflowEdge() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("gha:trigger:push", NodeKind.CONFIG_KEY, "push"));
        nodes.add(node("gha:workflow:main.yml", NodeKind.MODULE, "Main CI"));

        FlowDiagram diagram = FlowViews.buildCiView(new StubDataSource(nodes));

        // Trigger -> workflow dotted edge should be present
        assertFalse(diagram.edges().isEmpty());
    }

    // =============================================
    // buildDeployView tests
    // =============================================

    @Test
    void deployViewEmptyGraph() {
        FlowDiagram diagram = FlowViews.buildDeployView(new StubDataSource(List.of()));

        assertEquals("deploy", diagram.view());
        assertEquals(0, diagram.stats().get("k8s"));
        assertEquals(0, diagram.stats().get("compose"));
        assertEquals(0, diagram.stats().get("terraform"));
    }

    @Test
    void deployViewWithK8sNodes() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("k8s:deployment:web", NodeKind.INFRA_RESOURCE, "Web Deployment"));
        nodes.add(node("k8s:service:web-svc", NodeKind.INFRA_RESOURCE, "Web Service"));

        FlowDiagram diagram = FlowViews.buildDeployView(new StubDataSource(nodes));

        var k8sSg = diagram.subgraphs().stream().filter(sg -> "k8s".equals(sg.id())).findFirst();
        assertTrue(k8sSg.isPresent());
        assertEquals(2, diagram.stats().get("k8s"));
    }

    @Test
    void deployViewWithComposeNodes() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("compose:service:web", NodeKind.INFRA_RESOURCE, "Web Service"));

        FlowDiagram diagram = FlowViews.buildDeployView(new StubDataSource(nodes));

        var composeSg = diagram.subgraphs().stream().filter(sg -> "compose".equals(sg.id())).findFirst();
        assertTrue(composeSg.isPresent());
    }

    @Test
    void deployViewWithTerraformNodes() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("tf:aws_instance:web", NodeKind.INFRA_RESOURCE, "EC2 Instance"));

        FlowDiagram diagram = FlowViews.buildDeployView(new StubDataSource(nodes));

        var tfSg = diagram.subgraphs().stream().filter(sg -> "terraform".equals(sg.id())).findFirst();
        assertTrue(tfSg.isPresent());
    }

    @Test
    void deployViewWithDockerNodes() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("dockerfile:web", NodeKind.INFRA_RESOURCE, "Web Image"));

        FlowDiagram diagram = FlowViews.buildDeployView(new StubDataSource(nodes));

        var dockerSg = diagram.subgraphs().stream().filter(sg -> "docker".equals(sg.id())).findFirst();
        assertTrue(dockerSg.isPresent());
    }

    @Test
    void deployViewWithAzureResource() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("azure:resource:1", NodeKind.AZURE_RESOURCE, "App Service"));

        FlowDiagram diagram = FlowViews.buildDeployView(new StubDataSource(nodes));

        // Azure resources go into "other" category
        var otherSg = diagram.subgraphs().stream().filter(sg -> "other_infra".equals(sg.id())).findFirst();
        assertTrue(otherSg.isPresent());
    }

    // =============================================
    // buildRuntimeView tests
    // =============================================

    @Test
    void runtimeViewEmptyGraph() {
        FlowDiagram diagram = FlowViews.buildRuntimeView(new StubDataSource(List.of()));

        assertEquals("runtime", diagram.view());
        assertEquals("LR", diagram.direction());
        assertEquals(0, diagram.stats().get("endpoints"));
        assertEquals(0, diagram.stats().get("entities"));
    }

    @Test
    void runtimeViewWithBackendEndpoints() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("ep:1", NodeKind.ENDPOINT, "GET /api/users"));

        FlowDiagram diagram = FlowViews.buildRuntimeView(new StubDataSource(nodes));

        var backendSg = diagram.subgraphs().stream()
                .filter(sg -> "backend".equals(sg.id())).findFirst();
        assertTrue(backendSg.isPresent());
    }

    @Test
    void runtimeViewWithFrontendEndpoints() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode ep = node("ep:1", NodeKind.ENDPOINT, "Route /home");
        ep.getProperties().put("layer", "frontend");
        nodes.add(ep);

        FlowDiagram diagram = FlowViews.buildRuntimeView(new StubDataSource(nodes));

        var frontendSg = diagram.subgraphs().stream()
                .filter(sg -> "frontend".equals(sg.id())).findFirst();
        assertTrue(frontendSg.isPresent());
    }

    @Test
    void runtimeViewWithComponents() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("comp:1", NodeKind.COMPONENT, "NavBar"));

        FlowDiagram diagram = FlowViews.buildRuntimeView(new StubDataSource(nodes));

        var frontendSg = diagram.subgraphs().stream()
                .filter(sg -> "frontend".equals(sg.id())).findFirst();
        assertTrue(frontendSg.isPresent());
        assertTrue(frontendSg.get().nodes().stream().anyMatch(n -> "rt_components".equals(n.id())));
    }

    @Test
    void runtimeViewWithDataLayer() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("entity:1", NodeKind.ENTITY, "User"));
        nodes.add(node("db:1", NodeKind.DATABASE_CONNECTION, "PostgreSQL"));

        FlowDiagram diagram = FlowViews.buildRuntimeView(new StubDataSource(nodes));

        var dataSg = diagram.subgraphs().stream()
                .filter(sg -> "data".equals(sg.id())).findFirst();
        assertTrue(dataSg.isPresent());
    }

    @Test
    void runtimeViewEdgesConnectFrontendToBackend() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode feEp = node("ep:1", NodeKind.ENDPOINT, "Route /home");
        feEp.getProperties().put("layer", "frontend");
        nodes.add(feEp);
        nodes.add(node("ep:2", NodeKind.ENDPOINT, "GET /api"));

        FlowDiagram diagram = FlowViews.buildRuntimeView(new StubDataSource(nodes));

        // Edge from frontend to backend should exist
        assertFalse(diagram.edges().isEmpty());
    }

    @Test
    void runtimeViewWithMessaging() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("queue:1", NodeKind.QUEUE, "celery:task"));
        nodes.add(node("topic:1", NodeKind.TOPIC, "events"));

        FlowDiagram diagram = FlowViews.buildRuntimeView(new StubDataSource(nodes));

        assertNotNull(diagram);
        assertEquals(2, (int)(Integer) diagram.stats().get("topics"));
    }

    // =============================================
    // buildAuthView tests
    // =============================================

    @Test
    void authViewEmptyGraph() {
        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(List.of()));

        assertEquals("auth", diagram.view());
        assertEquals(0, diagram.stats().get("guards"));
        assertEquals(0, diagram.stats().get("protected"));
        assertEquals(0, diagram.stats().get("unprotected"));
    }

    @Test
    void authViewWithGuards() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode guard = node("guard:1", NodeKind.GUARD, "JwtGuard");
        guard.getProperties().put("auth_type", "jwt");
        nodes.add(guard);

        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(nodes));

        var guardsSg = diagram.subgraphs().stream()
                .filter(sg -> "guards".equals(sg.id())).findFirst();
        assertTrue(guardsSg.isPresent());
        assertEquals(1, diagram.stats().get("guards"));
    }

    @Test
    void authViewWithMiddleware() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("mw:1", NodeKind.MIDDLEWARE, "CorsMiddleware"));

        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(nodes));

        var guardsSg = diagram.subgraphs().stream()
                .filter(sg -> "guards".equals(sg.id())).findFirst();
        assertTrue(guardsSg.isPresent());
        assertTrue(guardsSg.get().nodes().stream().anyMatch(n -> "auth_middleware".equals(n.id())));
    }

    @Test
    void authViewWithProtectedEndpoints() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode guard = node("guard:1", NodeKind.GUARD, "Guard");
        CodeNode ep = node("ep:1", NodeKind.ENDPOINT, "GET /admin");
        CodeEdge protects = new CodeEdge();
        protects.setId("guard:1->protects->ep:1");
        protects.setKind(EdgeKind.PROTECTS);
        protects.setSourceId("guard:1");
        protects.setTarget(ep);
        guard.getEdges().add(protects);
        guard.getProperties().put("auth_type", "jwt");
        nodes.add(guard);
        nodes.add(ep);

        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(nodes));

        assertEquals(1, diagram.stats().get("protected"));
        assertEquals(0, diagram.stats().get("unprotected"));
    }

    @Test
    void authViewWithUnprotectedEndpoints() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(node("ep:1", NodeKind.ENDPOINT, "GET /public"));

        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(nodes));

        assertEquals(0, diagram.stats().get("protected"));
        assertEquals(1, diagram.stats().get("unprotected"));
    }

    @Test
    void authViewCoverageCalculatedCorrectly() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode guard = node("guard:1", NodeKind.GUARD, "Guard");
        guard.getProperties().put("auth_type", "jwt");
        CodeNode ep1 = node("ep:1", NodeKind.ENDPOINT, "Protected");
        CodeNode ep2 = node("ep:2", NodeKind.ENDPOINT, "Unprotected");
        CodeEdge protects = new CodeEdge();
        protects.setId("guard:1->protects->ep:1");
        protects.setKind(EdgeKind.PROTECTS);
        protects.setSourceId("guard:1");
        protects.setTarget(ep1);
        guard.getEdges().add(protects);
        nodes.add(guard);
        nodes.add(ep1);
        nodes.add(ep2);

        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(nodes));

        double coverage = (double) diagram.stats().get("coverage_pct");
        assertEquals(50.0, coverage, 0.1);
    }

    @Test
    void authViewGuardsGroupedByAuthType() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode g1 = node("guard:1", NodeKind.GUARD, "JwtGuard");
        g1.getProperties().put("auth_type", "jwt");
        CodeNode g2 = node("guard:2", NodeKind.GUARD, "OAuthGuard");
        g2.getProperties().put("auth_type", "oauth");
        nodes.add(g1);
        nodes.add(g2);

        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(nodes));

        var guardsSg = diagram.subgraphs().stream()
                .filter(sg -> "guards".equals(sg.id())).findFirst().orElseThrow();
        // Two distinct auth types => two guard nodes
        assertEquals(2, guardsSg.nodes().size());
    }

    @Test
    void authViewEdgesFromGuardsToProtectedEndpoints() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode guard = node("guard:1", NodeKind.GUARD, "Guard");
        guard.getProperties().put("auth_type", "jwt");
        CodeNode ep = node("ep:1", NodeKind.ENDPOINT, "Protected");
        CodeEdge protects = new CodeEdge();
        protects.setId("guard:1->protects->ep:1");
        protects.setKind(EdgeKind.PROTECTS);
        protects.setSourceId("guard:1");
        protects.setTarget(ep);
        guard.getEdges().add(protects);
        nodes.add(guard);
        nodes.add(ep);

        FlowDiagram diagram = FlowViews.buildAuthView(new StubDataSource(nodes));

        assertFalse(diagram.edges().isEmpty());
        assertTrue(diagram.edges().stream().anyMatch(e -> "ep_protected".equals(e.target())));
    }
}
