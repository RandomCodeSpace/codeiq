"""Properties file detector for Java .properties and Spring configuration files."""

from __future__ import annotations

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_DB_KEYWORDS = {"url", "jdbc", "datasource"}
_MAX_KEYS = 200


class PropertiesDetector:
    """Detects property keys, Spring config markers, and database connections from .properties files."""

    name: str = "properties"
    supported_languages: tuple[str, ...] = ("properties",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        # parsed_data expected: {"type": "properties", "file": path, "data": {"key": "value", ...}}
        if not isinstance(ctx.parsed_data, dict):
            return result

        if ctx.parsed_data.get("type") != "properties":
            return result

        data = ctx.parsed_data.get("data")
        if not isinstance(data, dict):
            return result

        filepath = ctx.file_path
        file_id = f"props:{filepath}"

        # CONFIG_FILE node
        result.nodes.append(GraphNode(
            id=file_id,
            kind=NodeKind.CONFIG_FILE,
            label=filepath,
            fqn=filepath,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=filepath,
                line_start=1,
            ),
            properties={"format": "properties"},
        ))

        # Process keys (limit to avoid node explosion)
        keys = list(data.items())[:_MAX_KEYS]

        for key, value in keys:
            if not isinstance(key, str):
                continue

            key_lower = key.lower()
            key_id = f"props:{filepath}:{key}"

            # Detect DB connection properties
            is_db = any(kw in key_lower for kw in _DB_KEYWORDS)

            if is_db:
                props: dict[str, object] = {"key": key}
                if isinstance(value, str):
                    props["value"] = value

                result.nodes.append(GraphNode(
                    id=key_id,
                    kind=NodeKind.DATABASE_CONNECTION,
                    label=key,
                    fqn=f"{filepath}:{key}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath),
                    properties=props,
                ))
            else:
                props = {"key": key}
                if isinstance(value, str):
                    props["value"] = value

                # Detect Spring config
                if key.startswith("spring."):
                    props["spring_config"] = True

                result.nodes.append(GraphNode(
                    id=key_id,
                    kind=NodeKind.CONFIG_KEY,
                    label=key,
                    fqn=f"{filepath}:{key}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath),
                    properties=props,
                ))

            result.edges.append(GraphEdge(
                source=file_id,
                target=key_id,
                kind=EdgeKind.CONTAINS,
                label=f"{filepath} contains {key}",
            ))

        return result
