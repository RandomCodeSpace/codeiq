package io.github.randomcodespace.iq.detector.docs;

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
public class MarkdownStructureDetector extends AbstractRegexDetector {

    private static final Pattern HEADING_RE = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK_RE = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern EXTERNAL_RE = Pattern.compile("^https?://");

    @Override
    public String getName() { return "markdown_structure"; }

    @Override
    public Set<String> getSupportedLanguages() { return Set.of("markdown"); }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) return DetectorResult.empty();

        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String fp = ctx.filePath();
        String[] lines = text.split("\n", -1);

        // Find first H1 for module label
        String firstH1 = null;
        for (String line : lines) {
            Matcher hm = HEADING_RE.matcher(line);
            if (hm.matches() && hm.group(1).length() == 1) {
                firstH1 = hm.group(2).strip();
                break;
            }
        }

        String filename = fileName(ctx);
        String moduleLabel = firstH1 != null ? firstH1 : filename;
        String moduleId = "md:" + fp;

        CodeNode moduleNode = new CodeNode();
        moduleNode.setId(moduleId); moduleNode.setKind(NodeKind.MODULE);
        moduleNode.setLabel(moduleLabel); moduleNode.setFqn(fp);
        moduleNode.setFilePath(fp); moduleNode.setLineStart(1);
        nodes.add(moduleNode);

        // Headings
        for (int i = 0; i < lines.length; i++) {
            Matcher hm = HEADING_RE.matcher(lines[i]);
            if (!hm.matches()) continue;
            int level = hm.group(1).length();
            String headingText = hm.group(2).strip();
            int lineNum = i + 1;
            String headingId = "md:" + fp + ":heading:" + lineNum;

            CodeNode n = new CodeNode(); n.setId(headingId);
            n.setKind(NodeKind.CONFIG_KEY); n.setLabel(headingText);
            n.setFqn(fp + ":heading:" + headingText);
            n.setFilePath(fp); n.setLineStart(lineNum);
            n.getProperties().put("level", level); n.getProperties().put("text", headingText);
            nodes.add(n);

            CodeEdge e = new CodeEdge(); e.setId(moduleId + ":contains:" + headingId);
            e.setKind(EdgeKind.CONTAINS); e.setSourceId(moduleId);
            e.setTarget(new CodeNode(headingId, NodeKind.CONFIG_KEY, headingText));
            edges.add(e);
        }

        // Internal links
        for (int i = 0; i < lines.length; i++) {
            Matcher lm = LINK_RE.matcher(lines[i]);
            while (lm.find()) {
                String linkText = lm.group(1);
                String linkTarget = lm.group(2);
                if (EXTERNAL_RE.matcher(linkTarget).find()) continue;
                String linkPath = linkTarget.split("#")[0];
                if (linkPath.isEmpty()) continue;
                CodeEdge e = new CodeEdge(); e.setId(moduleId + ":depends_on:" + linkPath);
                e.setKind(EdgeKind.DEPENDS_ON); e.setSourceId(moduleId);
                e.setTarget(new CodeNode(linkPath, NodeKind.MODULE, linkPath));
                e.getProperties().put("link_text", linkText);
                edges.add(e);
            }
        }

        return DetectorResult.of(nodes, edges);
    }
}
