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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DjangoModelDetector extends AbstractRegexDetector {

    private static final Pattern DJANGO_MODEL_RE = Pattern.compile(
            "^class\\s+(\\w+)\\s*\\(\\s*[\\w.]*Model\\s*\\)", Pattern.MULTILINE
    );
    private static final Pattern FK_RE = Pattern.compile(
            "(\\w+)\\s*=\\s*models\\.(?:ForeignKey|OneToOneField)\\s*\\(\\s*[\"']?(\\w+)",
            Pattern.MULTILINE
    );
    private static final Pattern M2M_RE = Pattern.compile(
            "(\\w+)\\s*=\\s*models\\.ManyToManyField\\s*\\(\\s*[\"']?(\\w+)", Pattern.MULTILINE
    );
    private static final Pattern FIELD_RE = Pattern.compile(
            "(\\w+)\\s*=\\s*models\\.(\\w+Field)\\s*\\(", Pattern.MULTILINE
    );
    private static final Pattern META_TABLE_RE = Pattern.compile(
            "db_table\\s*=\\s*[\"'](\\w+)[\"']"
    );
    private static final Pattern META_ORDERING_RE = Pattern.compile(
            "ordering\\s*=\\s*(\\[.*?\\])"
    );
    private static final Pattern MANAGER_RE = Pattern.compile(
            "^class\\s+(\\w+)\\s*\\(\\s*[\\w.]*Manager\\s*\\)", Pattern.MULTILINE
    );
    private static final Pattern MANAGER_ASSIGNMENT_RE = Pattern.compile(
            "(\\w+)\\s*=\\s*(\\w+)\\s*\\(\\s*\\)", Pattern.MULTILINE
    );
    private static final Pattern NEXT_CLASS_RE = Pattern.compile("\\nclass\\s+\\w+");
    private static final Pattern META_CLASS_RE = Pattern.compile("class\\s+Meta\\s*:");
    private static final Pattern META_END_RE = Pattern.compile("\\n\\s{4}\\S");

    @Override
    public String getName() {
        return "python.django_models";
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

        // Detect managers first
        Map<String, String> managerNames = new HashMap<>();
        Matcher mgrMatcher = MANAGER_RE.matcher(text);
        while (mgrMatcher.find()) {
            String mgrName = mgrMatcher.group(1);
            int line = findLineNumber(text, mgrMatcher.start());
            String nodeId = "django:" + filePath + ":manager:" + mgrName;
            managerNames.put(mgrName, nodeId);

            CodeNode node = new CodeNode();
            node.setId(nodeId);
            node.setKind(NodeKind.REPOSITORY);
            node.setLabel(mgrName);
            node.setFqn(filePath + "::" + mgrName);
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.getProperties().put("framework", "django");
            node.getProperties().put("type", "manager");
            nodes.add(node);
        }

        // Detect models
        Matcher modelMatcher = DJANGO_MODEL_RE.matcher(text);
        while (modelMatcher.find()) {
            String className = modelMatcher.group(1);
            int line = findLineNumber(text, modelMatcher.start());

            // Determine class body boundaries
            int classStart = modelMatcher.start();
            Matcher nextClassMatcher = NEXT_CLASS_RE.matcher(text.substring(modelMatcher.end()));
            String classBody;
            if (nextClassMatcher.find()) {
                classBody = text.substring(classStart, modelMatcher.end() + nextClassMatcher.start());
            } else {
                classBody = text.substring(classStart);
            }

            // Extract fields
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher fieldMatcher = FIELD_RE.matcher(classBody);
            while (fieldMatcher.find()) {
                fields.put(fieldMatcher.group(1), fieldMatcher.group(2));
            }

            // Extract Meta properties
            String tableName = null;
            String ordering = null;
            Matcher metaMatch = META_CLASS_RE.matcher(classBody);
            if (metaMatch.find()) {
                int metaStart = metaMatch.end();
                int metaEnd = classBody.length();
                Matcher metaEndMatcher = META_END_RE.matcher(classBody.substring(metaStart));
                if (metaEndMatcher.find()) {
                    metaEnd = metaStart + metaEndMatcher.start();
                }
                String metaBlock = classBody.substring(metaStart, metaEnd);
                Matcher tableMatch = META_TABLE_RE.matcher(metaBlock);
                if (tableMatch.find()) {
                    tableName = tableMatch.group(1);
                }
                Matcher orderingMatch = META_ORDERING_RE.matcher(metaBlock);
                if (orderingMatch.find()) {
                    ordering = orderingMatch.group(1);
                }
            }

            String nodeId = "django:" + filePath + ":model:" + className;
            CodeNode node = new CodeNode();
            node.setId(nodeId);
            node.setKind(NodeKind.ENTITY);
            node.setLabel(className);
            node.setFqn(filePath + "::" + className);
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.getProperties().put("fields", fields);
            node.getProperties().put("framework", "django");
            if (tableName != null) {
                node.getProperties().put("table_name", tableName);
            }
            if (ordering != null) {
                node.getProperties().put("ordering", ordering);
            }
            nodes.add(node);

            // FK / OneToOne edges
            Matcher fkMatcher = FK_RE.matcher(classBody);
            while (fkMatcher.find()) {
                String target = fkMatcher.group(2);
                String targetId = "django:" + filePath + ":model:" + target;
                CodeEdge edge = new CodeEdge();
                edge.setId(nodeId + "->depends_on->" + targetId);
                edge.setKind(EdgeKind.DEPENDS_ON);
                edge.setSourceId(nodeId);
                edges.add(edge);
            }

            // M2M edges
            Matcher m2mMatcher = M2M_RE.matcher(classBody);
            while (m2mMatcher.find()) {
                String target = m2mMatcher.group(2);
                String targetId = "django:" + filePath + ":model:" + target;
                CodeEdge edge = new CodeEdge();
                edge.setId(nodeId + "->depends_on->" + targetId);
                edge.setKind(EdgeKind.DEPENDS_ON);
                edge.setSourceId(nodeId);
                edges.add(edge);
            }

            // Manager assignments
            Matcher maMatcher = MANAGER_ASSIGNMENT_RE.matcher(classBody);
            while (maMatcher.find()) {
                String mgrClass = maMatcher.group(2);
                if (managerNames.containsKey(mgrClass)) {
                    CodeEdge edge = new CodeEdge();
                    edge.setId(nodeId + "->queries->" + managerNames.get(mgrClass));
                    edge.setKind(EdgeKind.QUERIES);
                    edge.setSourceId(nodeId);
                    edges.add(edge);
                }
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
