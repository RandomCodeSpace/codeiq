package io.github.randomcodespace.iq.detector.proto;

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
    name = "proto_structure",
    category = "structures",
    description = "Detects Protocol Buffer messages, services, RPCs, and imports",
    languages = {"proto"},
    nodeKinds = {NodeKind.CONFIG_KEY, NodeKind.INTERFACE, NodeKind.METHOD, NodeKind.MODULE, NodeKind.PROTOCOL_MESSAGE},
    edgeKinds = {EdgeKind.CONTAINS, EdgeKind.IMPORTS}
)
@Component
public class ProtoStructureDetector extends AbstractRegexDetector {

    private static final Pattern SERVICE_RE = Pattern.compile("service\\s+(\\w+)\\s*\\{");
    private static final Pattern RPC_RE = Pattern.compile("rpc\\s+(\\w+)\\s*\\((\\w+)\\)\\s*returns\\s*\\((\\w+)\\)");
    private static final Pattern MESSAGE_RE = Pattern.compile("message\\s+(\\w+)\\s*\\{");
    private static final Pattern IMPORT_RE = Pattern.compile("import\\s+\"([^\"]+)\"");
    private static final Pattern PACKAGE_RE = Pattern.compile("package\\s+([\\w.]+)\\s*;");

    @Override
    public String getName() { return "proto_structure"; }

    @Override
    public Set<String> getSupportedLanguages() { return Set.of("proto"); }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) return DetectorResult.empty();

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String fp = ctx.filePath();
        String[] lines = text.split("\n", -1);

        // Package
        for (int i = 0; i < lines.length; i++) {
            Matcher m = PACKAGE_RE.matcher(lines[i]);
            if (m.find()) {
                CodeNode n = new CodeNode(); n.setId("proto:" + fp + ":package:" + m.group(1));
                n.setKind(NodeKind.CONFIG_KEY); n.setLabel("package " + m.group(1));
                n.setFqn(m.group(1)); n.setFilePath(fp); n.setLineStart(i + 1);
                n.getProperties().put("package", m.group(1));
                nodes.add(n); break;
            }
        }

        // Imports
        for (int i = 0; i < lines.length; i++) {
            Matcher m = IMPORT_RE.matcher(lines[i]);
            if (m.find()) {
                CodeEdge e = new CodeEdge(); e.setId(fp + ":imports:" + m.group(1));
                e.setKind(EdgeKind.IMPORTS); e.setSourceId(fp);
                e.setTarget(new CodeNode(m.group(1), NodeKind.MODULE, m.group(1)));
                edges.add(e);
            }
        }

        // Services and RPCs
        String currentService = null;
        int braceDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            Matcher svcM = SERVICE_RE.matcher(lines[i]);
            if (svcM.find()) {
                String svcName = svcM.group(1);
                currentService = svcName;
                braceDepth = 0;
                CodeNode n = new CodeNode(); n.setId("proto:" + fp + ":service:" + svcName);
                n.setKind(NodeKind.INTERFACE); n.setLabel(svcName); n.setFqn(svcName);
                n.setFilePath(fp); n.setLineStart(i + 1);
                nodes.add(n);
            }

            if (currentService != null) {
                for (char c : lines[i].toCharArray()) {
                    if (c == '{') braceDepth++;
                    else if (c == '}') braceDepth--;
                }
                if (braceDepth <= 0) currentService = null;
            }

            Matcher rpcM = RPC_RE.matcher(lines[i]);
            if (rpcM.find()) {
                String methodName = rpcM.group(1);
                String requestType = rpcM.group(2);
                String responseType = rpcM.group(3);
                String svc = currentService != null ? currentService : "_unknown";
                String rpcId = "proto:" + fp + ":rpc:" + svc + ":" + methodName;
                CodeNode n = new CodeNode(); n.setId(rpcId);
                n.setKind(NodeKind.METHOD); n.setLabel(svc + "." + methodName);
                n.setFqn(svc + "." + methodName); n.setFilePath(fp); n.setLineStart(i + 1);
                n.getProperties().put("request_type", requestType);
                n.getProperties().put("response_type", responseType);
                nodes.add(n);

                if (currentService != null) {
                    CodeEdge e = new CodeEdge(); e.setId("proto:" + fp + ":service:" + currentService + ":contains:" + rpcId);
                    e.setKind(EdgeKind.CONTAINS); e.setSourceId("proto:" + fp + ":service:" + currentService);
                    e.setTarget(new CodeNode(rpcId, NodeKind.METHOD, methodName));
                    edges.add(e);
                }
            }
        }

        // Messages
        for (int i = 0; i < lines.length; i++) {
            Matcher m = MESSAGE_RE.matcher(lines[i]);
            if (m.find()) {
                CodeNode n = new CodeNode(); n.setId("proto:" + fp + ":message:" + m.group(1));
                n.setKind(NodeKind.PROTOCOL_MESSAGE); n.setLabel(m.group(1));
                n.setFqn(m.group(1)); n.setFilePath(fp); n.setLineStart(i + 1);
                nodes.add(n);
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
