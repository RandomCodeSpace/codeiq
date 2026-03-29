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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PythonStructuresDetector extends AbstractRegexDetector {

    private static final Pattern CLASS_RE = Pattern.compile(
            "^class\\s+(\\w+)(?:\\(([^)]*)\\))?:", Pattern.MULTILINE
    );
    private static final Pattern FUNC_RE = Pattern.compile(
            "^([^\\S\\n]*)(async\\s+)?def\\s+(\\w+)\\s*\\(", Pattern.MULTILINE
    );
    private static final Pattern IMPORT_RE = Pattern.compile(
            "^(?:from\\s+([\\w.]+)\\s+)?import\\s+([\\w., ]+)", Pattern.MULTILINE
    );
    private static final Pattern DECORATOR_RE = Pattern.compile(
            "^([^\\S\\n]*)@(\\w[\\w.]*)", Pattern.MULTILINE
    );
    private static final Pattern ALL_RE = Pattern.compile(
            "__all__\\s*=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL
    );
    private static final Pattern QUOTED_NAME_RE = Pattern.compile("['\"]((\\w+))['\"]");

    @Override
    public String getName() {
        return "python_structures";
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
        String fp = ctx.filePath();
        String moduleName = ctx.moduleName();

        // Collect decorators by line number
        Map<Integer, List<String>> decoratorMap = collectDecorators(text);

        // __all__ exports
        Matcher allMatch = ALL_RE.matcher(text);
        List<String> allExports = null;
        int allMatchStart = -1;
        if (allMatch.find()) {
            allMatchStart = allMatch.start();
            String raw = allMatch.group(1);
            allExports = new ArrayList<>();
            Matcher qm = QUOTED_NAME_RE.matcher(raw);
            while (qm.find()) {
                allExports.add(qm.group(1));
            }
        }

        // Classes
        List<int[]> classRanges = new ArrayList<>(); // [nameIdx into classNames, line, indent]
        List<String> classNames = new ArrayList<>();
        Matcher classMatcher = CLASS_RE.matcher(text);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String basesStr = classMatcher.group(2);
            int line = findLineNumber(text, classMatcher.start());

            int lineStartOffset = text.lastIndexOf('\n', classMatcher.start() - 1) + 1;
            int indent = classMatcher.start() - lineStartOffset;

            classNames.add(className);
            classRanges.add(new int[]{classNames.size() - 1, line, indent});

            List<String> annotations = findDecoratorsForLine(decoratorMap, line);

            Map<String, Object> properties = new HashMap<>();
            if (basesStr != null && !basesStr.isBlank()) {
                List<String> bases = new ArrayList<>();
                for (String b : basesStr.split(",")) {
                    String trimmed = b.trim();
                    if (!trimmed.isEmpty()) {
                        bases.add(trimmed);
                    }
                }
                properties.put("bases", bases);
            }
            if (allExports != null && allExports.contains(className)) {
                properties.put("exported", true);
            }

            String nodeId = "py:" + fp + ":class:" + className;
            CodeNode node = new CodeNode();
            node.setId(nodeId);
            node.setKind(NodeKind.CLASS);
            node.setLabel(className);
            node.setFqn(className);
            node.setModule(moduleName);
            node.setFilePath(fp);
            node.setLineStart(line);
            node.setAnnotations(annotations);
            node.setProperties(properties);
            nodes.add(node);

            // EXTENDS edges
            if (basesStr != null && !basesStr.isBlank()) {
                for (String b : basesStr.split(",")) {
                    String base = b.trim();
                    if (!base.isEmpty()) {
                        CodeEdge edge = new CodeEdge();
                        edge.setId(nodeId + "->extends->" + base);
                        edge.setKind(EdgeKind.EXTENDS);
                        edge.setSourceId(nodeId);
                        edges.add(edge);
                    }
                }
            }
        }

        // Functions and methods
        Matcher funcMatcher = FUNC_RE.matcher(text);
        while (funcMatcher.find()) {
            String indentStr = funcMatcher.group(1);
            boolean isAsync = funcMatcher.group(2) != null;
            String funcName = funcMatcher.group(3);
            int line = findLineNumber(text, funcMatcher.start());
            int indentLen = indentStr.length();

            List<String> annotations = findDecoratorsForLine(decoratorMap, line);

            Map<String, Object> properties = new HashMap<>();
            if (isAsync) {
                properties.put("async", true);
            }
            if (allExports != null && allExports.contains(funcName)) {
                properties.put("exported", true);
            }

            if (indentLen == 0) {
                // Top-level function
                String nodeId = "py:" + fp + ":func:" + funcName;
                CodeNode node = new CodeNode();
                node.setId(nodeId);
                node.setKind(NodeKind.METHOD);
                node.setLabel(funcName);
                node.setFqn(funcName);
                node.setModule(moduleName);
                node.setFilePath(fp);
                node.setLineStart(line);
                node.setAnnotations(annotations);
                node.setProperties(properties);
                nodes.add(node);
            } else {
                // Check if inside a class
                String enclosingClass = findEnclosingClass(classNames, classRanges, line, indentLen);
                if (enclosingClass != null) {
                    String nodeId = "py:" + fp + ":class:" + enclosingClass + ":method:" + funcName;
                    properties.put("class", enclosingClass);
                    CodeNode node = new CodeNode();
                    node.setId(nodeId);
                    node.setKind(NodeKind.METHOD);
                    node.setLabel(enclosingClass + "." + funcName);
                    node.setFqn(enclosingClass + "." + funcName);
                    node.setModule(moduleName);
                    node.setFilePath(fp);
                    node.setLineStart(line);
                    node.setAnnotations(annotations);
                    node.setProperties(properties);
                    nodes.add(node);

                    // DEFINES edge
                    String classNodeId = "py:" + fp + ":class:" + enclosingClass;
                    CodeEdge edge = new CodeEdge();
                    edge.setId(classNodeId + "->defines->" + nodeId);
                    edge.setKind(EdgeKind.DEFINES);
                    edge.setSourceId(classNodeId);
                    edges.add(edge);
                }
            }
        }

        // Imports
        Matcher importMatcher = IMPORT_RE.matcher(text);
        while (importMatcher.find()) {
            String fromModule = importMatcher.group(1);
            String importNames = importMatcher.group(2);
            if (fromModule != null) {
                CodeEdge edge = new CodeEdge();
                edge.setId(fp + "->imports->" + fromModule);
                edge.setKind(EdgeKind.IMPORTS);
                edge.setSourceId(fp);
                edges.add(edge);
            } else {
                for (String name : importNames.split(",")) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        CodeEdge edge = new CodeEdge();
                        edge.setId(fp + "->imports->" + trimmed);
                        edge.setKind(EdgeKind.IMPORTS);
                        edge.setSourceId(fp);
                        edges.add(edge);
                    }
                }
            }
        }

        // __all__ module node
        if (allExports != null) {
            String moduleNodeId = "py:" + fp + ":module";
            CodeNode moduleNode = new CodeNode();
            moduleNode.setId(moduleNodeId);
            moduleNode.setKind(NodeKind.MODULE);
            moduleNode.setLabel(fp);
            moduleNode.setFqn(fp);
            moduleNode.setModule(moduleName);
            moduleNode.setFilePath(fp);
            moduleNode.setLineStart(findLineNumber(text, allMatchStart));
            moduleNode.getProperties().put("__all__", allExports);
            nodes.add(moduleNode);
        }

        return DetectorResult.of(nodes, edges);
    }

    private Map<Integer, List<String>> collectDecorators(String text) {
        Map<Integer, List<String>> result = new HashMap<>();
        Matcher m = DECORATOR_RE.matcher(text);
        while (m.find()) {
            int line = findLineNumber(text, m.start());
            result.computeIfAbsent(line, k -> new ArrayList<>()).add(m.group(2));
        }
        return result;
    }

    private List<String> findDecoratorsForLine(Map<Integer, List<String>> decoratorMap, int targetLine) {
        List<String> decorators = new ArrayList<>();
        int line = targetLine - 1;
        while (decoratorMap.containsKey(line)) {
            decorators.addAll(decoratorMap.get(line));
            line--;
        }
        // Reverse so top-most decorator is first
        List<String> reversed = new ArrayList<>(decorators);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private String findEnclosingClass(List<String> classNames, List<int[]> classRanges,
                                       int line, int funcIndent) {
        for (int i = classRanges.size() - 1; i >= 0; i--) {
            int[] range = classRanges.get(i);
            int startLine = range[1];
            int classIndent = range[2];
            if (line > startLine && funcIndent > classIndent) {
                return classNames.get(range[0]);
            }
        }
        return null;
    }
}
