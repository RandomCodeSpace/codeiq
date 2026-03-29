package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DjangoViewDetector extends AbstractRegexDetector {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:path|re_path|url)\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*(\\w[\\w.]*)"
    );

    private static final Pattern CBV_PATTERN = Pattern.compile(
            "class\\s+(\\w+)\\(([^)]*(?:View|ViewSet|Mixin)[^)]*)\\):"
    );

    @Override
    public String getName() {
        return "python.django_views";
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

        // Detect URL patterns (typically in urls.py)
        if (text.contains("urlpatterns")) {
            Matcher urlMatcher = URL_PATTERN.matcher(text);
            while (urlMatcher.find()) {
                String pathPattern = urlMatcher.group(1);
                String viewRef = urlMatcher.group(2);
                int line = findLineNumber(text, urlMatcher.start());

                String nodeId = "endpoint:" + (moduleName != null ? moduleName : "") + ":ALL:" + pathPattern;
                CodeNode node = new CodeNode();
                node.setId(nodeId);
                node.setKind(NodeKind.ENDPOINT);
                node.setLabel(pathPattern);
                node.setFqn(viewRef);
                node.setModule(moduleName);
                node.setFilePath(filePath);
                node.setLineStart(line);
                node.getProperties().put("protocol", "REST");
                node.getProperties().put("path_pattern", pathPattern);
                node.getProperties().put("framework", "django");
                node.getProperties().put("view_reference", viewRef);
                nodes.add(node);
            }
        }

        // Detect class-based views
        Matcher cbvMatcher = CBV_PATTERN.matcher(text);
        while (cbvMatcher.find()) {
            String className = cbvMatcher.group(1);
            String bases = cbvMatcher.group(2);
            int line = findLineNumber(text, cbvMatcher.start());

            String nodeId = "class:" + filePath + "::" + className;
            CodeNode node = new CodeNode();
            node.setId(nodeId);
            node.setKind(NodeKind.CLASS);
            node.setLabel(className);
            node.setFqn(filePath + "::" + className);
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.setAnnotations(List.of("extends:" + bases.trim()));
            node.getProperties().put("framework", "django");
            node.getProperties().put("stereotype", "view");
            nodes.add(node);
        }

        return DetectorResult.of(nodes, List.of());
    }
}
