"""Tests for Go detectors."""

from code_intelligence.detectors.base import DetectorContext
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content, file_path="main.go"):
    return DetectorContext(
        file_path=file_path,
        language="go",
        content=content,
        module_name="pkg",
    )


class TestGoStructuresDetector:
    def test_detect_struct_and_interface(self):
        source = b'''
package handlers

type UserService struct {
    db *sql.DB
}

type Repository interface {
    FindByID(id int) (*User, error)
}

func (s *UserService) GetUser(id int) (*User, error) {
    return nil, nil
}

func NewUserService(db *sql.DB) *UserService {
    return &UserService{db: db}
}
'''
        from code_intelligence.detectors.go.go_structures import GoStructuresDetector

        result = GoStructuresDetector().detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        interfaces = [n for n in result.nodes if n.kind == NodeKind.INTERFACE]
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(classes) >= 1
        assert len(interfaces) >= 1
        assert len(methods) >= 2  # GetUser + NewUserService

    def test_detect_package(self):
        source = b'''
package main

func main() {
}
'''
        from code_intelligence.detectors.go.go_structures import GoStructuresDetector

        result = GoStructuresDetector().detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) == 1
        assert modules[0].label == "main"

    def test_detect_imports(self):
        source = b'''
package main

import (
    "fmt"
    "net/http"
)

import "os"

func main() {
}
'''
        from code_intelligence.detectors.go.go_structures import GoStructuresDetector

        result = GoStructuresDetector().detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        import_targets = {e.target for e in import_edges}
        assert "fmt" in import_targets
        assert "net/http" in import_targets
        assert "os" in import_targets

    def test_exported_property(self):
        source = b'''
package util

type PublicStruct struct {}
type privateStruct struct {}

func PublicFunc() {}
func privateFunc() {}
'''
        from code_intelligence.detectors.go.go_structures import GoStructuresDetector

        result = GoStructuresDetector().detect(_ctx(source))
        node_map = {n.label: n for n in result.nodes if n.kind in (NodeKind.CLASS, NodeKind.METHOD)}
        assert node_map["PublicStruct"].properties["exported"] is True
        assert node_map["privateStruct"].properties["exported"] is False
        assert node_map["PublicFunc"].properties["exported"] is True
        assert node_map["privateFunc"].properties["exported"] is False

    def test_method_defines_edge(self):
        source = b'''
package svc

type Server struct {}

func (s *Server) Start() {}
func (s Server) Stop() {}
'''
        from code_intelligence.detectors.go.go_structures import GoStructuresDetector

        result = GoStructuresDetector().detect(_ctx(source))
        defines_edges = [e for e in result.edges if e.kind == EdgeKind.DEFINES]
        assert len(defines_edges) == 2
        sources = {e.source for e in defines_edges}
        assert "main.go:Server" in sources

    def test_supported_languages(self):
        from code_intelligence.detectors.go.go_structures import GoStructuresDetector

        d = GoStructuresDetector()
        assert "go" in d.supported_languages
