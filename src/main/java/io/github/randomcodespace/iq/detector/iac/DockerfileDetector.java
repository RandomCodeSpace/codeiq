package io.github.randomcodespace.iq.detector.iac;

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
import io.github.randomcodespace.iq.detector.DetectorInfo;

@DetectorInfo(
    name = "dockerfile",
    category = "infra",
    description = "Detects Dockerfile instructions (FROM, EXPOSE, COPY, multi-stage builds)",
    languages = {"dockerfile"},
    nodeKinds = {NodeKind.CONFIG_DEFINITION, NodeKind.ENDPOINT, NodeKind.INFRA_RESOURCE},
    edgeKinds = {EdgeKind.DEPENDS_ON},
    properties = {"image", "port", "protocol"}
)
@Component
public class DockerfileDetector extends AbstractRegexDetector {

    private static final Pattern FROM_RE = Pattern.compile("^FROM\\s+(\\S+)(?:\\s+AS\\s+(\\w+))?", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPOSE_RE = Pattern.compile("^EXPOSE\\s+(\\d+)", Pattern.MULTILINE);
    private static final Pattern ENV_RE = Pattern.compile("^ENV\\s+(\\w+)[=\\s]", Pattern.MULTILINE);
    private static final Pattern LABEL_RE = Pattern.compile("^LABEL\\s+(\\S+)=", Pattern.MULTILINE);
    private static final Pattern COPY_FROM_RE = Pattern.compile("COPY\\s+--from=(\\w+)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern ARG_RE = Pattern.compile("^ARG\\s+(\\w+)", Pattern.MULTILINE);

    @Override
    public String getName() { return "dockerfile"; }

    @Override
    public Set<String> getSupportedLanguages() { return Set.of("dockerfile"); }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) return DetectorResult.empty();

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String fp = ctx.filePath();
        Map<String, String> stageNodeIds = new HashMap<>();
        List<int[]> fromOffsets = new ArrayList<>(); // [offset, indexInNodes]
        int stageOrder = 0;

        Matcher m = FROM_RE.matcher(text);
        while (m.find()) {
            String image = m.group(1); String alias = m.group(2);
            int line = findLineNumber(text, m.start());
            String nodeId = "docker:" + fp + ":from:" + image;
            String label = "FROM " + image + (alias != null ? " AS " + alias : "");

            CodeNode n = new CodeNode(); n.setId(nodeId); n.setKind(NodeKind.INFRA_RESOURCE);
            n.setLabel(label); n.setFqn(image); n.setFilePath(fp); n.setLineStart(line);
            n.getProperties().put("image", image); n.getProperties().put("stage_order", stageOrder++);
            if (image.contains(":") && !image.startsWith("$")) {
                String[] parts = image.split(":", 2);
                n.getProperties().put("image_name", parts[0]); n.getProperties().put("tag", parts[1]);
            } else {
                n.getProperties().put("image_name", image);
            }
            if (alias != null) {
                n.getProperties().put("stage_alias", alias); n.getProperties().put("build_stage", alias);
                stageNodeIds.put(alias, nodeId);
            }
            fromOffsets.add(new int[]{m.start(), nodes.size()});
            nodes.add(n);

            CodeEdge e = new CodeEdge(); e.setId(fp + ":depends_on:" + image);
            e.setKind(EdgeKind.DEPENDS_ON); e.setSourceId(fp);
            e.setTarget(new CodeNode(image, NodeKind.INFRA_RESOURCE, image));
            edges.add(e);
        }

        m = COPY_FROM_RE.matcher(text);
        while (m.find()) {
            String sourceStage = m.group(1);
            if (stageNodeIds.containsKey(sourceStage)) {
                String currentNodeId = null;
                for (int i = fromOffsets.size() - 1; i >= 0; i--) {
                    if (fromOffsets.get(i)[0] < m.start()) {
                        currentNodeId = nodes.get(fromOffsets.get(i)[1]).getId();
                        break;
                    }
                }
                if (currentNodeId != null && !currentNodeId.equals(stageNodeIds.get(sourceStage))) {
                    CodeEdge e = new CodeEdge(); e.setId(currentNodeId + ":depends_on:" + stageNodeIds.get(sourceStage));
                    e.setKind(EdgeKind.DEPENDS_ON); e.setSourceId(currentNodeId);
                    e.setTarget(new CodeNode(stageNodeIds.get(sourceStage), NodeKind.INFRA_RESOURCE, sourceStage));
                    edges.add(e);
                }
            }
        }

        m = EXPOSE_RE.matcher(text);
        while (m.find()) {
            String port = m.group(1); int line = findLineNumber(text, m.start());
            CodeNode n = new CodeNode(); n.setId("docker:" + fp + ":expose:" + port);
            n.setKind(NodeKind.ENDPOINT); n.setLabel("EXPOSE " + port); n.setFilePath(fp);
            n.setLineStart(line); n.getProperties().put("port", port); n.getProperties().put("protocol", "tcp");
            nodes.add(n);
        }

        m = ENV_RE.matcher(text);
        while (m.find()) {
            String key = m.group(1); int line = findLineNumber(text, m.start());
            CodeNode n = new CodeNode(); n.setId("docker:" + fp + ":env:" + key);
            n.setKind(NodeKind.CONFIG_DEFINITION); n.setLabel("ENV " + key); n.setFilePath(fp);
            n.setLineStart(line); n.getProperties().put("env_key", key); nodes.add(n);
        }

        m = LABEL_RE.matcher(text);
        while (m.find()) {
            String labelKey = m.group(1); int line = findLineNumber(text, m.start());
            CodeNode n = new CodeNode(); n.setId("docker:" + fp + ":label:" + labelKey);
            n.setKind(NodeKind.CONFIG_DEFINITION); n.setLabel("LABEL " + labelKey); n.setFilePath(fp);
            n.setLineStart(line); n.getProperties().put("label_key", labelKey); nodes.add(n);
        }

        m = ARG_RE.matcher(text);
        while (m.find()) {
            String argName = m.group(1); int line = findLineNumber(text, m.start());
            CodeNode n = new CodeNode(); n.setId("docker:" + fp + ":arg:" + argName);
            n.setKind(NodeKind.CONFIG_DEFINITION); n.setLabel("ARG " + argName); n.setFilePath(fp);
            n.setLineStart(line); n.getProperties().put("arg_name", argName); nodes.add(n);
        }

        return DetectorResult.of(nodes, edges);
    }
}
