package io.github.randomcodespace.iq.detector.iac;

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
public class BicepDetector extends AbstractRegexDetector {

    private static final Pattern RESOURCE_RE = Pattern.compile("resource\\s+(\\w+)\\s+'([^']+)'");
    private static final Pattern PARAM_RE = Pattern.compile("param\\s+(\\w+)\\s+(\\w+)");
    private static final Pattern MODULE_RE = Pattern.compile("module\\s+(\\w+)\\s+'([^']+)'");

    @Override
    public String getName() { return "bicep"; }

    @Override
    public Set<String> getSupportedLanguages() { return Set.of("bicep"); }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) return DetectorResult.empty();

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String fp = ctx.filePath();
        String[] lines = text.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            Matcher m = RESOURCE_RE.matcher(lines[i]);
            if (m.find()) {
                String resourceName = m.group(1); String typeStr = m.group(2);
                String azureType = typeStr.contains("@") ? typeStr.substring(0, typeStr.lastIndexOf('@')) : typeStr;
                String apiVersion = typeStr.contains("@") ? typeStr.substring(typeStr.lastIndexOf('@') + 1) : null;
                NodeKind kind = azureType.startsWith("Microsoft.") ? NodeKind.AZURE_RESOURCE : NodeKind.INFRA_RESOURCE;
                CodeNode n = new CodeNode(); n.setId(fp + ":resource:" + resourceName);
                n.setKind(kind); n.setLabel(resourceName + " (" + azureType + ")");
                n.setFqn(azureType); n.setFilePath(fp); n.setLineStart(i + 1);
                n.getProperties().put("azure_type", azureType);
                if (apiVersion != null) n.getProperties().put("api_version", apiVersion);
                nodes.add(n);
            }

            m = PARAM_RE.matcher(lines[i]);
            if (m.find()) {
                String paramName = m.group(1); String paramType = m.group(2);
                CodeNode n = new CodeNode(); n.setId(fp + ":param:" + paramName);
                n.setKind(NodeKind.CONFIG_KEY); n.setLabel("param " + paramName + ": " + paramType);
                n.setFilePath(fp); n.setLineStart(i + 1);
                n.getProperties().put("param_type", paramType); nodes.add(n);
            }

            m = MODULE_RE.matcher(lines[i]);
            if (m.find()) {
                String moduleName = m.group(1); String modulePath = m.group(2);
                CodeNode n = new CodeNode(); n.setId(fp + ":module:" + moduleName);
                n.setKind(NodeKind.INFRA_RESOURCE);
                n.setLabel("module " + moduleName + " (" + modulePath + ")");
                n.setFilePath(fp); n.setLineStart(i + 1);
                n.getProperties().put("module_path", modulePath); nodes.add(n);

                CodeEdge e = new CodeEdge(); e.setId(fp + ":depends_on:" + modulePath);
                e.setKind(EdgeKind.DEPENDS_ON); e.setSourceId(fp);
                e.setTarget(new CodeNode(modulePath, NodeKind.MODULE, modulePath));
                e.getProperties().put("module_name", moduleName); edges.add(e);
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
