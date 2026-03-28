"""Mongoose ODM detector for TypeScript/JavaScript."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import EdgeKind, GraphEdge, GraphNode, NodeKind, SourceLocation


class MongooseORMDetector:
    """Detects Mongoose ODM usage patterns in TypeScript/JavaScript files."""

    name: str = "mongoose_orm"
    supported_languages: tuple[str, ...] = ("typescript", "javascript")

    # mongoose.model('Name', schema)
    _MODEL_RE = re.compile(r"mongoose\.model\s*\(\s*['\"](\w+)['\"]")
    # new Schema({ or new mongoose.Schema({
    _SCHEMA_RE = re.compile(r"(?:const|let|var)\s+(\w+)\s*=\s*new\s+(?:mongoose\.)?Schema\s*\(")
    # mongoose.connect(
    _CONNECT_RE = re.compile(r"mongoose\.connect\s*\(")
    # Model query operations
    _QUERY_RE = re.compile(
        r"(\w+)\.(find|findOne|findById|findOneAndUpdate|findOneAndDelete"
        r"|create|insertMany|updateOne|updateMany|deleteOne|deleteMany"
        r"|countDocuments|aggregate)\s*\("
    )
    # schema.virtual('name')
    _VIRTUAL_RE = re.compile(r"(\w+)\.virtual\s*\(\s*['\"](\w+)['\"]")
    # schema.pre('save', ...) / schema.post('save', ...)
    _HOOK_RE = re.compile(r"(\w+)\.(pre|post)\s*\(\s*['\"](\w+)['\"]")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        seen_models: dict[str, str] = {}
        schema_vars: set[str] = set()

        # Detect mongoose.connect -> DATABASE_CONNECTION
        for match in self._CONNECT_RE.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"mongoose:{ctx.file_path}:connection:{line}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.DATABASE_CONNECTION,
                    label="mongoose.connect",
                    fqn=f"{ctx.file_path}::mongoose.connect",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={"framework": "mongoose"},
                )
            )

        # Detect new Schema({ ... }) -> ENTITY for schema definition
        for match in self._SCHEMA_RE.finditer(text):
            var_name = match.group(1)
            schema_vars.add(var_name)
            line = text[: match.start()].count("\n") + 1
            node_id = f"mongoose:{ctx.file_path}:schema:{var_name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.ENTITY,
                    label=var_name,
                    fqn=f"{ctx.file_path}::{var_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={"framework": "mongoose", "definition": "schema"},
                )
            )

        # Detect mongoose.model('Name', schema) -> ENTITY
        for match in self._MODEL_RE.finditer(text):
            model_name = match.group(1)
            line = text[: match.start()].count("\n") + 1
            model_id = f"mongoose:{ctx.file_path}:model:{model_name}"
            seen_models[model_name] = model_id
            result.nodes.append(
                GraphNode(
                    id=model_id,
                    kind=NodeKind.ENTITY,
                    label=model_name,
                    fqn=f"{ctx.file_path}::{model_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={"framework": "mongoose", "definition": "model"},
                )
            )

        # Detect schema.virtual('name') -> property on schema
        virtuals: list[str] = []
        for match in self._VIRTUAL_RE.finditer(text):
            var_name = match.group(1)
            virtual_name = match.group(2)
            if var_name in schema_vars:
                virtuals.append(virtual_name)

        # Attach virtuals to schema nodes
        if virtuals:
            for node in result.nodes:
                if node.properties.get("definition") == "schema":
                    node.properties["virtuals"] = virtuals

        # Detect schema.pre/post hooks -> EVENT nodes
        for match in self._HOOK_RE.finditer(text):
            var_name = match.group(1)
            hook_type = match.group(2)
            event_name = match.group(3)
            if var_name in schema_vars:
                line = text[: match.start()].count("\n") + 1
                event_id = f"mongoose:{ctx.file_path}:hook:{hook_type}:{event_name}:{line}"
                result.nodes.append(
                    GraphNode(
                        id=event_id,
                        kind=NodeKind.EVENT,
                        label=f"{hook_type}:{event_name}",
                        fqn=f"{ctx.file_path}::{hook_type}:{event_name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=line),
                        properties={
                            "framework": "mongoose",
                            "hook_type": hook_type,
                            "event": event_name,
                        },
                    )
                )

        # Detect query operations -> QUERIES edges
        for match in self._QUERY_RE.finditer(text):
            model_name = match.group(1)
            operation = match.group(2)
            line = text[: match.start()].count("\n") + 1

            target_id = seen_models.get(
                model_name, f"mongoose:{ctx.file_path}:model:{model_name}"
            )
            result.edges.append(
                GraphEdge(
                    source=ctx.file_path,
                    target=target_id,
                    kind=EdgeKind.QUERIES,
                    label=f"{model_name}.{operation}",
                    properties={"operation": operation, "line": line},
                )
            )

        return result
