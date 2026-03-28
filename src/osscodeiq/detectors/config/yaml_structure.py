"""Generic YAML structure detector for all .yaml/.yml files."""

from __future__ import annotations

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)


class YamlStructureDetector:
    """Detects YAML file structures: top-level keys and file identity."""

    name: str = "yaml_structure"
    supported_languages: tuple[str, ...] = ("yaml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        file_id = f"yaml:{ctx.file_path}"

        # Create CONFIG_FILE node for the file itself
        result.nodes.append(GraphNode(
            id=file_id,
            kind=NodeKind.CONFIG_FILE,
            label=ctx.file_path,
            fqn=ctx.file_path,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=ctx.file_path,
                line_start=1,
            ),
            properties={"format": "yaml"},
        ))

        if not isinstance(ctx.parsed_data, dict):
            return result

        doc_type = ctx.parsed_data.get("type", "")

        # Collect all top-level keys from documents
        top_level_keys: set[str] = set()

        if doc_type == "yaml_multi":
            # Multi-document YAML: iterate over documents list
            documents = ctx.parsed_data.get("documents", [])
            for doc in documents:
                if isinstance(doc, dict):
                    top_level_keys.update(str(k) for k in doc)
        elif doc_type == "yaml":
            # Single-document YAML
            data = ctx.parsed_data.get("data")
            if isinstance(data, dict):
                top_level_keys.update(str(k) for k in data)

        # Create CONFIG_KEY nodes for top-level keys
        for key_str in sorted(top_level_keys):
            key_id = f"yaml:{ctx.file_path}:{key_str}"

            result.nodes.append(GraphNode(
                id=key_id,
                kind=NodeKind.CONFIG_KEY,
                label=key_str,
                fqn=f"{ctx.file_path}:{key_str}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                ),
            ))

            result.edges.append(GraphEdge(
                source=file_id,
                target=key_id,
                kind=EdgeKind.CONTAINS,
                label=f"{ctx.file_path} contains {key_str}",
            ))

        return result
