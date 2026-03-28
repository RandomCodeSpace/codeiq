"""Batch script (.bat/.cmd) structure detector for labels, calls, and variables."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_LABEL_RE = re.compile(r'^:(\w+)', re.MULTILINE)
_CALL_RE = re.compile(r'CALL\s+:?(\S+)', re.IGNORECASE)
_SET_RE = re.compile(r'SET\s+(\w+)=', re.IGNORECASE)


class BatchStructureDetector:
    """Detects Batch script structures: labels, CALL commands, and SET variables."""

    name: str = "batch_structure"
    supported_languages: tuple[str, ...] = ("batch",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        try:
            text = decode_text(ctx)
        except Exception:
            return result

        filepath = ctx.file_path
        lines = text.split("\n")
        module_id = f"bat:{filepath}"

        # MODULE node for the script
        result.nodes.append(GraphNode(
            id=module_id,
            kind=NodeKind.MODULE,
            label=filepath,
            fqn=filepath,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=filepath,
                line_start=1,
            ),
        ))

        for i, line in enumerate(lines):
            line_num = i + 1
            stripped = line.strip()

            # Skip comments and echo off
            if not stripped:
                continue
            upper = stripped.upper()
            if upper.startswith("@ECHO OFF"):
                continue
            if upper.startswith("REM ") or upper == "REM":
                continue
            if stripped.startswith("::"):
                continue

            # Labels
            m = _LABEL_RE.match(stripped)
            if m:
                label_name = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"bat:{filepath}:label:{label_name}",
                    kind=NodeKind.METHOD,
                    label=f":{label_name}",
                    fqn=f"{filepath}:{label_name}",
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=line_num,
                    ),
                ))
                result.edges.append(GraphEdge(
                    source=module_id,
                    target=f"bat:{filepath}:label:{label_name}",
                    kind=EdgeKind.CONTAINS,
                    label=f"{filepath} contains :{label_name}",
                ))
                continue

            # CALL commands
            m = _CALL_RE.search(stripped)
            if m:
                call_target = m.group(1)
                # Determine if calling an internal label or external script
                if call_target.startswith(":"):
                    target_id = f"bat:{filepath}:label:{call_target[1:]}"
                elif "." in call_target:
                    # External script call
                    target_id = call_target
                else:
                    target_id = f"bat:{filepath}:label:{call_target}"

                result.edges.append(GraphEdge(
                    source=module_id,
                    target=target_id,
                    kind=EdgeKind.CALLS,
                    label=f"{filepath} calls {call_target}",
                ))

            # SET variables
            m = _SET_RE.search(stripped)
            if m:
                var_name = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"bat:{filepath}:set:{var_name}",
                    kind=NodeKind.CONFIG_DEFINITION,
                    label=f"SET {var_name}",
                    fqn=f"{filepath}:{var_name}",
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=line_num,
                    ),
                    properties={"variable": var_name},
                ))

        return result
