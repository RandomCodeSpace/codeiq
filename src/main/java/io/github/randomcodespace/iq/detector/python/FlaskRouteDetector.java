package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FlaskRouteDetector extends AbstractRegexDetector {

    private static final Pattern ROUTE_PATTERN = Pattern.compile(
            "@(\\w+)\\.(route)\\(\\s*['\"]([^'\"]+)['\"]"
            + "(?:.*?methods\\s*=\\s*\\[([^\\]]+)\\])?"
            + ".*?\\)\\s*\\n\\s*def\\s+(\\w+)",
            Pattern.DOTALL
    );

    @Override
    public String getName() {
        return "python.flask_routes";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }
        String filePath = ctx.filePath();
        String moduleName = ctx.moduleName();

        Matcher routeMatcher = ROUTE_PATTERN.matcher(text);
        while (routeMatcher.find()) {
            String blueprint = routeMatcher.group(1);
            String path = routeMatcher.group(3);
            String methodsRaw = routeMatcher.group(4);
            String funcName = routeMatcher.group(5);

            List<String> methods = new ArrayList<>();
            if (methodsRaw != null) {
                for (String m : methodsRaw.split(",")) {
                    methods.add(m.trim().replace("'", "").replace("\"", ""));
                }
            } else {
                methods.add("GET");
            }

            int line = findLineNumber(text, routeMatcher.start());

            for (String method : methods) {
                String nodeId = "endpoint:" + (moduleName != null ? moduleName : "") + ":" + method + ":" + path;
                CodeNode node = new CodeNode();
                node.setId(nodeId);
                node.setKind(NodeKind.ENDPOINT);
                node.setLabel(method + " " + path);
                node.setFqn(filePath + "::" + funcName);
                node.setModule(moduleName);
                node.setFilePath(filePath);
                node.setLineStart(line);
                node.getProperties().put("protocol", "REST");
                node.getProperties().put("http_method", method);
                node.getProperties().put("path_pattern", path);
                node.getProperties().put("framework", "flask");
                node.getProperties().put("blueprint", blueprint);
                nodes.add(node);

                String classId = "class:" + filePath + "::" + blueprint;
                CodeEdge edge = new CodeEdge();
                edge.setId(classId + "->exposes->" + nodeId);
                edge.setKind(EdgeKind.EXPOSES);
                edge.setSourceId(classId);
                edges.add(edge);
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
