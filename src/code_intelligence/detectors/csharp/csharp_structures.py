"""Regex-based C# structures detector for C# source files."""

from __future__ import annotations

import re
from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text, find_line_number
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_CLASS_RE = re.compile(
    r'(?:public|internal|private|protected)?\s*'
    r'(?:abstract|static|sealed|partial)?\s*'
    r'class\s+(\w+)(?:\s*<[^>]+>)?(?:\s*:\s*([^{]+))?'
)
_INTERFACE_RE = re.compile(
    r'(?:public|internal)?\s*interface\s+(\w+)(?:\s*<[^>]+>)?(?:\s*:\s*([^{]+))?'
)
_ENUM_RE = re.compile(r'(?:public|internal)?\s*enum\s+(\w+)')
_NAMESPACE_RE = re.compile(r'namespace\s+([\w.]+)')
_METHOD_RE = re.compile(
    r'(?:public|protected|private|internal)\s+'
    r'(?:static\s+|virtual\s+|override\s+|async\s+|abstract\s+)*'
    r'(?:[\w<>\[\]?,\s]+)\s+(\w+)\s*\('
)
_USING_RE = re.compile(r'^\s*using\s+([\w.]+)\s*;', re.MULTILINE)
_HTTP_ATTR_RE = re.compile(r'\[(Http(?:Get|Post|Put|Delete|Patch))\s*(?:\("([^"]*)"\))?\]')
_ROUTE_RE = re.compile(r'\[Route\("([^"]*)"\)\]')
_API_CONTROLLER_RE = re.compile(r'\[ApiController\]')
_FUNCTION_RE = re.compile(r'\[Function\("([^"]+)"\)\]')
_HTTP_TRIGGER_RE = re.compile(r'\[HttpTrigger\(')



def _parse_base_types(base_str: str | None) -> tuple[str | None, list[str]]:
    """Parse the base type list after ':' in a class declaration.

    Returns (base_class_or_none, list_of_interfaces).
    Convention: interfaces in C# start with 'I' followed by an uppercase letter.
    """
    if not base_str:
        return None, []
    parts = [p.strip() for p in base_str.split(",")]
    parts = [p for p in parts if p]
    base_class = None
    interfaces: list[str] = []
    for part in parts:
        # Strip generic parameters for classification
        clean = re.sub(r'<[^>]*>', '', part).strip()
        if not clean:
            continue
        if len(clean) >= 2 and clean[0] == "I" and clean[1].isupper():
            interfaces.append(clean)
        elif base_class is None:
            base_class = clean
        else:
            # Ambiguous; treat as interface
            interfaces.append(clean)
    return base_class, interfaces


class CSharpStructuresDetector:
    """Detects C# classes, interfaces, enums, namespaces, methods, and endpoints."""

    name: str = "csharp_structures"
    supported_languages: tuple[str, ...] = ("csharp",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        # Namespace
        namespace: str | None = None
        ns_match = _NAMESPACE_RE.search(text)
        if ns_match:
            namespace = ns_match.group(1)
            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:namespace:{namespace}",
                kind=NodeKind.MODULE,
                label=namespace,
                fqn=namespace,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=find_line_number(text, ns_match.start()),
                ),
                properties={},
            ))

        # Using statements (imports)
        for m in _USING_RE.finditer(text):
            using_ns = m.group(1)
            result.edges.append(GraphEdge(
                source=ctx.file_path,
                target=using_ns,
                kind=EdgeKind.IMPORTS,
                label=f"{ctx.file_path} imports {using_ns}",
            ))

        # Detect class-level route for ASP.NET controllers
        class_route: str | None = None
        is_api_controller = bool(_API_CONTROLLER_RE.search(text))

        # Classes
        for m in _CLASS_RE.finditer(text):
            class_name = m.group(1)
            base_str = m.group(2)
            line_num = find_line_number(text, m.start())

            # Check if abstract
            match_text = text[max(0, m.start() - 60):m.start() + len(m.group(0))]
            is_abstract = "abstract" in match_text
            kind = NodeKind.ABSTRACT_CLASS if is_abstract else NodeKind.CLASS

            fqn = f"{namespace}.{class_name}" if namespace else class_name
            node_id = f"{ctx.file_path}:{class_name}"

            base_class, iface_list = _parse_base_types(base_str)

            properties: dict[str, Any] = {}
            if is_abstract:
                properties["is_abstract"] = True
            if base_class:
                properties["base_class"] = base_class
            if iface_list:
                properties["interfaces"] = iface_list

            result.nodes.append(GraphNode(
                id=node_id,
                kind=kind,
                label=class_name,
                fqn=fqn,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=line_num,
                ),
                properties=properties,
            ))

            # Extends edge
            if base_class:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=f"*:{base_class}",
                    kind=EdgeKind.EXTENDS,
                    label=f"{class_name} extends {base_class}",
                ))

            # Implements edges
            for iface in iface_list:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=f"*:{iface}",
                    kind=EdgeKind.IMPLEMENTS,
                    label=f"{class_name} implements {iface}",
                ))

            # Check for [Route] attribute above this class
            class_line_idx = line_num - 1
            for j in range(max(0, class_line_idx - 5), class_line_idx):
                route_m = _ROUTE_RE.search(lines[j])
                if route_m:
                    class_route = route_m.group(1)
                    # Replace [controller] placeholder
                    controller_name = class_name
                    if controller_name.endswith("Controller"):
                        controller_name = controller_name[:-len("Controller")]
                    class_route = class_route.replace("[controller]", controller_name)
                    break

        # Interfaces
        for m in _INTERFACE_RE.finditer(text):
            iface_name = m.group(1)
            base_str = m.group(2)
            fqn = f"{namespace}.{iface_name}" if namespace else iface_name
            node_id = f"{ctx.file_path}:{iface_name}"

            _, extended_ifaces = _parse_base_types(base_str)

            properties = {}
            if extended_ifaces:
                properties["extends"] = extended_ifaces

            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.INTERFACE,
                label=iface_name,
                fqn=fqn,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=find_line_number(text, m.start()),
                ),
                properties=properties,
            ))

            for ext in extended_ifaces:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=f"*:{ext}",
                    kind=EdgeKind.EXTENDS,
                    label=f"{iface_name} extends {ext}",
                ))

        # Enums
        for m in _ENUM_RE.finditer(text):
            enum_name = m.group(1)
            fqn = f"{namespace}.{enum_name}" if namespace else enum_name
            node_id = f"{ctx.file_path}:{enum_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.ENUM,
                label=enum_name,
                fqn=fqn,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=find_line_number(text, m.start()),
                ),
                properties={},
            ))

        # Methods and HTTP endpoints
        for i, line in enumerate(lines):
            method_m = _METHOD_RE.search(line)
            if not method_m:
                continue

            method_name = method_m.group(1)
            # Skip common false positives
            if method_name in ("if", "for", "while", "switch", "catch", "using", "return", "new", "class"):
                continue

            # Look backwards for HTTP attribute annotations
            http_method_str: str | None = None
            http_path: str | None = None
            for j in range(max(0, i - 5), i):
                http_m = _HTTP_ATTR_RE.search(lines[j])
                if http_m:
                    attr_name = http_m.group(1)
                    http_method_str = attr_name.replace("Http", "").upper()
                    http_path = http_m.group(2)
                    break

            # Build endpoint node for HTTP-annotated methods
            if http_method_str is not None:
                path = http_path or ""
                if class_route:
                    full_path = f"/{class_route.strip('/')}"
                    if path:
                        full_path = f"{full_path}/{path.lstrip('/')}"
                else:
                    full_path = f"/{path.lstrip('/')}" if path else "/"

                endpoint_label = f"{http_method_str} {full_path}"
                endpoint_id = f"endpoint:{ctx.module_name}:{method_name}:{http_method_str}:{full_path}"

                result.nodes.append(GraphNode(
                    id=endpoint_id,
                    kind=NodeKind.ENDPOINT,
                    label=endpoint_label,
                    fqn=f"{namespace}.{method_name}" if namespace else method_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=ctx.file_path,
                        line_start=i + 1,
                    ),
                    annotations=[f"[{http_m.group(1)}]"],
                    properties={
                        "http_method": http_method_str,
                        "path": full_path,
                    },
                ))

        # Azure Functions
        for i, line in enumerate(lines):
            func_m = _FUNCTION_RE.search(line)
            if not func_m:
                continue

            func_name = func_m.group(1)
            # Check if next few lines have HttpTrigger
            is_http_trigger = False
            for j in range(i, min(i + 10, len(lines))):
                if _HTTP_TRIGGER_RE.search(lines[j]):
                    is_http_trigger = True
                    break

            func_id = f"{ctx.file_path}:function:{func_name}"
            properties: dict[str, Any] = {"function_name": func_name}
            if is_http_trigger:
                properties["trigger_type"] = "http"

            result.nodes.append(GraphNode(
                id=func_id,
                kind=NodeKind.AZURE_FUNCTION,
                label=f"Function({func_name})",
                fqn=f"{namespace}.{func_name}" if namespace else func_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                annotations=[f'[Function("{func_name}")]'],
                properties=properties,
            ))

        return result
