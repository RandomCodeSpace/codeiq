"""Detector for tsconfig.json files (TypeScript compiler configuration)."""

from __future__ import annotations

import os
import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_TSCONFIG_RE = re.compile(r'^tsconfig(?:\..+)?\.json$')

_TRACKED_COMPILER_OPTIONS = ("strict", "target", "module", "outDir", "rootDir")


class TsconfigJsonDetector:
    """Detects configuration structure from tsconfig.json files."""

    name: str = "tsconfig_json"
    supported_languages: tuple[str, ...] = ("json",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        # Only trigger for tsconfig.json or tsconfig.*.json
        basename = os.path.basename(ctx.file_path)
        if not _TSCONFIG_RE.match(basename):
            return result

        data = ctx.parsed_data
        if not isinstance(data, dict) or not isinstance(data.get("data"), dict):
            return result

        cfg = data["data"]
        filepath = ctx.file_path
        config_id = f"tsconfig:{filepath}"

        # CONFIG_FILE node
        result.nodes.append(GraphNode(
            id=config_id,
            kind=NodeKind.CONFIG_FILE,
            label=basename,
            fqn=filepath,
            module=ctx.module_name,
            location=SourceLocation(file_path=filepath),
            properties={"config_type": "tsconfig"},
        ))

        # DEPENDS_ON edge for "extends"
        extends = cfg.get("extends")
        if isinstance(extends, str) and extends:
            result.edges.append(GraphEdge(
                source=config_id,
                target=extends,
                kind=EdgeKind.DEPENDS_ON,
                label=f"{basename} extends {extends}",
                properties={"relation": "extends"},
            ))

        # DEPENDS_ON edges for "references"
        references = cfg.get("references")
        if isinstance(references, list):
            for ref in references:
                if not isinstance(ref, dict):
                    continue
                ref_path = ref.get("path")
                if isinstance(ref_path, str) and ref_path:
                    result.edges.append(GraphEdge(
                        source=config_id,
                        target=ref_path,
                        kind=EdgeKind.DEPENDS_ON,
                        label=f"{basename} references {ref_path}",
                        properties={"relation": "reference"},
                    ))

        # CONFIG_KEY nodes for key compiler options
        compiler_options = cfg.get("compilerOptions")
        if isinstance(compiler_options, dict):
            for opt in _TRACKED_COMPILER_OPTIONS:
                if opt not in compiler_options:
                    continue
                value = compiler_options[opt]
                key_id = f"tsconfig:{filepath}:option:{opt}"
                result.nodes.append(GraphNode(
                    id=key_id,
                    kind=NodeKind.CONFIG_KEY,
                    label=f"compilerOptions.{opt}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath),
                    properties={"key": opt, "value": value},
                ))
                result.edges.append(GraphEdge(
                    source=config_id,
                    target=key_id,
                    kind=EdgeKind.CONTAINS,
                    label=f"{basename} defines {opt}",
                ))

        return result
