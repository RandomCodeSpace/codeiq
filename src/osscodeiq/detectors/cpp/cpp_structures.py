"""C/C++ structures detector for classes, structs, namespaces, functions, and enums."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_CLASS_RE = re.compile(
    r'(?:template\s*<[^>]*>\s*)?class\s+(\w+)(?:\s*:\s*(?:public|protected|private)\s+(\w+))?'
)
_STRUCT_RE = re.compile(
    r'struct\s+(\w+)(?:\s*:\s*(?:public|protected|private)\s+(\w+))?\s*\{'
)
_NAMESPACE_RE = re.compile(r'namespace\s+(\w+)\s*\{')
_FUNC_RE = re.compile(
    r'^(?:[\w:*&<>\s]+)\s+(\w+)\s*\([^)]*\)\s*(?:const\s*)?\{', re.MULTILINE
)
_INCLUDE_RE = re.compile(r'#include\s+[<"]([^>"]+)[>"]')
_ENUM_RE = re.compile(r'enum\s+(?:class\s+)?(\w+)')


def _is_forward_declaration(line: str) -> bool:
    """Check if a line is a forward declaration (ends with ; but has no body)."""
    stripped = line.rstrip()
    # A line ending with ; that contains { is a single-line definition, not a forward decl
    return stripped.endswith(';') and '{' not in stripped


class CppStructuresDetector:
    """Detects C/C++ structural elements: classes, structs, namespaces, functions, enums."""

    name: str = "cpp_structures"
    supported_languages: tuple[str, ...] = ("cpp", "c")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        file_node_id = ctx.file_path

        # Detect #include directives
        for i, line in enumerate(lines):
            m = _INCLUDE_RE.search(line)
            if not m:
                continue
            included = m.group(1)
            result.edges.append(GraphEdge(
                source=ctx.file_path,
                target=included,
                kind=EdgeKind.IMPORTS,
                label=f"{ctx.file_path} includes {included}",
            ))

        # Detect namespace declarations
        for i, line in enumerate(lines):
            m = _NAMESPACE_RE.search(line)
            if not m:
                continue
            ns_name = m.group(1)
            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:{ns_name}",
                kind=NodeKind.MODULE,
                label=ns_name,
                fqn=ns_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties={"namespace": True},
            ))

        # Detect class declarations (including template classes)
        for i, line in enumerate(lines):
            m = _CLASS_RE.search(line)
            if not m:
                continue
            # Skip forward declarations
            if _is_forward_declaration(line):
                continue
            class_name = m.group(1)
            base_class = m.group(2)
            is_template = 'template' in line[:m.start() + len(m.group(0))]

            props: dict = {}
            if is_template:
                props["is_template"] = True

            node_id = f"{ctx.file_path}:{class_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.CLASS,
                label=class_name,
                fqn=class_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties=props,
            ))

            if base_class:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=base_class,
                    kind=EdgeKind.EXTENDS,
                    label=f"{class_name} extends {base_class}",
                ))

        # Detect struct declarations
        for i, line in enumerate(lines):
            m = _STRUCT_RE.search(line)
            if not m:
                continue
            if _is_forward_declaration(line):
                continue
            struct_name = m.group(1)
            base_struct = m.group(2)

            node_id = f"{ctx.file_path}:{struct_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.CLASS,
                label=struct_name,
                fqn=struct_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties={"struct": True},
            ))

            if base_struct:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=base_struct,
                    kind=EdgeKind.EXTENDS,
                    label=f"{struct_name} extends {base_struct}",
                ))

        # Detect enum declarations
        for i, line in enumerate(lines):
            m = _ENUM_RE.search(line)
            if not m:
                continue
            if _is_forward_declaration(line):
                continue
            enum_name = m.group(1)

            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:{enum_name}",
                kind=NodeKind.ENUM,
                label=enum_name,
                fqn=enum_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
            ))

        # Detect file-scope function definitions
        for m in _FUNC_RE.finditer(text):
            func_name = m.group(1)
            # Compute line number from character offset
            line_num = text[:m.start()].count("\n") + 1

            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:{func_name}",
                kind=NodeKind.METHOD,
                label=func_name,
                fqn=func_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=line_num,
                ),
            ))

        return result
