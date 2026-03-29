package io.github.randomcodespace.iq.flow;

import io.github.randomcodespace.iq.flow.FlowModels.FlowDiagram;
import io.github.randomcodespace.iq.graph.GraphStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Core engine for generating and rendering architecture flow diagrams from an OSSCodeIQ graph.
 *
 * <p>All consumers (CLI, HTTP API, MCP tool, HTML UI) call the same methods.
 * FlowDiagram is the single source of truth -- renderers only change format, never data.</p>
 */
@Service
@ConditionalOnBean(GraphStore.class)
public class FlowEngine {

    /**
     * Available views and their builders.
     */
    public static final List<String> AVAILABLE_VIEWS = List.of("overview", "ci", "deploy", "runtime", "auth");

    private static final Map<String, Function<GraphStore, FlowDiagram>> VIEW_BUILDERS = Map.of(
            "overview", FlowViews::buildOverview,
            "ci", FlowViews::buildCiView,
            "deploy", FlowViews::buildDeployView,
            "runtime", FlowViews::buildRuntimeView,
            "auth", FlowViews::buildAuthView
    );

    private final GraphStore store;

    public FlowEngine(GraphStore store) {
        this.store = store;
    }

    /**
     * Generate a single flow view diagram.
     *
     * @param view the view name (overview, ci, deploy, runtime, auth)
     * @return the generated FlowDiagram
     * @throws IllegalArgumentException if the view is unknown
     */
    public FlowDiagram generate(String view) {
        var builder = VIEW_BUILDERS.get(view);
        if (builder == null) {
            throw new IllegalArgumentException(
                    "Unknown view: " + view + ". Available: " + String.join(", ", AVAILABLE_VIEWS));
        }
        return builder.apply(store);
    }

    /**
     * Generate all views. Used for HTML interactive output.
     */
    public Map<String, FlowDiagram> generateAll() {
        var result = new LinkedHashMap<String, FlowDiagram>();
        for (var viewName : AVAILABLE_VIEWS) {
            result.put(viewName, generate(viewName));
        }
        return result;
    }

    /**
     * Render a diagram to string.
     *
     * @param diagram the FlowDiagram to render
     * @param format  output format: "mermaid", "json", or "html"
     * @return the rendered string
     */
    public String render(FlowDiagram diagram, String format) {
        return switch (format) {
            case "mermaid" -> FlowRenderer.renderMermaid(diagram);
            case "json" -> FlowRenderer.renderJson(diagram);
            default -> throw new IllegalArgumentException(
                    "Unknown format: " + format + ". Available: mermaid, json, html");
        };
    }

    /**
     * Generate all views and bake into a self-contained interactive HTML file.
     */
    public String renderInteractive(String projectName) {
        var allViews = generateAll();
        var stats = Map.<String, Object>of(
                "total_nodes", store.count(),
                "total_edges", countEdges()
        );
        return FlowRenderer.renderHtml(allViews, stats, projectName);
    }

    /**
     * Get the parent view for drill-up navigation.
     *
     * @param nodeId the node ID to find the parent context for
     * @return a map with parentView and parentId, or null if no parent context
     */
    public Map<String, Object> getParentContext(String nodeId) {
        // Check overview diagram -- each subgraph has a drill-down view
        // So we reverse-map: if a node belongs to ci/deploy/runtime/auth,
        // its parent is "overview"
        for (var viewName : List.of("ci", "deploy", "runtime", "auth")) {
            var diagram = generate(viewName);
            for (var sg : diagram.subgraphs()) {
                for (var node : sg.nodes()) {
                    if (node.id().equals(nodeId)) {
                        var result = new LinkedHashMap<String, Object>();
                        result.put("parent_view", "overview");
                        result.put("parent_subgraph", sg.id());
                        result.put("current_view", viewName);
                        return result;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get children of a specific node in a view (drill-down).
     *
     * @param view   the current view
     * @param nodeId the node to drill into
     * @return a map with child nodes, or null if no children
     */
    public Map<String, Object> getChildren(String view, String nodeId) {
        var diagram = generate(view);
        for (var sg : diagram.subgraphs()) {
            if (sg.drillDownView() != null) {
                for (var node : sg.nodes()) {
                    if (node.id().equals(nodeId)) {
                        // Drill down into the linked view
                        var childDiagram = generate(sg.drillDownView());
                        var result = new LinkedHashMap<String, Object>();
                        result.put("drill_down_view", sg.drillDownView());
                        result.put("diagram", childDiagram.toMap());
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private long countEdges() {
        return store.findAll().stream()
                .mapToLong(n -> n.getEdges().size())
                .sum();
    }
}
