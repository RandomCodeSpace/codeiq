package io.github.randomcodespace.iq.analyzer;

import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceDetectorTest {

    private ServiceDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ServiceDetector();
    }

    @Test
    void detectsMavenModule() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:pom.xml", NodeKind.CONFIG_FILE, "pom.xml", "services/order/pom.xml"));
        nodes.add(makeNode("ep:OrderCtrl", NodeKind.ENDPOINT, "GET /orders", "services/order/src/OrderCtrl.java"));
        nodes.add(makeNode("ent:Order", NodeKind.ENTITY, "Order", "services/order/src/Order.java"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(1, result.serviceNodes().size());
        CodeNode svc = result.serviceNodes().getFirst();
        assertEquals(NodeKind.SERVICE, svc.getKind());
        assertEquals("order", svc.getLabel());
        assertEquals("maven", svc.getProperties().get("build_tool"));
        assertEquals(1, svc.getProperties().get("endpoint_count"));
        assertEquals(1, svc.getProperties().get("entity_count"));
    }

    @Test
    void detectsNpmModule() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:pkg", NodeKind.CONFIG_FILE, "package.json", "frontend/package.json"));
        nodes.add(makeNode("comp:App", NodeKind.COMPONENT, "App", "frontend/src/App.tsx"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(1, result.serviceNodes().size());
        assertEquals("frontend", result.serviceNodes().getFirst().getLabel());
        assertEquals("npm", result.serviceNodes().getFirst().getProperties().get("build_tool"));
    }

    @Test
    void detectsGoModule() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:gomod", NodeKind.CONFIG_FILE, "go.mod", "services/auth/go.mod"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(1, result.serviceNodes().size());
        assertEquals("auth", result.serviceNodes().getFirst().getLabel());
        assertEquals("go", result.serviceNodes().getFirst().getProperties().get("build_tool"));
    }

    @Test
    void detectsGradleModule() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:gradle", NodeKind.CONFIG_FILE, "build.gradle", "api/build.gradle"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(1, result.serviceNodes().size());
        assertEquals("api", result.serviceNodes().getFirst().getLabel());
        assertEquals("gradle", result.serviceNodes().getFirst().getProperties().get("build_tool"));
    }

    @Test
    void detectsCargoModule() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:cargo", NodeKind.CONFIG_FILE, "Cargo.toml", "crates/worker/Cargo.toml"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(1, result.serviceNodes().size());
        assertEquals("worker", result.serviceNodes().getFirst().getLabel());
        assertEquals("cargo", result.serviceNodes().getFirst().getProperties().get("build_tool"));
    }

    @Test
    void detectsDotnetProject() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:csproj", NodeKind.CONFIG_FILE, "Api.csproj", "src/Api/Api.csproj"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(1, result.serviceNodes().size());
        assertEquals("Api", result.serviceNodes().getFirst().getLabel());
        assertEquals("dotnet", result.serviceNodes().getFirst().getProperties().get("build_tool"));
    }

    @Test
    void detectsMultipleModules() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:pom1", NodeKind.CONFIG_FILE, "pom.xml", "services/order/pom.xml"));
        nodes.add(makeNode("cfg:pom2", NodeKind.CONFIG_FILE, "pom.xml", "services/auth/pom.xml"));
        nodes.add(makeNode("cfg:pkg", NodeKind.CONFIG_FILE, "package.json", "frontend/package.json"));
        nodes.add(makeNode("ep:ep1", NodeKind.ENDPOINT, "GET /orders", "services/order/src/Ctrl.java"));
        nodes.add(makeNode("ep:ep2", NodeKind.ENDPOINT, "POST /login", "services/auth/src/Auth.java"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(3, result.serviceNodes().size());
        var names = result.serviceNodes().stream().map(CodeNode::getLabel).sorted().toList();
        assertEquals(List.of("auth", "frontend", "order"), names);
    }

    @Test
    void fallsBackToProjectRootWhenNoModulesDetected() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cls:Foo", NodeKind.CLASS, "Foo", "src/Foo.java"));
        nodes.add(makeNode("cls:Bar", NodeKind.CLASS, "Bar", "src/Bar.java"));

        var result = detector.detect(nodes, List.of(), "my-project");

        assertEquals(1, result.serviceNodes().size());
        assertEquals("my-project", result.serviceNodes().getFirst().getLabel());
        assertEquals("unknown", result.serviceNodes().getFirst().getProperties().get("build_tool"));
    }

    @Test
    void setsServicePropertyOnChildNodes() {
        List<CodeNode> nodes = new ArrayList<>();
        CodeNode pomNode = makeNode("cfg:pom", NodeKind.CONFIG_FILE, "pom.xml", "svc/pom.xml");
        CodeNode epNode = makeNode("ep:ep1", NodeKind.ENDPOINT, "GET /test", "svc/src/Test.java");
        nodes.add(pomNode);
        nodes.add(epNode);

        detector.detect(nodes, List.of(), "project");

        assertEquals("svc", epNode.getProperties().get("service"));
    }

    @Test
    void createsContainsEdges() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:pom", NodeKind.CONFIG_FILE, "pom.xml", "svc/pom.xml"));
        nodes.add(makeNode("cls:A", NodeKind.CLASS, "A", "svc/src/A.java"));

        var result = detector.detect(nodes, List.of(), "project");

        assertFalse(result.serviceEdges().isEmpty());
        CodeEdge containsEdge = result.serviceEdges().stream()
                .filter(e -> e.getKind() == EdgeKind.CONTAINS)
                .findFirst()
                .orElse(null);
        assertNotNull(containsEdge);
        assertEquals("service:svc", containsEdge.getSourceId());
    }

    @Test
    void deterministic() {
        List<CodeNode> nodes = new ArrayList<>();
        nodes.add(makeNode("cfg:pom1", NodeKind.CONFIG_FILE, "pom.xml", "b-svc/pom.xml"));
        nodes.add(makeNode("cfg:pom2", NodeKind.CONFIG_FILE, "pom.xml", "a-svc/pom.xml"));
        nodes.add(makeNode("ep:ep1", NodeKind.ENDPOINT, "GET /b", "b-svc/src/B.java"));
        nodes.add(makeNode("ep:ep2", NodeKind.ENDPOINT, "GET /a", "a-svc/src/A.java"));

        var result1 = detector.detect(new ArrayList<>(nodes), List.of(), "project");
        var result2 = detector.detect(new ArrayList<>(nodes), List.of(), "project");

        assertEquals(result1.serviceNodes().size(), result2.serviceNodes().size());
        for (int i = 0; i < result1.serviceNodes().size(); i++) {
            assertEquals(result1.serviceNodes().get(i).getId(), result2.serviceNodes().get(i).getId());
            assertEquals(result1.serviceNodes().get(i).getLabel(), result2.serviceNodes().get(i).getLabel());
        }
    }

    private static CodeNode makeNode(String id, NodeKind kind, String label, String filePath) {
        CodeNode node = new CodeNode(id, kind, label);
        node.setFilePath(filePath);
        return node;
    }
}
