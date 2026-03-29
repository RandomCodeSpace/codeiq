package io.github.randomcodespace.iq.detector.frontend;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.randomcodespace.iq.detector.DetectorInfo;

@DetectorInfo(
    name = "frontend.svelte_components",
    category = "frontend",
    description = "Detects Svelte components (script, template, style blocks)",
    languages = {"typescript", "javascript", "svelte"},
    nodeKinds = {NodeKind.COMPONENT},
    properties = {"framework"}
)
@Component
public class SvelteComponentDetector extends AbstractRegexDetector {

    private static final Pattern PROP_PATTERN = Pattern.compile("export\\s+let\\s+(\\w+)");
    private static final Pattern REACTIVE_PATTERN = Pattern.compile("^\\s*\\$:", Pattern.MULTILINE);
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("^<script\\b", Pattern.MULTILINE);
    private static final Pattern HTML_TEMPLATE_PATTERN = Pattern.compile("^<(?!script\\b|style\\b|/)[a-zA-Z]\\w*[\\s>]", Pattern.MULTILINE);

    @Override
    public String getName() {
        return "frontend.svelte_components";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("typescript", "javascript", "svelte");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }

        boolean hasProps = PROP_PATTERN.matcher(text).find();
        boolean hasReactive = REACTIVE_PATTERN.matcher(text).find();
        boolean hasScript = SCRIPT_PATTERN.matcher(text).find();
        boolean hasTemplate = HTML_TEMPLATE_PATTERN.matcher(text).find();

        boolean isSvelte = hasProps || hasReactive || (hasScript && hasTemplate);
        if (!isSvelte) {
            return DetectorResult.empty();
        }

        String filePath = ctx.filePath();
        String normalized = filePath.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        String filename = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        int dotIdx = filename.lastIndexOf('.');
        String componentName = dotIdx > 0 ? filename.substring(0, dotIdx) : filename;

        // Collect props
        List<String> props = new ArrayList<>();
        Matcher propM = PROP_PATTERN.matcher(text);
        while (propM.find()) {
            props.add(propM.group(1));
        }

        // Count reactive statements
        int reactiveCount = 0;
        Matcher reactiveM = REACTIVE_PATTERN.matcher(text);
        while (reactiveM.find()) {
            reactiveCount++;
        }

        // Find first line
        int firstLine = 1;
        for (Pattern p : List.of(SCRIPT_PATTERN, PROP_PATTERN, REACTIVE_PATTERN)) {
            Matcher fm = p.matcher(text);
            if (fm.find()) {
                int candidate = text.substring(0, fm.start()).split("\n", -1).length;
                firstLine = firstLine > 1 ? Math.min(firstLine, candidate) : candidate;
                break;
            }
        }

        CodeNode node = new CodeNode();
        node.setId("svelte:" + filePath + ":component:" + componentName);
        node.setKind(NodeKind.COMPONENT);
        node.setLabel(componentName);
        node.setFqn(filePath + "::" + componentName);
        node.setFilePath(filePath);
        node.setLineStart(firstLine);
        node.getProperties().put("framework", "svelte");
        node.getProperties().put("props", props);
        node.getProperties().put("reactive_statements", reactiveCount);

        return DetectorResult.of(List.of(node), List.of());
    }
}
