"""Generic TOML structure detector for all .toml files."""

from __future__ import annotations

import sys

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

if sys.version_info >= (3, 11):
    import tomllib
else:
    try:
        import tomli as tomllib  # type: ignore[no-redef]
    except ImportError:
        tomllib = None  # type: ignore[assignment]


class TomlStructureDetector:
    """Detects TOML file structures: sections, top-level keys, and file identity."""

    name: str = "toml_structure"
    supported_languages: tuple[str, ...] = ("toml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        file_id = f"toml:{ctx.file_path}"

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
            properties={"format": "toml"},
        ))

        if tomllib is None:
            return result

        # Parse TOML from raw content
        try:
            data = tomllib.loads(decode_text(ctx))
        except Exception:
            return result

        if not isinstance(data, dict):
            return result

        for key, value in data.items():
            key_str = str(key)

            # Tables (sections) are dicts at top level
            is_section = isinstance(value, dict)
            key_id = f"toml:{ctx.file_path}:{key_str}"

            props: dict[str, object] = {}
            if is_section:
                props["section"] = True

            result.nodes.append(GraphNode(
                id=key_id,
                kind=NodeKind.CONFIG_KEY,
                label=key_str,
                fqn=f"{ctx.file_path}:{key_str}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                ),
                properties=props,
            ))

            result.edges.append(GraphEdge(
                source=file_id,
                target=key_id,
                kind=EdgeKind.CONTAINS,
                label=f"{ctx.file_path} contains {key_str}",
            ))

        return result
