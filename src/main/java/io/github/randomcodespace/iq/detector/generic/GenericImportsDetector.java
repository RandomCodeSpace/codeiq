package io.github.randomcodespace.iq.detector.generic;

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
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.randomcodespace.iq.detector.DetectorInfo;

@DetectorInfo(
    name = "generic_imports",
    category = "structures",
    description = "Detects imports, classes, and functions in Ruby, Swift, Perl, Lua, Dart, R",
    languages = {"ruby", "swift", "perl", "lua", "dart", "r"},
    nodeKinds = {NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.METHOD, NodeKind.MODULE},
    edgeKinds = {EdgeKind.EXTENDS, EdgeKind.IMPLEMENTS, EdgeKind.IMPORTS},
    properties = {"base_class"}
)
@Component
public class GenericImportsDetector extends AbstractRegexDetector {

    // Ruby
    private static final Pattern RUBY_REQUIRE_RE = Pattern.compile("^(?:require|require_relative)\\s+'([^']+)'", Pattern.MULTILINE);
    private static final Pattern RUBY_CLASS_RE = Pattern.compile("^\\s*class\\s+(\\w+)(?:\\s*<\\s*(\\w+))?", Pattern.MULTILINE);
    private static final Pattern RUBY_DEF_RE = Pattern.compile("^\\s*def\\s+(\\w+)", Pattern.MULTILINE);
    // Swift
    private static final Pattern SWIFT_IMPORT_RE = Pattern.compile("^\\s*import\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern SWIFT_CLASS_RE = Pattern.compile("^\\s*class\\s+(\\w+)(?:\\s*:\\s*([\\w\\s,]+))?", Pattern.MULTILINE);
    private static final Pattern SWIFT_STRUCT_RE = Pattern.compile("^\\s*struct\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern SWIFT_FUNC_RE = Pattern.compile("^\\s*(?:override\\s+)?func\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
    // Perl
    private static final Pattern PERL_USE_RE = Pattern.compile("^\\s*use\\s+([\\w:]+)", Pattern.MULTILINE);
    private static final Pattern PERL_PACKAGE_RE = Pattern.compile("^\\s*package\\s+([\\w:]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PERL_SUB_RE = Pattern.compile("^\\s*sub\\s+(\\w+)", Pattern.MULTILINE);
    // Lua
    private static final Pattern LUA_REQUIRE_RE = Pattern.compile("require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)", Pattern.MULTILINE);
    private static final Pattern LUA_FUNCTION_RE = Pattern.compile("^\\s*(?:local\\s+)?function\\s+(?:[\\w.]+[.:])?([\\w]+)\\s*\\(", Pattern.MULTILINE);
    // Dart
    private static final Pattern DART_IMPORT_RE = Pattern.compile("^\\s*import\\s+['\"]([^'\"]+)['\"]", Pattern.MULTILINE);
    private static final Pattern DART_CLASS_RE = Pattern.compile("^\\s*(?:abstract\\s+)?class\\s+(\\w+)(?:\\s+extends\\s+(\\w+))?(?:\\s+implements\\s+([\\w\\s,]+))?", Pattern.MULTILINE);
    // R
    private static final Pattern R_LIBRARY_RE = Pattern.compile("(?:library|require)\\s*\\(\\s*(\\w+)\\s*\\)", Pattern.MULTILINE);
    private static final Pattern R_FUNCTION_RE = Pattern.compile("^\\s*(\\w+)\\s*<-\\s*function\\s*\\(", Pattern.MULTILINE);

    @Override
    public String getName() { return "generic_imports"; }

    @Override
    public Set<String> getSupportedLanguages() { return Set.of("ruby", "swift", "perl", "lua", "dart", "r"); }

    private final Map<String, BiConsumer<DetectorContext, DetectState>> handlers = Map.of(
            "ruby", this::detectRuby,
            "swift", this::detectSwift,
            "perl", this::detectPerl,
            "lua", this::detectLua,
            "dart", this::detectDart,
            "r", this::detectR
    );

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        String text = ctx.content();
        if (text == null || text.isEmpty()) return DetectorResult.empty();
        var handler = handlers.get(ctx.language());
        if (handler == null) return DetectorResult.empty();
        DetectState state = new DetectState(ctx);
        handler.accept(ctx, state);
        return DetectorResult.of(state.nodes, state.edges);
    }

    private static class DetectState {
        final List<CodeNode> nodes = new ArrayList<>();
        final List<CodeEdge> edges = new ArrayList<>();
        final String text;
        final String fp;
        DetectState(DetectorContext ctx) { this.text = ctx.content(); this.fp = ctx.filePath(); }
    }

    private void detectRuby(DetectorContext ctx, DetectState s) {
        addImports(s, RUBY_REQUIRE_RE);
        Matcher m = RUBY_CLASS_RE.matcher(s.text);
        while (m.find()) {
            String cn = m.group(1); String bc = m.group(2);
            String nid = s.fp + ":" + cn;
            CodeNode n = new CodeNode(); n.setId(nid); n.setKind(NodeKind.CLASS);
            n.setLabel(cn); n.setFqn(cn); n.setFilePath(s.fp);
            n.setLineStart(findLineNumber(s.text, m.start()));
            if (bc != null) n.getProperties().put("base_class", bc);
            s.nodes.add(n);
            if (bc != null) {
                CodeEdge e = new CodeEdge(); e.setId(nid + ":extends:" + bc);
                e.setKind(EdgeKind.EXTENDS); e.setSourceId(nid);
                e.setTarget(new CodeNode(bc, NodeKind.CLASS, bc)); s.edges.add(e);
            }
        }
        addMethods(s, RUBY_DEF_RE);
    }

    private void detectSwift(DetectorContext ctx, DetectState s) {
        addImports(s, SWIFT_IMPORT_RE);
        Matcher m = SWIFT_CLASS_RE.matcher(s.text);
        while (m.find()) {
            String cn = m.group(1); String supers = m.group(2);
            String nid = s.fp + ":" + cn;
            CodeNode n = new CodeNode(); n.setId(nid); n.setKind(NodeKind.CLASS);
            n.setLabel(cn); n.setFqn(cn); n.setFilePath(s.fp);
            n.setLineStart(findLineNumber(s.text, m.start()));
            s.nodes.add(n);
            if (supers != null) {
                for (String st : supers.split(",")) {
                    st = st.trim(); if (st.isEmpty()) continue;
                    CodeEdge e = new CodeEdge(); e.setId(nid + ":extends:" + st);
                    e.setKind(EdgeKind.EXTENDS); e.setSourceId(nid);
                    e.setTarget(new CodeNode(st, NodeKind.CLASS, st)); s.edges.add(e);
                }
            }
        }
        m = SWIFT_STRUCT_RE.matcher(s.text);
        while (m.find()) {
            CodeNode n = new CodeNode(); n.setId(s.fp + ":" + m.group(1));
            n.setKind(NodeKind.CLASS); n.setLabel(m.group(1)); n.setFqn(m.group(1));
            n.setFilePath(s.fp); n.setLineStart(findLineNumber(s.text, m.start()));
            n.getProperties().put("type", "struct"); s.nodes.add(n);
        }
        addMethods(s, SWIFT_FUNC_RE);
    }

    private void detectPerl(DetectorContext ctx, DetectState s) {
        addImports(s, PERL_USE_RE);
        Matcher m = PERL_PACKAGE_RE.matcher(s.text);
        while (m.find()) {
            CodeNode n = new CodeNode(); n.setId(s.fp + ":" + m.group(1));
            n.setKind(NodeKind.MODULE); n.setLabel(m.group(1)); n.setFqn(m.group(1));
            n.setFilePath(s.fp); n.setLineStart(findLineNumber(s.text, m.start()));
            s.nodes.add(n);
        }
        addMethods(s, PERL_SUB_RE);
    }

    private void detectLua(DetectorContext ctx, DetectState s) {
        addImports(s, LUA_REQUIRE_RE);
        addMethods(s, LUA_FUNCTION_RE);
    }

    private void detectDart(DetectorContext ctx, DetectState s) {
        addImports(s, DART_IMPORT_RE);
        Matcher m = DART_CLASS_RE.matcher(s.text);
        while (m.find()) {
            String cn = m.group(1); String bc = m.group(2); String ifaces = m.group(3);
            String nid = s.fp + ":" + cn;
            CodeNode n = new CodeNode(); n.setId(nid); n.setKind(NodeKind.CLASS);
            n.setLabel(cn); n.setFqn(cn); n.setFilePath(s.fp);
            n.setLineStart(findLineNumber(s.text, m.start()));
            s.nodes.add(n);
            if (bc != null) {
                CodeEdge e = new CodeEdge(); e.setId(nid + ":extends:" + bc);
                e.setKind(EdgeKind.EXTENDS); e.setSourceId(nid);
                e.setTarget(new CodeNode(bc, NodeKind.CLASS, bc)); s.edges.add(e);
            }
            if (ifaces != null) {
                for (String iface : ifaces.split(",")) {
                    iface = iface.trim(); if (iface.isEmpty()) continue;
                    CodeEdge e = new CodeEdge(); e.setId(nid + ":implements:" + iface);
                    e.setKind(EdgeKind.IMPLEMENTS); e.setSourceId(nid);
                    e.setTarget(new CodeNode(iface, NodeKind.INTERFACE, iface)); s.edges.add(e);
                }
            }
        }
    }

    private void detectR(DetectorContext ctx, DetectState s) {
        addImports(s, R_LIBRARY_RE);
        addMethods(s, R_FUNCTION_RE);
    }

    private void addImports(DetectState s, Pattern pattern) {
        Matcher m = pattern.matcher(s.text);
        while (m.find()) {
            CodeEdge e = new CodeEdge(); e.setId(s.fp + ":imports:" + m.group(1));
            e.setKind(EdgeKind.IMPORTS); e.setSourceId(s.fp);
            e.setTarget(new CodeNode(m.group(1), NodeKind.MODULE, m.group(1)));
            s.edges.add(e);
        }
    }

    private void addMethods(DetectState s, Pattern pattern) {
        Matcher m = pattern.matcher(s.text);
        while (m.find()) {
            CodeNode n = new CodeNode(); n.setId(s.fp + ":" + m.group(1));
            n.setKind(NodeKind.METHOD); n.setLabel(m.group(1)); n.setFqn(m.group(1));
            n.setFilePath(s.fp); n.setLineStart(findLineNumber(s.text, m.start()));
            s.nodes.add(n);
        }
    }
}
