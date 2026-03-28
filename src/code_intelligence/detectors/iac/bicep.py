"""Bicep IaC detector for Azure infrastructure resource definitions."""

from __future__ import annotations

import re
from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

# resource <symbolName> '<type>@<apiVersion>'
_RESOURCE_RE = re.compile(r"resource\s+(\w+)\s+'([^']+)'")

# param <name> <type>
_PARAM_RE = re.compile(r"param\s+(\w+)\s+(\w+)")

# module <name> '<path>'
_MODULE_RE = re.compile(r"module\s+(\w+)\s+'([^']+)'")


def _parse_resource_type(type_str: str) -> dict[str, str]:
    """Parse 'Microsoft.DocumentDB/databaseAccounts@2023-04-15' into parts."""
    props: dict[str, str] = {}
    if "@" in type_str:
        azure_type, api_version = type_str.rsplit("@", 1)
        props["azure_type"] = azure_type
        props["api_version"] = api_version
    else:
        props["azure_type"] = type_str
    return props


class BicepDetector:
    """Detects Azure infrastructure resources from Bicep files."""

    name: str = "bicep"
    supported_languages: tuple[str, ...] = ("bicep",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        # Detect resource declarations
        for i, line in enumerate(lines):
            m = _RESOURCE_RE.search(line)
            if not m:
                continue

            resource_name = m.group(1)
            type_str = m.group(2)
            props = _parse_resource_type(type_str)
            azure_type = props.get("azure_type", "")

            # Use AZURE_RESOURCE for Microsoft.* types, INFRA_RESOURCE otherwise
            if azure_type.startswith("Microsoft."):
                kind = NodeKind.AZURE_RESOURCE
            else:
                kind = NodeKind.INFRA_RESOURCE

            node_id = f"{ctx.file_path}:resource:{resource_name}"
            label = f"{resource_name} ({azure_type})"

            result.nodes.append(GraphNode(
                id=node_id,
                kind=kind,
                label=label,
                fqn=azure_type,
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties=props,
            ))

        # Detect param declarations
        for i, line in enumerate(lines):
            m = _PARAM_RE.search(line)
            if not m:
                continue

            param_name = m.group(1)
            param_type = m.group(2)

            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:param:{param_name}",
                kind=NodeKind.CONFIG_KEY,
                label=f"param {param_name}: {param_type}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties={"param_type": param_type},
            ))

        # Detect module references
        for i, line in enumerate(lines):
            m = _MODULE_RE.search(line)
            if not m:
                continue

            module_name = m.group(1)
            module_path = m.group(2)

            result.nodes.append(GraphNode(
                id=f"{ctx.file_path}:module:{module_name}",
                kind=NodeKind.INFRA_RESOURCE,
                label=f"module {module_name} ({module_path})",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=i + 1,
                ),
                properties={"module_path": module_path},
            ))

            # Edge: current file depends on the referenced module
            result.edges.append(GraphEdge(
                source=ctx.file_path,
                target=module_path,
                kind=EdgeKind.DEPENDS_ON,
                label=f"{ctx.file_path} depends on module {module_path}",
                properties={"module_name": module_name},
            ))

        return result
