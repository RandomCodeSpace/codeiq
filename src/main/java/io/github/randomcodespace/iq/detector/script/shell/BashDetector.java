package io.github.randomcodespace.iq.detector.script.shell;

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
    name = "bash",
    category = "config",
    description = "Detects Bash script structure (functions, source imports, variables)",
    languages = {"bash"},
    nodeKinds = {NodeKind.CONFIG_DEFINITION, NodeKind.METHOD, NodeKind.MODULE},
    edgeKinds = {EdgeKind.CALLS, EdgeKind.IMPORTS}
)
@Component
public class BashDetector extends AbstractRegexDetector {

    private static final Pattern FUNC_RE = Pattern.compile("(?:function\\s+(\\w+)|(\\w+)\\s*\\(\\s*\\))\\s*\\{");
    private static final Pattern SOURCE_RE = Pattern.compile("(?:source|\\.) (?:\")?([^\\s\"]+)");
    private static final Pattern SHEBANG_RE = Pattern.compile("^#!\\s*/(?:usr/)?(?:bin/)?(?:env\\s+)?(\\w+)");
    private static final Pattern EXPORT_RE = Pattern.compile("export\\s+(\\w+)=");
    private static final Pattern TOOL_RE = Pattern.compile("\\b(aws|az|docker|gcloud|kubectl|terraform)\\b");

    @Override
    public String getName() { return "bash"; }

    @Override
    public Set<String> getSupportedLanguages() { return Set.of("bash"); }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) return DetectorResult.empty();

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String fp = ctx.filePath();
        String[] lines = text.split("\n", -1);

        if (lines.length > 0) {
            Matcher m = SHEBANG_RE.matcher(lines[0]);
            if (m.find()) {
                CodeNode n = new CodeNode(); n.setId(fp);
                n.setKind(NodeKind.MODULE); n.setLabel(fp); n.setFqn(fp);
                n.setFilePath(fp); n.setLineStart(1);
                n.getProperties().put("shell", m.group(1));
                nodes.add(n);
            }
        }

        for (int i = 0; i < lines.length; i++) {
            Matcher m = FUNC_RE.matcher(lines[i]);
            if (m.find()) {
                String funcName = m.group(1) != null ? m.group(1) : m.group(2);
                CodeNode n = new CodeNode(); n.setId(fp + ":" + funcName);
                n.setKind(NodeKind.METHOD); n.setLabel(funcName); n.setFqn(funcName);
                n.setFilePath(fp); n.setLineStart(i + 1);
                nodes.add(n);
            }

            m = SOURCE_RE.matcher(lines[i]);
            if (m.find()) {
                CodeEdge e = new CodeEdge(); e.setId(fp + ":sources:" + m.group(1));
                e.setKind(EdgeKind.IMPORTS); e.setSourceId(fp);
                e.setTarget(new CodeNode(m.group(1), NodeKind.MODULE, m.group(1)));
                edges.add(e);
            }

            m = EXPORT_RE.matcher(lines[i]);
            if (m.find()) {
                String varName = m.group(1);
                CodeNode n = new CodeNode(); n.setId(fp + ":export:" + varName);
                n.setKind(NodeKind.CONFIG_DEFINITION); n.setLabel("export " + varName);
                n.setFqn(varName); n.setFilePath(fp); n.setLineStart(i + 1);
                nodes.add(n);
            }
        }

        Set<String> toolsSeen = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            String stripped = lines[i].stripLeading();
            if (stripped.startsWith("#")) continue;
            Matcher m = TOOL_RE.matcher(lines[i]);
            while (m.find()) {
                String tool = m.group(1);
                if (toolsSeen.add(tool)) {
                    CodeEdge e = new CodeEdge(); e.setId(fp + ":calls:" + tool);
                    e.setKind(EdgeKind.CALLS); e.setSourceId(fp);
                    e.setTarget(new CodeNode(tool, NodeKind.MODULE, tool));
                    e.getProperties().put("tool", tool);
                    edges.add(e);
                }
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
