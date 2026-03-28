"""PowerShell script detector for functions, modules, parameters, and dot-sourcing."""

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

_FUNC_RE = re.compile(r'function\s+([\w-]+)\s*(?:\([^)]*\))?\s*\{', re.IGNORECASE)
_IMPORT_RE = re.compile(r'Import-Module\s+(\S+)', re.IGNORECASE)
_DOT_SOURCE_RE = re.compile(r'\.\s+["\']?(\S+\.ps(?:1|m1))["\']?')
_PARAM_RE = re.compile(r'\[Parameter[^]]*\]\s*\[(\w+)\]\s*\$(\w+)')
_CMDLET_BINDING_RE = re.compile(r'\[CmdletBinding\(\)\]', re.IGNORECASE)


class PowerShellDetector:
    """Detects PowerShell script structures: functions, modules, parameters, and dot-sourcing."""

    name: str = "powershell"
    supported_languages: tuple[str, ...] = ("powershell",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        # Track which functions have CmdletBinding for advanced function marking
        # We do a two-pass approach: first find functions, then check for CmdletBinding nearby
        func_positions: list[tuple[int, str]] = []

        # Detect function definitions
        for i, line in enumerate(lines):
            m = _FUNC_RE.search(line)
            if not m:
                continue
            func_name = m.group(1)
            func_positions.append((i, func_name))

            # Check if [CmdletBinding()] appears within the next few lines
            is_advanced = False
            for j in range(i + 1, min(i + 5, len(lines))):
                if _CMDLET_BINDING_RE.search(lines[j]):
                    is_advanced = True
                    break

            props: dict = {}
            if is_advanced:
                props["advanced_function"] = True

            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:{func_name}",
                kind=NodeKind.METHOD,
                label=func_name,
                fqn=func_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties=props,
            ))

        # Detect Import-Module statements
        for i, line in enumerate(lines):
            m = _IMPORT_RE.search(line)
            if not m:
                continue
            module_name = m.group(1)
            result.edges.append(GraphEdge(
                source=ctx.file_path,
                target=module_name,
                kind=EdgeKind.IMPORTS,
                label=f"{ctx.file_path} imports module {module_name}",
            ))

        # Detect dot-sourcing
        for i, line in enumerate(lines):
            m = _DOT_SOURCE_RE.search(line)
            if not m:
                continue
            sourced_file = m.group(1)
            result.edges.append(GraphEdge(
                source=ctx.file_path,
                target=sourced_file,
                kind=EdgeKind.IMPORTS,
                label=f"{ctx.file_path} dot-sources {sourced_file}",
            ))

        # Detect typed parameters with [Parameter] attribute
        for i, line in enumerate(lines):
            m = _PARAM_RE.search(line)
            if not m:
                continue
            param_type = m.group(1)
            param_name = m.group(2)

            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:param:{param_name}",
                kind=NodeKind.CONFIG_DEFINITION,
                label=f"${param_name}: {param_type}",
                fqn=param_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties={"param_type": param_type},
            ))

        return result
