"""Tests for C/C++ structures detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.cpp.cpp_structures import CppStructuresDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "main.cpp", language: str = "cpp") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestCppStructuresDetector:
    def setup_method(self):
        self.detector = CppStructuresDetector()

    def test_detects_class(self):
        source = """\
class UserService : public IService {
public:
    void GetUser(int id);
    void UpdateUser(int id, const std::string& name);
};
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) >= 1
        assert any(n.label == "UserService" for n in classes)
        extends_edges = [e for e in result.edges if e.kind == EdgeKind.EXTENDS]
        assert len(extends_edges) >= 1
        assert extends_edges[0].target == "IService"

    def test_detects_struct(self):
        source = """\
struct Config {
    std::string host;
    int port;
};
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) >= 1
        config = [n for n in classes if n.label == "Config"]
        assert len(config) == 1
        assert config[0].properties.get("struct") is True

    def test_detects_namespace(self):
        source = """\
namespace myapp {
    class Foo {};
}
"""
        result = self.detector.detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) >= 1
        assert modules[0].label == "myapp"

    def test_detects_enum(self):
        source = """\
enum class Status { Active, Inactive, Pending };
"""
        result = self.detector.detect(_ctx(source))
        enums = [n for n in result.nodes if n.kind == NodeKind.ENUM]
        assert len(enums) == 1
        assert enums[0].label == "Status"

    def test_detects_function(self):
        source = """\
void process_data(int* data, int size) {
    for (int i = 0; i < size; i++) {
        printf("%d", data[i]);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) >= 1
        assert any(n.label == "process_data" for n in methods)

    def test_detects_includes(self):
        source = """\
#include <iostream>
#include "database.h"
#include <vector>

int main() {
    return 0;
}
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) >= 3
        targets = {e.target for e in import_edges}
        assert "iostream" in targets
        assert "database.h" in targets
        assert "vector" in targets

    def test_detects_template_class(self):
        source = """\
template<typename T> class Container : public BaseContainer {
public:
    void add(T item);
};
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) >= 1
        container = [n for n in classes if n.label == "Container"]
        assert len(container) == 1
        assert container[0].properties.get("is_template") is True

    def test_skips_forward_declarations(self):
        source = """\
class ForwardDeclared;
struct ForwardStruct;
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 0

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("// just a comment\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_determinism(self):
        source = """\
#include <string>
namespace app {
class Service : public IBase {
public:
    void run();
};
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
