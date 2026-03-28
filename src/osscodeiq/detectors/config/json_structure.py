"""Generic JSON structure detector for all .json files."""

from __future__ import annotations

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)


class JsonStructureDetector:
    """Detects JSON file structures: top-level keys and file identity."""

    name: str = "json_structure"
    supported_languages: tuple[str, ...] = ("json",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        file_id = f"json:{ctx.file_path}"

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
            properties={"format": "json"},
        ))

        # Extract data from parsed_data
        data = None
        if isinstance(ctx.parsed_data, dict):
            data = ctx.parsed_data.get("data")

        if data is None:
            return result

        # Only extract top-level keys from dicts
        if isinstance(data, dict):
            for key in data:
                key_str = str(key)
                key_id = f"json:{ctx.file_path}:{key_str}"

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
