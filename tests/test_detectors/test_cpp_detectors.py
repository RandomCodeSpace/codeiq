"""Tests for C/C++ detectors."""
from osscodeiq.detectors.base import DetectorContext
from osscodeiq.models.graph import NodeKind


def _ctx(content, language="cpp", file_path="main.cpp"):
    return DetectorContext(file_path=file_path, language=language, content=content, module_name="lib")


class TestCppStructuresDetector:
    def test_detect_class_and_struct(self):
        source = b'''
#include <string>
#include "database.h"
namespace myapp {
class UserService : public IService {
public:
    void GetUser(int id);
};
struct Config {
    std::string host;
    int port;
};
enum class Status { Active, Inactive };
}
'''
        from osscodeiq.detectors.cpp.cpp_structures import CppStructuresDetector
        result = CppStructuresDetector().detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        enums = [n for n in result.nodes if n.kind == NodeKind.ENUM]
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(classes) >= 2  # UserService + Config
        assert len(enums) >= 1
        assert len(modules) >= 1

    def test_detect_c_functions(self):
        source = b'''
#include <stdio.h>
void process_data(int* data, int size) {
    for (int i = 0; i < size; i++) {
        printf("%d", data[i]);
    }
}
'''
        from osscodeiq.detectors.cpp.cpp_structures import CppStructuresDetector
        result = CppStructuresDetector().detect(_ctx(source, language="c", file_path="main.c"))
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) >= 1
