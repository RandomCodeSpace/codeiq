"""Markdown structure detector for headings and internal links."""

from __future__ import annotations

import os
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

_HEADING_RE = re.compile(r'^(#{1,6})\s+(.+)$', re.MULTILINE)
_LINK_RE = re.compile(r'\[([^\]]+)\]\(([^)]+)\)')
_EXTERNAL_RE = re.compile(r'^https?://')


class MarkdownStructureDetector:
    """Detects Markdown headings and internal file links."""

    name: str = "markdown_structure"
    supported_languages: tuple[str, ...] = ("markdown",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        try:
            text = decode_text(ctx)
        except Exception:
            return result

        filepath = ctx.file_path
        lines = text.split("\n")

        # Find first H1 for module label
        first_h1: str | None = None
        for line in lines:
            m = _HEADING_RE.match(line)
            if m and len(m.group(1)) == 1:
                first_h1 = m.group(2).strip()
                break

        module_label = first_h1 or os.path.basename(filepath)
        module_id = f"md:{filepath}"

        # MODULE node for the file
        result.nodes.append(GraphNode(
            id=module_id,
            kind=NodeKind.MODULE,
            label=module_label,
            fqn=filepath,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=filepath,
                line_start=1,
            ),
        ))

        # CONFIG_KEY nodes for each heading
        for i, line in enumerate(lines):
            m = _HEADING_RE.match(line)
            if not m:
                continue
            level = len(m.group(1))
            heading_text = m.group(2).strip()
            line_num = i + 1

            heading_id = f"md:{filepath}:heading:{line_num}"
            result.nodes.append(GraphNode(
                id=heading_id,
                kind=NodeKind.CONFIG_KEY,
                label=heading_text,
                fqn=f"{filepath}:heading:{heading_text}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=filepath,
                    line_start=line_num,
                ),
                properties={"level": level, "text": heading_text},
            ))

            result.edges.append(GraphEdge(
                source=module_id,
                target=heading_id,
                kind=EdgeKind.CONTAINS,
                label=f"{filepath} contains heading {heading_text}",
            ))

        # DEPENDS_ON edges for internal links
        for i, line in enumerate(lines):
            for m in _LINK_RE.finditer(line):
                link_text = m.group(1)
                link_target = m.group(2)

                # Skip external URLs
                if _EXTERNAL_RE.match(link_target):
                    continue

                # Strip anchor fragments
                link_path = link_target.split("#")[0]
                if not link_path:
                    continue

                result.edges.append(GraphEdge(
                    source=module_id,
                    target=link_path,
                    kind=EdgeKind.DEPENDS_ON,
                    label=f"{filepath} links to {link_path}",
                    properties={"link_text": link_text},
                ))

        return result
