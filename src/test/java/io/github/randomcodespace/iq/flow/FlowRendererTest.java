package io.github.randomcodespace.iq.flow;

import io.github.randomcodespace.iq.flow.FlowModels.FlowDiagram;
import io.github.randomcodespace.iq.flow.FlowModels.FlowEdge;
import io.github.randomcodespace.iq.flow.FlowModels.FlowNode;
import io.github.randomcodespace.iq.flow.FlowModels.FlowSubgraph;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FlowRenderer -- Mermaid, JSON, and HTML rendering.
 */
class FlowRendererTest {

    @Test
    void renderMermaidEmptyDiagram() {
        var diagram = new FlowDiagram("Test", "overview");
        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.startsWith("graph LR"), "Should start with graph direction");
        assertTrue(mermaid.contains("classDef success"));
    }

    @Test
    void renderMermaidWithSubgraphs() {
        var node = new FlowNode("ep_1", "GET /users", "endpoint");
        var sg = new FlowSubgraph("app", "Application", List.of(node), "runtime");
        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(sg), List.of(), List.of(), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.contains("subgraph app[\"Application\"]"));
        assertTrue(mermaid.contains("ep_1{{\"GET /users\"}}"));
    }

    @Test
    void renderMermaidWithEdges() {
        var node1 = new FlowNode("n1", "Source", "service");
        var node2 = new FlowNode("n2", "Target", "service");
        var edge = new FlowEdge("n1", "n2", "calls");
        var sg = new FlowSubgraph("sg1", "Services", List.of(node1, node2));
        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(sg), List.of(), List.of(edge), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.contains("n1 -->|calls| n2"));
    }

    @Test
    void renderMermaidDottedEdge() {
        var edge = new FlowEdge("a", "b", null, "dotted");
        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(), List.of(), List.of(edge), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.contains("a -.-> b"));
    }

    @Test
    void renderMermaidThickEdge() {
        var edge = new FlowEdge("a", "b", "protects", "thick");
        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(), List.of(), List.of(edge), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.contains("a ==>|protects| b"));
    }

    @Test
    void renderMermaidStyleClasses() {
        var node = new FlowNode("n1", "Protected", "endpoint", "success", Map.of());
        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(), List.of(node), List.of(), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.contains(":::success"));
    }

    @Test
    void renderMermaidNodeShapes() {
        // Trigger -> stadium
        var trigger = new FlowNode("t1", "Push", "trigger");
        // Entity -> cylinder
        var entity = new FlowNode("e1", "User", "entity");
        // Guard -> flag
        var guard = new FlowNode("g1", "JWT", "guard");

        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(), List.of(trigger, entity, guard), List.of(), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.contains("t1([\"Push\"])"), "Trigger should be stadium shape");
        assertTrue(mermaid.contains("e1[(\"User\")]"), "Entity should be cylinder shape");
        assertTrue(mermaid.contains("g1>\"JWT\"]"), "Guard should be flag shape");
    }

    @Test
    void renderMermaidEscapesSpecialChars() {
        var node = new FlowNode("n1", "Test <html> {json} [array]", "service");
        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(), List.of(node), List.of(), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertFalse(mermaid.contains("<html>"), "Should escape angle brackets");
        assertFalse(mermaid.contains("{json}"), "Should escape curly braces");
    }

    @Test
    void renderMermaidSanitizesIds() {
        var node = new FlowNode("ep:api:getUser", "GET /users", "endpoint");
        var diagram = new FlowDiagram("Test", "overview", "LR",
                List.of(), List.of(node), List.of(), Map.of());

        String mermaid = FlowRenderer.renderMermaid(diagram);
        assertTrue(mermaid.contains("ep_api_getUser"), "Should sanitize colons in IDs");
    }

    @Test
    void renderJsonEmptyDiagram() {
        var diagram = new FlowDiagram("Test", "overview");
        String json = FlowRenderer.renderJson(diagram);
        assertTrue(json.contains("\"view\" : \"overview\""));
        assertTrue(json.contains("\"subgraphs\""));
        assertTrue(json.contains("\"loose_nodes\""));
        assertTrue(json.contains("\"edges\""));
    }

    @Test
    void renderJsonContainsAllFields() {
        var node = new FlowNode("n1", "Test", "service", Map.of("count", 5));
        var edge = new FlowEdge("n1", "n2", "calls");
        var sg = new FlowSubgraph("sg1", "Group", List.of(node), "detail");
        var stats = new LinkedHashMap<String, Object>();
        stats.put("total", 42);

        var diagram = new FlowDiagram("Title", "overview", "LR",
                List.of(sg), List.of(), List.of(edge), stats);

        String json = FlowRenderer.renderJson(diagram);
        assertTrue(json.contains("\"title\" : \"Title\""));
        assertTrue(json.contains("\"id\" : \"n1\""));
        assertTrue(json.contains("\"source\" : \"n1\""));
        assertTrue(json.contains("\"drill_down_view\" : \"detail\""));
        assertTrue(json.contains("\"total\" : 42"));
    }

    @Test
    void renderHtmlContainsVendorJs() {
        var diagram = new FlowDiagram("Test", "overview");
        var views = Map.of("overview", diagram);
        var stats = Map.<String, Object>of("total_nodes", 10, "total_edges", 5);

        String html = FlowRenderer.renderHtml(views, stats, "TestProject");
        assertTrue(html.contains("<!DOCTYPE html>"), "Should contain HTML doctype");
        assertTrue(html.contains("OSSCodeIQ"), "Should contain OSSCodeIQ branding");
        // Vendor JS should be inlined (placeholders replaced)
        assertFalse(html.contains("{{VENDOR_CYTOSCAPE}}"), "Cytoscape placeholder should be replaced");
        assertFalse(html.contains("{{VENDOR_DAGRE}}"), "Dagre placeholder should be replaced");
        assertFalse(html.contains("{{VENDOR_CYTOSCAPE_DAGRE}}"), "Cytoscape-dagre placeholder should be replaced");
        assertFalse(html.contains("{{VIEWS_DATA}}"), "Views data placeholder should be replaced");
        assertFalse(html.contains("{{STATS}}"), "Stats placeholder should be replaced");
        assertFalse(html.contains("{{PROJECT_NAME}}"), "Project name placeholder should be replaced");
    }

    @Test
    void renderHtmlIsSelfContained() {
        var diagram = new FlowDiagram("Test", "overview");
        var views = Map.of("overview", diagram);
        var stats = Map.<String, Object>of("total_nodes", 0, "total_edges", 0);

        String html = FlowRenderer.renderHtml(views, stats, "MyProject");
        // Must contain the inlined JS (cytoscape is large, so check for a known substring)
        assertTrue(html.contains("cytoscape"), "Should contain inlined cytoscape JS");
        assertTrue(html.contains("dagre"), "Should contain inlined dagre JS");
        // No CDN links
        assertFalse(html.contains("cdn."), "Should not contain CDN links");
    }

    @Test
    void sanitizeIdReplacesNonWordChars() {
        assertEquals("abc_def_ghi", FlowRenderer.sanitizeId("abc:def:ghi"));
        assertEquals("no_spaces", FlowRenderer.sanitizeId("no spaces"));
        assertEquals("keep_underscores", FlowRenderer.sanitizeId("keep_underscores"));
    }

    @Test
    void escapeLabelEscapesAllSpecialChars() {
        // Test individual special characters are escaped
        assertFalse(FlowRenderer.escapeLabel("<html>").contains("<"));
        assertFalse(FlowRenderer.escapeLabel("{obj}").contains("{"));
        assertFalse(FlowRenderer.escapeLabel("[arr]").contains("["));
        assertFalse(FlowRenderer.escapeLabel("(par)").contains("("));
        assertFalse(FlowRenderer.escapeLabel("|pipe|").contains("|"));

        // Test combined string does not contain raw special chars
        String escaped = FlowRenderer.escapeLabel("A<B>C");
        assertTrue(escaped.contains("&#60;"));
        assertTrue(escaped.contains("&#62;"));
    }

    @Test
    void escapeLabelHandlesNull() {
        assertEquals("", FlowRenderer.escapeLabel(null));
    }
}
