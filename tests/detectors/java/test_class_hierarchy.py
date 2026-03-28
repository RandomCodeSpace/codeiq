"""Tests for Java class hierarchy detector."""

import tree_sitter
import tree_sitter_java

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.java.class_hierarchy import ClassHierarchyDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "Test.java", language: str = "java") -> DetectorContext:
    content_bytes = content.encode()
    parser = tree_sitter.Parser(tree_sitter.Language(tree_sitter_java.language()))
    tree = parser.parse(content_bytes)
    return DetectorContext(
        file_path=path, language=language, content=content_bytes, tree=tree, module_name="test"
    )


def _ctx_no_tree(content: str = "", path: str = "Test.java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language="java", content=content.encode(), tree=None, module_name="test"
    )


class TestClassHierarchyDetector:
    def setup_method(self):
        self.detector = ClassHierarchyDetector()

    def test_detects_class_extends(self):
        source = """\
public class AdminUser extends User {
    private boolean superAdmin;
}
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 1
        assert classes[0].label == "AdminUser"
        extends_edges = [e for e in result.edges if e.kind == EdgeKind.EXTENDS]
        assert len(extends_edges) == 1
        assert extends_edges[0].target == "*:User"

    def test_detects_class_implements(self):
        source = """\
public class OrderService implements Serializable, Comparable<OrderService> {
    public int compareTo(OrderService other) { return 0; }
}
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 1
        impl_edges = [e for e in result.edges if e.kind == EdgeKind.IMPLEMENTS]
        assert len(impl_edges) == 2

    def test_detects_interface(self):
        source = """\
public interface Repository<T> extends Closeable {
    T findById(Long id);
}
"""
        result = self.detector.detect(_ctx(source))
        interfaces = [n for n in result.nodes if n.kind == NodeKind.INTERFACE]
        assert len(interfaces) == 1
        assert interfaces[0].label == "Repository"
        extends_edges = [e for e in result.edges if e.kind == EdgeKind.EXTENDS]
        assert len(extends_edges) == 1
        assert extends_edges[0].target == "*:Closeable"

    def test_detects_enum(self):
        source = """\
public enum Status {
    ACTIVE, INACTIVE, PENDING;
}
"""
        result = self.detector.detect(_ctx(source))
        enums = [n for n in result.nodes if n.kind == NodeKind.ENUM]
        assert len(enums) == 1
        assert enums[0].label == "Status"

    def test_detects_abstract_class(self):
        source = """\
public abstract class AbstractProcessor {
    public abstract void process();
}
"""
        result = self.detector.detect(_ctx(source))
        abstracts = [n for n in result.nodes if n.kind == NodeKind.ABSTRACT_CLASS]
        assert len(abstracts) == 1
        assert abstracts[0].properties["is_abstract"] is True

    def test_no_tree_returns_empty(self):
        result = self.detector.detect(_ctx_no_tree("public class Foo {}"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_empty_file_returns_nothing(self):
        result = self.detector.detect(_ctx(""))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
public class Dog extends Animal implements Runnable {
    public void run() {}
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
