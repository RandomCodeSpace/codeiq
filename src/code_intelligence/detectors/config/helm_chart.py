"""Helm chart detector for Kubernetes Helm chart patterns.

Detects Chart.yaml, values.yaml, and template references.
"""

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

# Template value references: {{ .Values.key }}
_VALUES_REF_RE = re.compile(
    r"\{\{\s*\.Values\.([a-zA-Z0-9_.]+)\s*\}\}", re.MULTILINE
)

# Include helper references: {{ include "helper" }}
_INCLUDE_RE = re.compile(
    r'\{\{-?\s*include\s+["\']([^"\']+)["\']', re.MULTILINE
)


def _get_yaml_data(ctx: DetectorContext) -> dict[str, Any] | None:
    """Extract YAML data from parsed_data."""
    if not ctx.parsed_data:
        return None

    ptype = ctx.parsed_data.get("type")
    if ptype == "yaml":
        data = ctx.parsed_data.get("data")
        if isinstance(data, dict):
            return data
    return None


class HelmChartDetector:
    """Detects Helm chart patterns in Chart.yaml, values.yaml, and templates."""

    name: str = "helm_chart"
    supported_languages: tuple[str, ...] = ("yaml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        fp = ctx.file_path

        if fp.endswith("Chart.yaml"):
            self._detect_chart_yaml(ctx, result)
        elif fp.endswith("values.yaml") and ("charts/" in fp or "helm/" in fp):
            self._detect_values_yaml(ctx, result)
        elif "/templates/" in fp and fp.endswith(".yaml"):
            self._detect_template(ctx, result)
        else:
            return result

        return result

    def _detect_chart_yaml(
        self, ctx: DetectorContext, result: DetectorResult
    ) -> None:
        """Parse Chart.yaml and emit MODULE + DEPENDS_ON edges."""
        fp = ctx.file_path
        data = _get_yaml_data(ctx)
        if not data:
            return

        chart_name = data.get("name", "unknown")
        chart_version = data.get("version", "0.0.0")

        chart_node_id = f"helm:{fp}:chart:{chart_name}"
        result.nodes.append(GraphNode(
            id=chart_node_id,
            kind=NodeKind.MODULE,
            label=f"helm:{chart_name}",
            fqn=f"helm:{chart_name}:{chart_version}",
            module=ctx.module_name,
            location=SourceLocation(file_path=fp),
            properties={
                "chart_name": str(chart_name),
                "chart_version": str(chart_version),
                "type": "helm_chart",
            },
        ))

        # Process dependencies
        dependencies = data.get("dependencies")
        if isinstance(dependencies, list):
            for dep in dependencies:
                if not isinstance(dep, dict):
                    continue
                dep_name = dep.get("name", "")
                dep_version = dep.get("version", "")
                dep_repo = dep.get("repository", "")
                if not dep_name:
                    continue

                dep_node_id = f"helm:{fp}:dep:{dep_name}"
                result.nodes.append(GraphNode(
                    id=dep_node_id,
                    kind=NodeKind.MODULE,
                    label=f"helm-dep:{dep_name}",
                    fqn=f"helm:{dep_name}:{dep_version}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties={
                        "chart_name": str(dep_name),
                        "chart_version": str(dep_version),
                        "repository": str(dep_repo),
                        "type": "helm_dependency",
                    },
                ))

                result.edges.append(GraphEdge(
                    source=chart_node_id,
                    target=dep_node_id,
                    kind=EdgeKind.DEPENDS_ON,
                    label=f"{chart_name} depends on {dep_name}",
                    properties={"version": str(dep_version)},
                ))

    def _detect_values_yaml(
        self, ctx: DetectorContext, result: DetectorResult
    ) -> None:
        """Parse values.yaml and emit CONFIG_KEY nodes for top-level keys."""
        fp = ctx.file_path
        data = _get_yaml_data(ctx)
        if not data:
            return

        for key in sorted(data.keys()):
            result.nodes.append(GraphNode(
                id=f"helm:{fp}:value:{key}",
                kind=NodeKind.CONFIG_KEY,
                label=f"helm-value:{key}",
                module=ctx.module_name,
                location=SourceLocation(file_path=fp),
                properties={"helm_value": True, "key": str(key)},
            ))

    def _detect_template(
        self, ctx: DetectorContext, result: DetectorResult
    ) -> None:
        """Parse template files for .Values references and include directives."""
        fp = ctx.file_path
        text = decode_text(ctx)
        lines = text.split("\n")
        file_node_id = f"helm:{fp}:template"

        seen_values: set[str] = set()
        seen_includes: set[str] = set()

        for i, line in enumerate(lines):
            lineno = i + 1

            # Detect {{ .Values.key }}
            for m in _VALUES_REF_RE.finditer(line):
                key = m.group(1)
                if key not in seen_values:
                    seen_values.add(key)
                    result.edges.append(GraphEdge(
                        source=file_node_id,
                        target=f"helm:values:{key}",
                        kind=EdgeKind.READS_CONFIG,
                        label=f"reads .Values.{key}",
                        properties={"key": key, "line": lineno},
                    ))

            # Detect {{ include "helper" }}
            for m in _INCLUDE_RE.finditer(line):
                helper = m.group(1)
                if helper not in seen_includes:
                    seen_includes.add(helper)
                    result.edges.append(GraphEdge(
                        source=file_node_id,
                        target=f"helm:helper:{helper}",
                        kind=EdgeKind.IMPORTS,
                        label=f"includes {helper}",
                        properties={"helper": helper, "line": lineno},
                    ))
