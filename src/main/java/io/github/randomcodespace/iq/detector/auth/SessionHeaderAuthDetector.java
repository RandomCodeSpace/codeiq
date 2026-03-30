package io.github.randomcodespace.iq.detector.auth;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import io.github.randomcodespace.iq.detector.DetectorInfo;

@DetectorInfo(
    name = "session_header_auth",
    category = "auth",
    description = "Detects session and header-based authentication (cookies, API keys, tokens)",
    languages = {"java", "python", "typescript"},
    nodeKinds = {NodeKind.GUARD, NodeKind.MIDDLEWARE},
    properties = {"auth_type", "language"}
)
@Component
public class SessionHeaderAuthDetector extends AbstractRegexDetector {

    private record PatternDef(Pattern regex, String authType, NodeKind nodeKind) {}

    private static final List<PatternDef> SESSION_PATTERNS = List.of(
            new PatternDef(Pattern.compile("['\"]express-session['\"]"), "session", NodeKind.MIDDLEWARE),
            new PatternDef(Pattern.compile("['\"]cookie-session['\"]"), "session", NodeKind.MIDDLEWARE),
            new PatternDef(Pattern.compile("@SessionAttributes\\b"), "session", NodeKind.GUARD),
            new PatternDef(Pattern.compile("\\bSessionMiddleware\\b"), "session", NodeKind.MIDDLEWARE),
            new PatternDef(Pattern.compile("\\bHttpSession\\b"), "session", NodeKind.GUARD),
            new PatternDef(Pattern.compile("\\bSESSION_ENGINE\\b"), "session", NodeKind.GUARD)
    );

    private static final List<PatternDef> HEADER_PATTERNS = List.of(
            new PatternDef(Pattern.compile("['\"]X-API-Key['\"]", Pattern.CASE_INSENSITIVE), "header", NodeKind.GUARD),
            new PatternDef(Pattern.compile("(?:req|request|ctx)\\.headers?\\s*\\[\\s*['\"]authorization['\"]\\s*]", Pattern.CASE_INSENSITIVE), "header", NodeKind.GUARD),
            new PatternDef(Pattern.compile("getHeader\\s*\\(\\s*['\"]Authorization['\"]", Pattern.CASE_INSENSITIVE), "header", NodeKind.GUARD)
    );

    private static final List<PatternDef> API_KEY_PATTERNS = List.of(
            new PatternDef(Pattern.compile("(?:req|request)\\.headers?\\s*\\[\\s*['\"]x-api-key['\"]\\s*]", Pattern.CASE_INSENSITIVE), "api_key", NodeKind.GUARD),
            new PatternDef(Pattern.compile("\\bapi[_-]?key\\s*[=:]\\s*", Pattern.CASE_INSENSITIVE), "api_key", NodeKind.GUARD),
            new PatternDef(Pattern.compile("\\bvalidate_?api_?key\\b", Pattern.CASE_INSENSITIVE), "api_key", NodeKind.GUARD)
    );

    private static final List<PatternDef> CSRF_PATTERNS = List.of(
            new PatternDef(Pattern.compile("@csrf_protect\\b"), "csrf", NodeKind.GUARD),
            new PatternDef(Pattern.compile("\\bcsrf_exempt\\b"), "csrf", NodeKind.GUARD),
            new PatternDef(Pattern.compile("\\bCsrfViewMiddleware\\b"), "csrf", NodeKind.MIDDLEWARE),
            new PatternDef(Pattern.compile("['\"]csurf['\"]"), "csrf", NodeKind.MIDDLEWARE)
    );

    private static final List<PatternDef> ALL_PATTERNS;

    static {
        ALL_PATTERNS = new ArrayList<>();
        ALL_PATTERNS.addAll(SESSION_PATTERNS);
        ALL_PATTERNS.addAll(HEADER_PATTERNS);
        ALL_PATTERNS.addAll(API_KEY_PATTERNS);
        ALL_PATTERNS.addAll(CSRF_PATTERNS);
    }

    private static final Map<String, String> ID_TAG = Map.of(
            "session", "session",
            "header", "header",
            "api_key", "apikey",
            "csrf", "csrf"
    );

    @Override
    public String getName() {
        return "session_header_auth";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("java", "python", "typescript");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        if (!getSupportedLanguages().contains(ctx.language())) {
            return DetectorResult.empty();
        }

        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }

        List<CodeNode> nodes = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        Set<Integer> seenLines = new HashSet<>();

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];
            for (PatternDef pdef : ALL_PATTERNS) {
                if (seenLines.contains(lineIdx)) {
                    break;
                }
                if (pdef.regex.matcher(line).find()) {
                    seenLines.add(lineIdx);
                    int lineNum = lineIdx + 1;
                    String matchedText = line.strip();
                    String tag = ID_TAG.get(pdef.authType);

                    CodeNode node = new CodeNode();
                    node.setId("auth:" + ctx.filePath() + ":" + tag + ":" + lineNum);
                    node.setKind(pdef.nodeKind);
                    String truncLabel = matchedText.length() > 70 ? matchedText.substring(0, 70) : matchedText;
                    node.setLabel(pdef.authType + " auth: " + truncLabel);
                    node.setFilePath(ctx.filePath());
                    node.setLineStart(lineNum);
                    node.setLineEnd(lineNum);
                    node.getProperties().put("auth_type", pdef.authType);
                    node.getProperties().put("language", ctx.language());
                    String truncPattern = matchedText.length() > 120 ? matchedText.substring(0, 120) : matchedText;
                    node.getProperties().put("pattern", truncPattern);
                    nodes.add(node);
                }
            }
        }

        return DetectorResult.of(nodes, List.of());
    }
}
