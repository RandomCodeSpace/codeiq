"""Prisma ORM detector for TypeScript/JavaScript."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import EdgeKind, GraphEdge, GraphNode, NodeKind, SourceLocation


class PrismaORMDetector:
    """Detects Prisma ORM usage patterns in TypeScript/JavaScript files."""

    name: str = "prisma_orm"
    supported_languages: tuple[str, ...] = ("typescript", "javascript")

    _PRISMA_OP_RE = re.compile(
        r"prisma\.(\w+)\.(findMany|findFirst|findUnique|create|update|delete|upsert|count|aggregate|groupBy)\s*\("
    )
    _PRISMA_CLIENT_RE = re.compile(r"new\s+PrismaClient\s*\(|PrismaClient\s*\(")
    _PRISMA_IMPORT_RE = re.compile(r"""(?:import|require)\s*\(?[^)]*['"]@prisma/client['"]""")
    _PRISMA_TRANSACTION_RE = re.compile(r"prisma\.\$transaction\s*\(")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        # Detect PrismaClient instantiation -> DATABASE_CONNECTION
        for match in self._PRISMA_CLIENT_RE.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"prisma:{ctx.file_path}:client:{line}"
            props: dict[str, object] = {"framework": "prisma"}
            # Check for $transaction usage
            if self._PRISMA_TRANSACTION_RE.search(text):
                props["transaction"] = True
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.DATABASE_CONNECTION,
                    label="PrismaClient",
                    fqn=f"{ctx.file_path}::PrismaClient",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties=props,
                )
            )

        # Detect @prisma/client imports -> IMPORTS edge
        for match in self._PRISMA_IMPORT_RE.finditer(text):
            line = text[: match.start()].count("\n") + 1
            result.edges.append(
                GraphEdge(
                    source=ctx.file_path,
                    target="@prisma/client",
                    kind=EdgeKind.IMPORTS,
                    label="import @prisma/client",
                    properties={"line": line},
                )
            )

        # Detect prisma model operations -> ENTITY nodes + QUERIES edges
        seen_models: dict[str, str] = {}
        for match in self._PRISMA_OP_RE.finditer(text):
            model_name = match.group(1)
            operation = match.group(2)
            line = text[: match.start()].count("\n") + 1

            # Create ENTITY node for each unique model
            if model_name not in seen_models:
                model_id = f"prisma:{ctx.file_path}:model:{model_name}"
                seen_models[model_name] = model_id
                result.nodes.append(
                    GraphNode(
                        id=model_id,
                        kind=NodeKind.ENTITY,
                        label=model_name,
                        fqn=f"{ctx.file_path}::{model_name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=line),
                        properties={"framework": "prisma"},
                    )
                )

            # QUERIES edge from file to model
            result.edges.append(
                GraphEdge(
                    source=ctx.file_path,
                    target=seen_models[model_name],
                    kind=EdgeKind.QUERIES,
                    label=f"{model_name}.{operation}",
                    properties={"operation": operation, "line": line},
                )
            )

        return result
