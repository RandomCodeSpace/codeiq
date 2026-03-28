"""Tests for Java public API method detector."""

import tree_sitter
import tree_sitter_java

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.java.public_api import PublicApiDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "Service.java", language: str = "java") -> DetectorContext:
    content_bytes = content.encode()
    parser = tree_sitter.Parser(tree_sitter.Language(tree_sitter_java.language()))
    tree = parser.parse(content_bytes)
    return DetectorContext(
        file_path=path, language=language, content=content_bytes, tree=tree, module_name="test"
    )


def _ctx_no_tree(content: str = "", path: str = "Service.java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language="java", content=content.encode(), tree=None, module_name="test"
    )


class TestPublicApiDetector:
    def setup_method(self):
        self.detector = PublicApiDetector()

    def test_detects_public_methods(self):
        source = """\
public class UserService {

    public User findById(Long id) {
        return repo.findById(id).orElseThrow();
    }

    public List<User> findAll() {
        return repo.findAll();
    }
}
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 2
        names = {n.label.split(".")[-1] for n in methods}
        assert "findById" in names
        assert "findAll" in names

    def test_detects_protected_methods(self):
        source = """\
public class BaseService {

    protected void validate(Object obj) {
        if (obj == null) throw new IllegalArgumentException("null");
    }
}
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 1
        assert methods[0].properties["visibility"] == "protected"

    def test_skips_private_methods(self):
        source = """\
public class Foo {
    private void secret() {
        System.out.println("hidden");
    }
}
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 0

    def test_skips_trivial_getters(self):
        source = """\
public class User {
    public String getName() { return name; }
    public void setName(String n) { name = n; }
}
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 0

    def test_creates_defines_edges(self):
        source = """\
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
"""
        result = self.detector.detect(_ctx(source))
        define_edges = [e for e in result.edges if e.kind == EdgeKind.DEFINES]
        assert len(define_edges) >= 1

    def test_no_tree_returns_empty(self):
        result = self.detector.detect(_ctx_no_tree("public class Foo {}"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_empty_class_returns_nothing(self):
        result = self.detector.detect(_ctx("public class Empty { }"))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
public class MathService {
    public int add(int a, int b) {
        return a + b;
    }
    public int subtract(int a, int b) {
        return a - b;
    }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
