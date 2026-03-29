package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FastAPIRouteDetector extends AbstractRegexDetector {

    private static final Pattern ROUTE_PATTERN = Pattern.compile(
            "@(\\w+)\\.(get|post|put|delete|patch|options|head)\\(\\s*['\"]([^'\"]+)['\"]"
            + ".*?\\)\\s*\\n(?:\\s*async\\s+)?def\\s+(\\w+)",
            Pattern.DOTALL
    );

    private static final Pattern ROUTER_PREFIX = Pattern.compile(
            "(\\w+)\\s*=\\s*APIRouter\\(.*?prefix\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.DOTALL
    );

    @Override
    public String getName() {
        return "python.fastapi_routes";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        List<CodeNode> nodes = new ArrayList<>();
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }
        String filePath = ctx.filePath();
        String moduleName = ctx.moduleName();

        // Extract router prefixes
        Map<String, String> prefixes = new HashMap<>();
        Matcher prefixMatcher = ROUTER_PREFIX.matcher(text);
        while (prefixMatcher.find()) {
            prefixes.put(prefixMatcher.group(1), prefixMatcher.group(2));
        }

        Matcher routeMatcher = ROUTE_PATTERN.matcher(text);
        while (routeMatcher.find()) {
            String routerName = routeMatcher.group(1);
            String method = routeMatcher.group(2).toUpperCase();
            String path = routeMatcher.group(3);
            String funcName = routeMatcher.group(4);

            String prefix = prefixes.getOrDefault(routerName, "");
            String fullPath = prefix + path;

            int line = findLineNumber(text, routeMatcher.start());

            String nodeId = "endpoint:" + (moduleName != null ? moduleName : "") + ":" + method + ":" + fullPath;
            CodeNode node = new CodeNode();
            node.setId(nodeId);
            node.setKind(NodeKind.ENDPOINT);
            node.setLabel(method + " " + fullPath);
            node.setFqn(filePath + "::" + funcName);
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.getProperties().put("protocol", "REST");
            node.getProperties().put("http_method", method);
            node.getProperties().put("path_pattern", fullPath);
            node.getProperties().put("framework", "fastapi");
            node.getProperties().put("router", routerName);
            nodes.add(node);
        }

        return DetectorResult.of(nodes, List.of());
    }
}
