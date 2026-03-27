"""Regex-based Terraform/HCL detector for infrastructure resource definitions."""

from __future__ import annotations

import re
from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_RESOURCE_RE = re.compile(r'resource\s+"([^"]+)"\s+"([^"]+)"')
_DATA_RE = re.compile(r'data\s+"([^"]+)"\s+"([^"]+)"')
_MODULE_RE = re.compile(r'module\s+"([^"]+)"')
_VARIABLE_RE = re.compile(r'variable\s+"([^"]+)"')
_OUTPUT_RE = re.compile(r'output\s+"([^"]+)"')
_PROVIDER_RE = re.compile(r'provider\s+"([^"]+)"')
_SOURCE_RE = re.compile(r'source\s*=\s*"([^"]+)"')


def _find_line_number(text: str, pos: int) -> int:
    """Return the 1-based line number for a character offset."""
    return text[:pos].count("\n") + 1


def _extract_provider(resource_type: str) -> str | None:
    """Extract provider name from a resource type like 'azurerm_storage_account'."""
    parts = resource_type.split("_", 1)
    return parts[0] if len(parts) > 1 else None


def _find_source_in_block(text: str, block_start: int) -> str | None:
    """Find a source = "..." attribute within a block starting at block_start."""
    # Scan forward from block_start to find the opening brace and then source attr
    brace_pos = text.find("{", block_start)
    if brace_pos == -1:
        return None
    # Look for source within a reasonable range (next ~500 chars)
    block_snippet = text[brace_pos:brace_pos + 500]
    m = _SOURCE_RE.search(block_snippet)
    return m.group(1) if m else None


class TerraformDetector:
    """Detects Terraform/HCL infrastructure resources, modules, variables, and outputs."""

    name: str = "terraform"
    supported_languages: tuple[str, ...] = ("terraform",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        # Resource declarations
        for m in _RESOURCE_RE.finditer(text):
            resource_type = m.group(1)
            resource_name = m.group(2)
            provider = _extract_provider(resource_type)

            node_id = f"tf:resource:{resource_type}:{resource_name}"
            properties: dict[str, Any] = {"resource_type": resource_type}
            if provider:
                properties["provider"] = provider

            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.INFRA_RESOURCE,
                label=f"{resource_type}.{resource_name}",
                fqn=f"{resource_type}.{resource_name}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties=properties,
            ))

        # Data source declarations
        for m in _DATA_RE.finditer(text):
            data_type = m.group(1)
            data_name = m.group(2)
            provider = _extract_provider(data_type)

            node_id = f"tf:data:{data_type}:{data_name}"
            properties = {"resource_type": data_type, "data_source": True}
            if provider:
                properties["provider"] = provider

            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.INFRA_RESOURCE,
                label=f"data.{data_type}.{data_name}",
                fqn=f"data.{data_type}.{data_name}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties=properties,
            ))

        # Module declarations
        for m in _MODULE_RE.finditer(text):
            module_name = m.group(1)
            source = _find_source_in_block(text, m.start())

            node_id = f"tf:module:{module_name}"
            properties: dict[str, Any] = {}
            if source:
                properties["source"] = source

            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.MODULE,
                label=f"module.{module_name}",
                fqn=f"module.{module_name}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties=properties,
            ))

            # Edge: depends on source module
            if source:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=source,
                    kind=EdgeKind.DEPENDS_ON,
                    label=f"module.{module_name} depends on {source}",
                    properties={"module_source": source},
                ))

        # Variable declarations
        for m in _VARIABLE_RE.finditer(text):
            var_name = m.group(1)
            node_id = f"tf:var:{var_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.CONFIG_DEFINITION,
                label=f"var.{var_name}",
                fqn=f"var.{var_name}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties={"config_type": "variable"},
            ))

        # Output declarations
        for m in _OUTPUT_RE.finditer(text):
            output_name = m.group(1)
            node_id = f"tf:output:{output_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.CONFIG_DEFINITION,
                label=f"output.{output_name}",
                fqn=f"output.{output_name}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties={"config_type": "output"},
            ))

        # Provider declarations
        for m in _PROVIDER_RE.finditer(text):
            provider_name = m.group(1)
            node_id = f"tf:provider:{provider_name}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.INFRA_RESOURCE,
                label=f"provider.{provider_name}",
                fqn=f"provider.{provider_name}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                    line_start=_find_line_number(text, m.start()),
                ),
                properties={"resource_type": "provider", "provider": provider_name},
            ))

        return result
