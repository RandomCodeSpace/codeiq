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
public class ReactComponentDetector extends AbstractRegexDetector {

    private static final Pattern EXPORT_DEFAULT_FUNC = Pattern.compile("export\\s+default\\s+function\\s+([A-Z]\\w*)\\s*\\(");
    private static final Pattern EXPORT_CONST_ARROW = Pattern.compile("export\\s+const\\s+([A-Z]\\w*)\\s*=\\s*\\(");
    private static final Pattern EXPORT_CONST_FC = Pattern.compile("export\\s+const\\s+([A-Z]\\w*)\\s*:\\s*React\\.FC");
    private static final Pattern CLASS_EXTENDS_REACT_COMPONENT = Pattern.compile("class\\s+([A-Z]\\w*)\\s+extends\\s+React\\.Component");
    private static final Pattern CLASS_EXTENDS_COMPONENT = Pattern.compile("class\\s+([A-Z]\\w*)\\s+extends\\s+Component\\b");
    private static final Pattern EXPORT_FUNC_HOOK = Pattern.compile("export\\s+function\\s+(use[A-Z]\\w*)\\s*\\(");
    private static final Pattern EXPORT_CONST_HOOK = Pattern.compile("export\\s+const\\s+(use[A-Z]\\w*)\\s*=\\s*");
    private static final Pattern JSX_TAG = Pattern.compile("<([A-Z]\\w*)\\b");

    @Override
    public String getName() {
        return "frontend.react_components";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("typescript", "javascript");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String filePath = ctx.filePath();
        List<String> componentNames = new ArrayList<>();

        // Function components
        for (Pattern pattern : List.of(EXPORT_DEFAULT_FUNC, EXPORT_CONST_ARROW, EXPORT_CONST_FC)) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String name = m.group(1);
                if (componentNames.contains(name)) continue;
                int line = text.substring(0, m.start()).split("\n", -1).length;
                CodeNode node = new CodeNode();
                node.setId("react:" + filePath + ":component:" + name);
                node.setKind(NodeKind.COMPONENT);
                node.setLabel(name);
                node.setFqn(filePath + "::" + name);
                node.setFilePath(filePath);
                node.setLineStart(line);
                node.getProperties().put("framework", "react");
                node.getProperties().put("component_type", "function");
                nodes.add(node);
                componentNames.add(name);
            }
        }

        // Class components
        for (Pattern pattern : List.of(CLASS_EXTENDS_REACT_COMPONENT, CLASS_EXTENDS_COMPONENT)) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String name = m.group(1);
                if (componentNames.contains(name)) continue;
                int line = text.substring(0, m.start()).split("\n", -1).length;
                CodeNode node = new CodeNode();
                node.setId("react:" + filePath + ":component:" + name);
                node.setKind(NodeKind.COMPONENT);
                node.setLabel(name);
                node.setFqn(filePath + "::" + name);
                node.setFilePath(filePath);
                node.setLineStart(line);
                node.getProperties().put("framework", "react");
                node.getProperties().put("component_type", "class");
                nodes.add(node);
                componentNames.add(name);
            }
        }

        // Custom hooks
        List<String> hookNames = new ArrayList<>();
        for (Pattern pattern : List.of(EXPORT_FUNC_HOOK, EXPORT_CONST_HOOK)) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String name = m.group(1);
                if (hookNames.contains(name)) continue;
                int line = text.substring(0, m.start()).split("\n", -1).length;
                CodeNode node = new CodeNode();
                node.setId("react:" + filePath + ":hook:" + name);
                node.setKind(NodeKind.HOOK);
                node.setLabel(name);
                node.setFqn(filePath + "::" + name);
                node.setFilePath(filePath);
                node.setLineStart(line);
                node.getProperties().put("framework", "react");
                nodes.add(node);
                hookNames.add(name);
            }
        }

        // RENDERS edges (JSX child components)
        Set<String> allDetected = new HashSet<>(componentNames);
        allDetected.addAll(hookNames);
        Set<String> childNames = new TreeSet<>();
        Matcher jsxM = JSX_TAG.matcher(text);
        while (jsxM.find()) {
            String tag = jsxM.group(1);
            if (!allDetected.contains(tag)) {
                childNames.add(tag);
            }
        }

        for (String comp : componentNames) {
            String sourceId = "react:" + filePath + ":component:" + comp;
            for (String child : childNames) {
                CodeEdge edge = new CodeEdge();
                edge.setId(sourceId + ":renders:" + child);
                edge.setKind(EdgeKind.RENDERS);
                edge.setSourceId(sourceId);
                edge.setTarget(new CodeNode(child, NodeKind.COMPONENT, child));
                edges.add(edge);
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
