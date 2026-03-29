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
public class SQLAlchemyModelDetector extends AbstractRegexDetector {

    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "class\\s+(\\w+)\\(([^)]*(?:Base|Model|DeclarativeBase)[^)]*)\\):"
    );
    private static final Pattern TABLE_NAME = Pattern.compile(
            "__tablename__\\s*=\\s*['\"]((\\w+))['\"]"
    );
    private static final Pattern COLUMN_PATTERN = Pattern.compile(
            "(\\w+)\\s*(?::\\s*Mapped\\[.*?\\])?\\s*=\\s*(?:Column|mapped_column)\\("
    );
    private static final Pattern RELATIONSHIP_PATTERN = Pattern.compile(
            "(\\w+)\\s*(?::\\s*Mapped\\[.*?\\])?\\s*=\\s*relationship\\(\\s*['\"]((\\w+))['\"]"
    );
    private static final Pattern NEXT_CLASS_RE = Pattern.compile("\\nclass\\s+\\w+");

    @Override
    public String getName() {
        return "python.sqlalchemy_models";
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

        Matcher modelMatcher = MODEL_PATTERN.matcher(text);
        while (modelMatcher.find()) {
            String className = modelMatcher.group(1);
            int line = findLineNumber(text, modelMatcher.start());

            // Class body boundaries
            int classStart = modelMatcher.start();
            Matcher nextClassMatcher = NEXT_CLASS_RE.matcher(text.substring(modelMatcher.end()));
            String classBody;
            if (nextClassMatcher.find()) {
                classBody = text.substring(classStart, modelMatcher.end() + nextClassMatcher.start());
            } else {
                classBody = text.substring(classStart);
            }

            // Extract table name
            Matcher tableMatch = TABLE_NAME.matcher(classBody);
            String tableName = tableMatch.find() ? tableMatch.group(1) : className.toLowerCase() + "s";

            // Extract columns
            List<String> columns = new ArrayList<>();
            Matcher colMatcher = COLUMN_PATTERN.matcher(classBody);
            while (colMatcher.find()) {
                columns.add(colMatcher.group(1));
            }

            String nodeId = "entity:" + (moduleName != null ? moduleName : "") + ":" + className;
            CodeNode node = new CodeNode();
            node.setId(nodeId);
            node.setKind(NodeKind.ENTITY);
            node.setLabel(className);
            node.setFqn(filePath + "::" + className);
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.getProperties().put("table_name", tableName);
            node.getProperties().put("columns", columns);
            node.getProperties().put("framework", "sqlalchemy");
            nodes.add(node);

            // Relationships
            Matcher relMatcher = RELATIONSHIP_PATTERN.matcher(classBody);
            while (relMatcher.find()) {
                String targetClass = relMatcher.group(2);
                String targetId = "entity:" + (moduleName != null ? moduleName : "") + ":" + targetClass;
                CodeEdge edge = new CodeEdge();
                edge.setId(nodeId + "->maps_to->" + targetId);
                edge.setKind(EdgeKind.MAPS_TO);
                edge.setSourceId(nodeId);
                edges.add(edge);
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
