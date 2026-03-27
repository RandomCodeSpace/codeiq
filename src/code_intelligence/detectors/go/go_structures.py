"""Regex-based Go structures detector for Go source files."""

from __future__ import annotations

import re
from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_STRUCT_RE = re.compile(r'type\s+(\w+)\s+struct\s*\{')
_INTERFACE_RE = re.compile(r'type\s+(\w+)\s+interface\s*\{')
_METHOD_RE = re.compile(r'func\s+\(\s*\w+\s+\*?(\w+)\s*\)\s+(\w+)\s*\(')
_FUNC_RE = re.compile(r'^func\s+(\w+)\s*\(', re.MULTILINE)
_PACKAGE_RE = re.compile(r'^package\s+(\w+)', re.MULTILINE)
_IMPORT_SINGLE_RE = re.compile(r'^import\s+"([^"]+)"', re.MULTILINE)
_IMPORT_BLOCK_RE = re.compile(r'import\s*\((.*?)\)', re.DOTALL)
_IMPORT_PATH_RE = re.compile(r'"([^"]+)"')


def _find_line_number(text: str, pos: int) -> int:
    """Return the 1-based line number for a character offset."""
    return text[:pos].count("\n") + 1


class GoStructuresDetector:
    """Detects Go structs, interfaces, methods, functions, and packages."""

    name: str = "go_structures"
    supported_languages: tuple[str, ...] = ("go",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        # Package declaration (one per file)
        pkg_match = _PACKAGE_RE.search(text)
        pkg_name = pkg_match.group(1) if pkg_match else None
        if pkg_name:
            pkg_node_id = f"{ctx.file_path}:package:{pkg_name}"
            result.nodes.append(GraphNode(
                id=pkg_node_id,
                kind=NodeKind.MODULE,
                label=pkg_name,
                fqn=pkg_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, pkg_match.start()),
                ),
                properties={"package": pkg_name},
            ))

        # Imports
        file_node_id = ctx.file_path
        for m in _IMPORT_SINGLE_RE.finditer(text):
            import_path = m.group(1)
            result.edges.append(GraphEdge(
                source=file_node_id,
                target=import_path,
                kind=EdgeKind.IMPORTS,
                label=f"{ctx.file_path} imports {import_path}",
            ))

        for block_m in _IMPORT_BLOCK_RE.finditer(text):
            block_text = block_m.group(1)
            for path_m in _IMPORT_PATH_RE.finditer(block_text):
                import_path = path_m.group(1)
                result.edges.append(GraphEdge(
                    source=file_node_id,
                    target=import_path,
                    kind=EdgeKind.IMPORTS,
                    label=f"{ctx.file_path} imports {import_path}",
                ))

        # Struct declarations
        for m in _STRUCT_RE.finditer(text):
            struct_name = m.group(1)
            exported = struct_name[0].isupper()
            node_id = f"{ctx.file_path}:{struct_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.CLASS,
                label=struct_name,
                fqn=f"{pkg_name}.{struct_name}" if pkg_name else struct_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties={"exported": exported, "type": "struct"},
            ))

        # Interface declarations
        for m in _INTERFACE_RE.finditer(text):
            iface_name = m.group(1)
            exported = iface_name[0].isupper()
            node_id = f"{ctx.file_path}:{iface_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.INTERFACE,
                label=iface_name,
                fqn=f"{pkg_name}.{iface_name}" if pkg_name else iface_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties={"exported": exported},
            ))

        # Method declarations (receiver methods)
        for m in _METHOD_RE.finditer(text):
            receiver_type = m.group(1)
            method_name = m.group(2)
            exported = method_name[0].isupper()
            node_id = f"{ctx.file_path}:{receiver_type}:{method_name}"
            type_node_id = f"{ctx.file_path}:{receiver_type}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.METHOD,
                label=f"{receiver_type}.{method_name}",
                fqn=f"{pkg_name}.{receiver_type}.{method_name}" if pkg_name else f"{receiver_type}.{method_name}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties={"exported": exported, "receiver_type": receiver_type},
            ))
            result.edges.append(GraphEdge(
                source=type_node_id,
                target=node_id,
                kind=EdgeKind.DEFINES,
                label=f"{receiver_type} defines {method_name}",
            ))

        # Package-level function declarations (exclude methods already matched)
        method_positions = {m.start() for m in _METHOD_RE.finditer(text)}
        for m in _FUNC_RE.finditer(text):
            if m.start() in method_positions:
                continue
            func_name = m.group(1)
            exported = func_name[0].isupper()
            node_id = f"{ctx.file_path}:{func_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.METHOD,
                label=func_name,
                fqn=f"{pkg_name}.{func_name}" if pkg_name else func_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties={"exported": exported, "type": "function"},
            ))

        return result
