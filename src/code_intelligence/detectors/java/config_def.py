"""ConfigDef detector for Java source files using Kafka's ConfigDef.define() pattern."""

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

_CLASS_RE = re.compile(r"(?:public\s+)?class\s+(\w+)")
_DEFINE_RE = re.compile(r'\.define\s*\(\s*"([^"]+)"')


class ConfigDefDetector:
    """Detects Kafka ConfigDef.define() configuration definitions."""

    name: str = "config_def"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        if "ConfigDef" not in text:
            return result

        lines = text.split("\n")

        # Find class name
        class_name: str | None = None
        for line in lines:
            cm = _CLASS_RE.search(line)
            if cm:
                class_name = cm.group(1)
                break

        if not class_name:
            return result

        class_node_id = f"{ctx.file_path}:{class_name}"
        seen_keys: set[str] = set()

        # Find all .define("config.key") calls
        for i, line in enumerate(lines):
            m = _DEFINE_RE.search(line)
            if not m:
                continue

            config_key = m.group(1)
            if config_key in seen_keys:
                continue
            seen_keys.add(config_key)

            node_id = f"config:{config_key}"

            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.CONFIG_DEFINITION,
                label=config_key,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties={"config_key": config_key},
            ))

            result.edges.append(GraphEdge(
                source=class_node_id,
                target=node_id,
                kind=EdgeKind.READS_CONFIG,
                label=f"{class_name} reads config {config_key}",
                properties={"config_key": config_key},
            ))

        return result
