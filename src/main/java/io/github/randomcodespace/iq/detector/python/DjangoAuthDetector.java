package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DjangoAuthDetector extends AbstractRegexDetector {

    private static final Pattern LOGIN_REQUIRED_RE = Pattern.compile("@login_required\\b");

    private static final Pattern PERMISSION_REQUIRED_RE = Pattern.compile(
            "@permission_required\\(\\s*[\"']([^\"']*)[\"']"
    );

    private static final Pattern USER_PASSES_TEST_RE = Pattern.compile(
            "@user_passes_test\\(\\s*([^,)\\s]+)"
    );

    private static final Pattern MIXIN_RE = Pattern.compile(
            "class\\s+(\\w+)\\s*\\(([^)]*)\\):"
    );

    private static final Map<String, String> AUTH_MIXINS = Map.of(
            "LoginRequiredMixin", "login_required",
            "PermissionRequiredMixin", "permission_required",
            "UserPassesTestMixin", "user_passes_test"
    );

    @Override
    public String getName() {
        return "django_auth";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        List<CodeNode> nodes = new ArrayList<>();
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }
        String filePath = ctx.filePath();
        String moduleName = ctx.moduleName();

        // @login_required
        Matcher m = LOGIN_REQUIRED_RE.matcher(text);
        while (m.find()) {
            int line = findLineNumber(text, m.start());
            CodeNode node = new CodeNode();
            node.setId("auth:" + filePath + ":login_required:" + line);
            node.setKind(NodeKind.GUARD);
            node.setLabel("@login_required");
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.setAnnotations(List.of("@login_required"));
            node.getProperties().put("auth_type", "django");
            node.getProperties().put("permissions", List.of());
            node.getProperties().put("auth_required", true);
            nodes.add(node);
        }

        // @permission_required("perm")
        m = PERMISSION_REQUIRED_RE.matcher(text);
        while (m.find()) {
            int line = findLineNumber(text, m.start());
            String permission = m.group(1);
            CodeNode node = new CodeNode();
            node.setId("auth:" + filePath + ":permission_required:" + line);
            node.setKind(NodeKind.GUARD);
            node.setLabel("@permission_required(" + permission + ")");
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.setAnnotations(List.of("@permission_required"));
            node.getProperties().put("auth_type", "django");
            node.getProperties().put("permissions", List.of(permission));
            node.getProperties().put("auth_required", true);
            nodes.add(node);
        }

        // @user_passes_test(fn)
        m = USER_PASSES_TEST_RE.matcher(text);
        while (m.find()) {
            int line = findLineNumber(text, m.start());
            String testFunc = m.group(1);
            CodeNode node = new CodeNode();
            node.setId("auth:" + filePath + ":user_passes_test:" + line);
            node.setKind(NodeKind.GUARD);
            node.setLabel("@user_passes_test(" + testFunc + ")");
            node.setModule(moduleName);
            node.setFilePath(filePath);
            node.setLineStart(line);
            node.setAnnotations(List.of("@user_passes_test"));
            node.getProperties().put("auth_type", "django");
            node.getProperties().put("permissions", List.of());
            node.getProperties().put("test_function", testFunc);
            node.getProperties().put("auth_required", true);
            nodes.add(node);
        }

        // Class-based views with auth mixins
        m = MIXIN_RE.matcher(text);
        while (m.find()) {
            String className = m.group(1);
            String basesStr = m.group(2);
            String[] bases = basesStr.split(",");
            for (String base : bases) {
                String trimmed = base.trim();
                if (AUTH_MIXINS.containsKey(trimmed)) {
                    int line = findLineNumber(text, m.start());
                    CodeNode node = new CodeNode();
                    node.setId("auth:" + filePath + ":" + trimmed + ":" + line);
                    node.setKind(NodeKind.GUARD);
                    node.setLabel(className + "(" + trimmed + ")");
                    node.setModule(moduleName);
                    node.setFilePath(filePath);
                    node.setLineStart(line);
                    node.setAnnotations(List.of("mixin:" + trimmed));
                    node.getProperties().put("auth_type", "django");
                    node.getProperties().put("permissions", List.of());
                    node.getProperties().put("mixin", trimmed);
                    node.getProperties().put("class_name", className);
                    node.getProperties().put("auth_required", true);
                    nodes.add(node);
                }
            }
        }

        return DetectorResult.of(nodes, List.of());
    }
}
