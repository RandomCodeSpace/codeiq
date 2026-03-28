"""Dockerfile detector for container infrastructure definitions."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text, find_line_number
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_FROM_RE = re.compile(r'^FROM\s+(\S+)(?:\s+AS\s+(\w+))?', re.MULTILINE | re.IGNORECASE)
_EXPOSE_RE = re.compile(r'^EXPOSE\s+(\d+)', re.MULTILINE)
_ENV_RE = re.compile(r'^ENV\s+(\w+)[=\s]', re.MULTILINE)
_LABEL_RE = re.compile(r'^LABEL\s+(\S+)=', re.MULTILINE)



class DockerfileDetector:
    """Detects infrastructure resources from Dockerfile definitions."""

    name: str = "dockerfile"
    supported_languages: tuple[str, ...] = ("dockerfile",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        stages: dict[str, str] = {}  # stage alias -> base image

        # Detect FROM instructions (base image dependencies)
        for m in _FROM_RE.finditer(text):
            image = m.group(1)
            alias = m.group(2)
            line = find_line_number(text, m.start())

            # Track stage aliases for multi-stage build references
            if alias:
                stages[alias] = image

            # Parse image name and tag
            props: dict[str, str] = {"image": image}
            if ":" in image and not image.startswith("$"):
                img_name, tag = image.rsplit(":", 1)
                props["image_name"] = img_name
                props["tag"] = tag
            else:
                props["image_name"] = image

            if alias:
                props["stage_alias"] = alias

            node_id = f"docker:{ctx.file_path}:from:{image}"
            label = f"FROM {image}" + (f" AS {alias}" if alias else "")

            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.INFRA_RESOURCE,
                label=label,
                fqn=image,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=line,
                ),
                properties=props,
            ))

            # Edge: this Dockerfile depends on the base image
            result.edges.append(GraphEdge(
                source=ctx.file_path,
                target=image,
                kind=EdgeKind.DEPENDS_ON,
                label=f"{ctx.file_path} depends on image {image}",
            ))

        # Detect EXPOSE instructions (exposed ports)
        for m in _EXPOSE_RE.finditer(text):
            port = m.group(1)
            line = find_line_number(text, m.start())

            result.nodes.append(GraphNode(
                id=f"docker:{ctx.file_path}:expose:{port}",
                kind=NodeKind.ENDPOINT,
                label=f"EXPOSE {port}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=line,
                ),
                properties={"port": port, "protocol": "tcp"},
            ))

        # Detect ENV instructions (configuration definitions)
        for m in _ENV_RE.finditer(text):
            key = m.group(1)
            line = find_line_number(text, m.start())

            result.nodes.append(GraphNode(
                id=f"docker:{ctx.file_path}:env:{key}",
                kind=NodeKind.CONFIG_DEFINITION,
                label=f"ENV {key}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=line,
                ),
                properties={"env_key": key},
            ))

        # Detect LABEL instructions
        for m in _LABEL_RE.finditer(text):
            label_key = m.group(1)
            line = find_line_number(text, m.start())

            result.nodes.append(GraphNode(
                id=f"docker:{ctx.file_path}:label:{label_key}",
                kind=NodeKind.CONFIG_DEFINITION,
                label=f"LABEL {label_key}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=line,
                ),
                properties={"label_key": label_key},
            ))

        return result
