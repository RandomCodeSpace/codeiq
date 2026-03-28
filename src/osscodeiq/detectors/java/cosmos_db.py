"""Azure Cosmos DB client usage detector for Java and TypeScript/JavaScript."""

from __future__ import annotations

import re
from typing import Any

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_CLASS_RE = re.compile(r"(?:public\s+)?class\s+(\w+)")
_DATABASE_RE = re.compile(r'\.(?:getDatabase|database)\s*\(\s*"([^"]+)"')
_CONTAINER_RE = re.compile(r'\.(?:getContainer|container)\s*\(\s*"([^"]+)"')


class CosmosDbDetector:
    """Detects Azure Cosmos DB client usage patterns."""

    name: str = "cosmos_db"
    supported_languages: tuple[str, ...] = ("java", "typescript", "javascript")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        # Fast bail
        if (
            "CosmosClient" not in text
            and "CosmosDatabase" not in text
            and "CosmosContainer" not in text
            and "@azure/cosmos" not in text
        ):
            return result

        lines = text.split("\n")

        # Find class name (for Java) or use file path as source
        class_name: str | None = None
        for line in lines:
            cm = _CLASS_RE.search(line)
            if cm:
                class_name = cm.group(1)
                break

        source_node_id = f"{ctx.file_path}:{class_name}" if class_name else ctx.file_path
        seen_databases: set[str] = set()
        seen_containers: set[str] = set()

        for i, line in enumerate(lines):
            # Detect database references
            for db_match in _DATABASE_RE.finditer(line):
                db_name = db_match.group(1)
                if db_name not in seen_databases:
                    seen_databases.add(db_name)
                    db_node_id = f"azure:cosmos:db:{db_name}"
                    result.nodes.append(GraphNode(
                        id=db_node_id,
                        kind=NodeKind.AZURE_RESOURCE,
                        label=f"cosmosdb:{db_name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                        properties={
                            "cosmos_type": "database",
                            "resource_name": db_name,
                        },
                    ))
                    result.edges.append(GraphEdge(
                        source=source_node_id,
                        target=db_node_id,
                        kind=EdgeKind.CONNECTS_TO,
                        label=f"{class_name or ctx.file_path} connects to cosmosdb:{db_name}",
                    ))

            # Detect container references
            for cont_match in _CONTAINER_RE.finditer(line):
                container_name = cont_match.group(1)
                if container_name not in seen_containers:
                    seen_containers.add(container_name)
                    container_node_id = f"azure:cosmos:container:{container_name}"
                    result.nodes.append(GraphNode(
                        id=container_node_id,
                        kind=NodeKind.AZURE_RESOURCE,
                        label=f"cosmosdb-container:{container_name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                        properties={
                            "cosmos_type": "container",
                            "resource_name": container_name,
                        },
                    ))
                    result.edges.append(GraphEdge(
                        source=source_node_id,
                        target=container_node_id,
                        kind=EdgeKind.CONNECTS_TO,
                        label=f"{class_name or ctx.file_path} connects to container:{container_name}",
                    ))

        return result
