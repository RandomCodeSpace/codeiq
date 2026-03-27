"""Kafka protocol message detector for Java source files."""

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

_PROTOCOL_MSG_RE = re.compile(
    r"class\s+(\w+)\s+extends\s+(AbstractRequest|AbstractResponse)(?!\.)\b"
)


class KafkaProtocolDetector:
    """Detects classes extending AbstractRequest or AbstractResponse — Kafka's binary protocol message pattern."""

    name: str = "kafka_protocol"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        if "AbstractRequest" not in text and "AbstractResponse" not in text:
            return result

        lines = text.split("\n")

        for i, line in enumerate(lines):
            m = _PROTOCOL_MSG_RE.search(line)
            if not m:
                continue

            class_name = m.group(1)
            parent_class = m.group(2)
            protocol_type = "request" if parent_class == "AbstractRequest" else "response"

            node_id = f"{ctx.file_path}:{class_name}"

            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.PROTOCOL_MESSAGE,
                    label=class_name,
                    location=SourceLocation(
                        file_path=ctx.file_path,
                        line_start=i + 1,
                    ),
                    properties={"protocol_type": protocol_type},
                )
            )

            result.edges.append(
                GraphEdge(
                    source=node_id,
                    target=f"*:{parent_class}",
                    kind=EdgeKind.EXTENDS,
                    label=f"{class_name} extends {parent_class}",
                )
            )

        return result
