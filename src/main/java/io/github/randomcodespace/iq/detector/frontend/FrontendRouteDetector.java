package io.github.randomcodespace.iq.detector.frontend;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FrontendRouteDetector extends AbstractRegexDetector {

    private static final Pattern REACT_ROUTE_COMPONENT = Pattern.compile(
            "<Route\\s+[^>]*?path\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?component\\s*=\\s*\\{(\\w+)\\}"
    );
    private static final Pattern REACT_ROUTE_ELEMENT = Pattern.compile(
            "<Route\\s+[^>]*?path\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?element\\s*=\\s*\\{<(\\w+)"
    );
    private static final Pattern REACT_ROUTE_BARE = Pattern.compile(
            "<Route\\s+[^>]*?path\\s*=\\s*[\"']([^\"']+)[\"']"
    );
    private static final Pattern VUE_ROUTE = Pattern.compile(
            "\\{\\s*path\\s*:\\s*['\"]([^'\"]+)['\"](?:.*?component\\s*:\\s*(\\w+))?"
    );
    private static final Pattern VUE_CREATE_ROUTER = Pattern.compile("createRouter\\s*\\(");
    private static final Pattern VUE_ROUTES_ARRAY = Pattern.compile("\\broutes\\s*:\\s*\\[");
    private static final Pattern ANGULAR_ROUTE = Pattern.compile(
            "\\{\\s*path\\s*:\\s*['\"]([^'\"]+)['\"](?:.*?component\\s*:\\s*(\\w+))?"
    );
    private static final Pattern ANGULAR_ROUTER_MODULE = Pattern.compile("RouterModule\\.for(?:Root|Child)\\s*\\(");
    private static final Pattern NEXTJS_PAGES = Pattern.compile("^pages/(.+)\\.(tsx|ts|jsx|js)$");
    private static final Pattern NEXTJS_APP = Pattern.compile("^app/(.+)/page\\.(tsx|ts|jsx|js)$");

    @Override
    public String getName() {
        return "frontend.frontend_routes";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("typescript", "javascript", "vue", "svelte");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();

        detectNextjsFileRoutes(ctx, nodes);
        detectReactRouter(ctx, text, nodes, edges);
        detectVueRouter(ctx, text, nodes, edges);
        detectAngularRouter(ctx, text, nodes, edges);

        return DetectorResult.of(nodes, edges);
    }

    private void detectNextjsFileRoutes(DetectorContext ctx, List<CodeNode> nodes) {
        String fp = ctx.filePath();

        Matcher m = NEXTJS_PAGES.matcher(fp);
        if (m.matches()) {
            String raw = m.group(1);
            String routePath = nextjsPagesPath(raw);
            nodes.add(routeNode("route:" + fp + ":nextjs:" + routePath, routePath, "nextjs", ctx, 1));
            return;
        }

        m = NEXTJS_APP.matcher(fp);
        if (m.matches()) {
            String raw = m.group(1);
            String routePath = "/" + raw.replace("\\", "/");
            nodes.add(routeNode("route:" + fp + ":nextjs:" + routePath, routePath, "nextjs", ctx, 1));
        }
    }

    private static String nextjsPagesPath(String raw) {
        String[] parts = raw.replace("\\", "/").split("/");
        List<String> partsList = new ArrayList<>(Arrays.asList(parts));
        if (!partsList.isEmpty() && "index".equals(partsList.get(partsList.size() - 1))) {
            partsList.remove(partsList.size() - 1);
        }
        return partsList.isEmpty() ? "/" : "/" + String.join("/", partsList);
    }

    private void detectReactRouter(DetectorContext ctx, String text, List<CodeNode> nodes, List<CodeEdge> edges) {
        Set<String> seenPaths = new HashSet<>();

        for (Pattern pattern : List.of(REACT_ROUTE_COMPONENT, REACT_ROUTE_ELEMENT)) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String path = m.group(1);
                String component = m.group(2);
                if (!seenPaths.add(path)) continue;
                int line = text.substring(0, m.start()).split("\n", -1).length;
                String nodeId = "route:" + ctx.filePath() + ":react:" + path;
                nodes.add(routeNode(nodeId, path, "react", ctx, line));
                CodeEdge edge = new CodeEdge();
                edge.setId(nodeId + ":renders:" + component);
                edge.setKind(EdgeKind.RENDERS);
                edge.setSourceId(nodeId);
                edge.setTarget(new CodeNode(component, NodeKind.COMPONENT, component));
                edges.add(edge);
            }
        }

        Matcher m = REACT_ROUTE_BARE.matcher(text);
        while (m.find()) {
            String path = m.group(1);
            if (!seenPaths.add(path)) continue;
            int line = text.substring(0, m.start()).split("\n", -1).length;
            nodes.add(routeNode("route:" + ctx.filePath() + ":react:" + path, path, "react", ctx, line));
        }
    }

    private void detectVueRouter(DetectorContext ctx, String text, List<CodeNode> nodes, List<CodeEdge> edges) {
        boolean hasCreateRouter = VUE_CREATE_ROUTER.matcher(text).find();
        boolean hasRoutesArray = VUE_ROUTES_ARRAY.matcher(text).find();
        if (!hasCreateRouter && !hasRoutesArray) return;

        Matcher m = VUE_ROUTE.matcher(text);
        while (m.find()) {
            String path = m.group(1);
            String component = m.group(2);
            int line = text.substring(0, m.start()).split("\n", -1).length;
            String nodeId = "route:" + ctx.filePath() + ":vue:" + path;
            nodes.add(routeNode(nodeId, path, "vue", ctx, line));
            if (component != null) {
                CodeEdge edge = new CodeEdge();
                edge.setId(nodeId + ":renders:" + component);
                edge.setKind(EdgeKind.RENDERS);
                edge.setSourceId(nodeId);
                edge.setTarget(new CodeNode(component, NodeKind.COMPONENT, component));
                edges.add(edge);
            }
        }
    }

    private void detectAngularRouter(DetectorContext ctx, String text, List<CodeNode> nodes, List<CodeEdge> edges) {
        if (!ANGULAR_ROUTER_MODULE.matcher(text).find()) return;

        Matcher m = ANGULAR_ROUTE.matcher(text);
        while (m.find()) {
            String path = m.group(1);
            String component = m.group(2);
            int line = text.substring(0, m.start()).split("\n", -1).length;
            String nodeId = "route:" + ctx.filePath() + ":angular:" + path;
            nodes.add(routeNode(nodeId, path, "angular", ctx, line));
            if (component != null) {
                CodeEdge edge = new CodeEdge();
                edge.setId(nodeId + ":renders:" + component);
                edge.setKind(EdgeKind.RENDERS);
                edge.setSourceId(nodeId);
                edge.setTarget(new CodeNode(component, NodeKind.COMPONENT, component));
                edges.add(edge);
            }
        }
    }

    private static CodeNode routeNode(String nodeId, String path, String framework, DetectorContext ctx, int line) {
        CodeNode node = new CodeNode();
        node.setId(nodeId);
        node.setKind(NodeKind.ENDPOINT);
        node.setLabel("route " + path);
        node.setFqn(ctx.filePath() + "::route:" + path);
        node.setFilePath(ctx.filePath());
        node.setLineStart(line);
        node.getProperties().put("protocol", "frontend_route");
        node.getProperties().put("framework", framework);
        node.getProperties().put("route_path", path);
        return node;
    }
}
