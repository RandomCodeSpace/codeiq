package io.github.randomcodespace.iq.detector.script.shell;

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
import io.github.randomcodespace.iq.detector.DetectorInfo;

@DetectorInfo(
    name = "powershell",
    category = "config",
    description = "Detects PowerShell script structure (functions, modules, imports)",
    languages = {"powershell"},
    nodeKinds = {NodeKind.CONFIG_DEFINITION, NodeKind.METHOD, NodeKind.MODULE},
    edgeKinds = {EdgeKind.IMPORTS}
)
@Component
public class PowerShellDetector extends AbstractRegexDetector {

    private static final Pattern FUNC_RE = Pattern.compile("function\\s+([\\w-]+)\\s*(?:\\([^)]*\\))?\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPORT_RE = Pattern.compile("Import-Module\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOT_SOURCE_RE = Pattern.compile("\\.\\s+[\"']?(\\S+\\.ps(?:1|m1))[\"']?");
    private static final Pattern PARAM_RE = Pattern.compile("\\[Parameter[^]]*\\]\\s*\\[(\\w+)\\]\\s*\\$(\\w+)");
    private static final Pattern CMDLET_BINDING_RE = Pattern.compile("\\[CmdletBinding\\(\\)\\]", Pattern.CASE_INSENSITIVE);

    @Override
    public String getName() { return "powershell"; }

    @Override
    public Set<String> getSupportedLanguages() { return Set.of("powershell"); }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) return DetectorResult.empty();

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String fp = ctx.filePath();
        String[] lines = text.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            Matcher m = FUNC_RE.matcher(lines[i]);
            if (m.find()) {
                String funcName = m.group(1);
                boolean isAdvanced = false;
                for (int j = i + 1; j < Math.min(i + 5, lines.length); j++) {
                    if (CMDLET_BINDING_RE.matcher(lines[j]).find()) { isAdvanced = true; break; }
                }
                CodeNode n = new CodeNode(); n.setId(fp + ":" + funcName);
                n.setKind(NodeKind.METHOD); n.setLabel(funcName); n.setFqn(funcName);
                n.setFilePath(fp); n.setLineStart(i + 1);
                if (isAdvanced) n.getProperties().put("advanced_function", true);
                nodes.add(n);
            }

            Matcher im = IMPORT_RE.matcher(lines[i]);
            if (im.find()) {
                CodeEdge e = new CodeEdge(); e.setId(fp + ":imports:" + im.group(1));
                e.setKind(EdgeKind.IMPORTS); e.setSourceId(fp);
                e.setTarget(new CodeNode(im.group(1), NodeKind.MODULE, im.group(1)));
                edges.add(e);
            }

            Matcher dm = DOT_SOURCE_RE.matcher(lines[i]);
            if (dm.find()) {
                CodeEdge e = new CodeEdge(); e.setId(fp + ":dotsource:" + dm.group(1));
                e.setKind(EdgeKind.IMPORTS); e.setSourceId(fp);
                e.setTarget(new CodeNode(dm.group(1), NodeKind.MODULE, dm.group(1)));
                edges.add(e);
            }

            Matcher pm = PARAM_RE.matcher(lines[i]);
            if (pm.find()) {
                String paramType = pm.group(1);
                String paramName = pm.group(2);
                CodeNode n = new CodeNode(); n.setId(fp + ":param:" + paramName);
                n.setKind(NodeKind.CONFIG_DEFINITION);
                n.setLabel("$" + paramName + ": " + paramType);
                n.setFqn(paramName); n.setFilePath(fp); n.setLineStart(i + 1);
                n.getProperties().put("param_type", paramType);
                nodes.add(n);
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
