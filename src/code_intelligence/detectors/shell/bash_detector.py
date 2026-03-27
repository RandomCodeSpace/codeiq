"""Bash/Shell script detector for functions, sourced files, exports, and tool usage."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_FUNC_RE = re.compile(r'(?:function\s+(\w+)|(\w+)\s*\(\s*\))\s*\{')
_SOURCE_RE = re.compile(r'(?:source|\.) (?:")?([^\s"]+)')
_SHEBANG_RE = re.compile(r'^#!\s*/(?:usr/)?(?:bin/)?(?:env\s+)?(\w+)')
_EXPORT_RE = re.compile(r'export\s+(\w+)=')

_INFRA_TOOLS = {"docker", "kubectl", "terraform", "aws", "az", "gcloud"}
_TOOL_RE = re.compile(
    r'\b(' + '|'.join(re.escape(t) for t in sorted(_INFRA_TOOLS)) + r')\b'
)


class BashDetector:
    """Detects Bash/Shell script structures: functions, sourced files, exports, and tool usage."""

    name: str = "bash"
    supported_languages: tuple[str, ...] = ("bash",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")
        lines = text.split("\n")

        # Detect shebang
        if lines:
            m = _SHEBANG_RE.match(lines[0])
            if m:
                shell = m.group(1)
                result.nodes.append(GraphNode(
                    id=ctx.file_path,
                    kind=NodeKind.MODULE,
                    label=ctx.file_path,
                    fqn=ctx.file_path,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=ctx.file_path,
                        line_start=1,
                    ),
                    properties={"shell": shell},
                ))

        # Detect function definitions
        for i, line in enumerate(lines):
            m = _FUNC_RE.search(line)
            if not m:
                continue
            func_name = m.group(1) or m.group(2)

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
            ))

        # Detect source/dot-source imports
        for i, line in enumerate(lines):
            m = _SOURCE_RE.search(line)
            if not m:
                continue
            sourced_file = m.group(1)
            result.edges.append(GraphEdge(
                source=ctx.file_path,
                target=sourced_file,
                kind=EdgeKind.IMPORTS,
                label=f"{ctx.file_path} sources {sourced_file}",
            ))

        # Detect exports
        for i, line in enumerate(lines):
            m = _EXPORT_RE.search(line)
            if not m:
                continue
            var_name = m.group(1)

            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:export:{var_name}",
                kind=NodeKind.CONFIG_DEFINITION,
                label=f"export {var_name}",
                fqn=var_name,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
            ))

        # Detect infrastructure tool usage
        tools_seen: set[str] = set()
        for i, line in enumerate(lines):
            # Skip comments
            stripped = line.lstrip()
            if stripped.startswith('#'):
                continue
            for m in _TOOL_RE.finditer(line):
                tool = m.group(1)
                if tool not in tools_seen:
                    tools_seen.add(tool)
                    result.edges.append(GraphEdge(
                        source=ctx.file_path,
                        target=tool,
                        kind=EdgeKind.CALLS,
                        label=f"{ctx.file_path} uses {tool}",
                        properties={"tool": tool},
                    ))

        return result
