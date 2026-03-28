"""Sequelize ORM detector for TypeScript/JavaScript."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import EdgeKind, GraphEdge, GraphNode, NodeKind, SourceLocation


class SequelizeORMDetector:
    """Detects Sequelize ORM usage patterns in TypeScript/JavaScript files."""

    name: str = "sequelize_orm"
    supported_languages: tuple[str, ...] = ("typescript", "javascript")

    # sequelize.define('ModelName', { ... })
    _DEFINE_RE = re.compile(r"sequelize\.define\s*\(\s*['\"](\w+)['\"]")
    # class User extends Model { ... }
    _EXTENDS_MODEL_RE = re.compile(r"class\s+(\w+)\s+extends\s+Model\s*\{")
    # Model.init({ ... }, { sequelize })
    _MODEL_INIT_RE = re.compile(r"(\w+)\.init\s*\(\s*\{")
    # new Sequelize( or new Sequelize.Sequelize(
    _CONNECTION_RE = re.compile(r"new\s+Sequelize(?:\.Sequelize)?\s*\(")
    # Model.belongsTo(, Model.hasMany(, etc.
    _ASSOCIATION_RE = re.compile(
        r"(\w+)\.(belongsTo|hasMany|hasOne|belongsToMany)\s*\(\s*(\w+)"
    )
    # Model.findAll(, Model.findOne(, Model.create(, etc.
    _QUERY_RE = re.compile(
        r"(\w+)\.(findAll|findOne|findByPk|findOrCreate|create|bulkCreate|update|destroy|count|max|min|sum)\s*\("
    )

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        seen_models: dict[str, str] = {}

        # Detect Sequelize connection -> DATABASE_CONNECTION
        for match in self._CONNECTION_RE.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"sequelize:{ctx.file_path}:connection:{line}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.DATABASE_CONNECTION,
                    label="Sequelize",
                    fqn=f"{ctx.file_path}::Sequelize",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={"framework": "sequelize"},
                )
            )

        # Detect sequelize.define('ModelName', { ... }) -> ENTITY
        for match in self._DEFINE_RE.finditer(text):
            model_name = match.group(1)
            line = text[: match.start()].count("\n") + 1
            model_id = f"sequelize:{ctx.file_path}:model:{model_name}"
            seen_models[model_name] = model_id
            result.nodes.append(
                GraphNode(
                    id=model_id,
                    kind=NodeKind.ENTITY,
                    label=model_name,
                    fqn=f"{ctx.file_path}::{model_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={"framework": "sequelize", "definition": "define"},
                )
            )

        # Detect class X extends Model -> ENTITY
        for match in self._EXTENDS_MODEL_RE.finditer(text):
            class_name = match.group(1)
            line = text[: match.start()].count("\n") + 1
            if class_name not in seen_models:
                model_id = f"sequelize:{ctx.file_path}:model:{class_name}"
                seen_models[class_name] = model_id
                result.nodes.append(
                    GraphNode(
                        id=model_id,
                        kind=NodeKind.ENTITY,
                        label=class_name,
                        fqn=f"{ctx.file_path}::{class_name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=line),
                        properties={"framework": "sequelize", "definition": "class"},
                    )
                )

        # Detect associations -> DEPENDS_ON edges
        for match in self._ASSOCIATION_RE.finditer(text):
            source_model = match.group(1)
            assoc_type = match.group(2)
            target_model = match.group(3)
            line = text[: match.start()].count("\n") + 1

            source_id = seen_models.get(
                source_model, f"sequelize:{ctx.file_path}:model:{source_model}"
            )
            target_id = seen_models.get(
                target_model, f"sequelize:{ctx.file_path}:model:{target_model}"
            )
            result.edges.append(
                GraphEdge(
                    source=source_id,
                    target=target_id,
                    kind=EdgeKind.DEPENDS_ON,
                    label=assoc_type,
                    properties={"association": assoc_type, "line": line},
                )
            )

        # Detect query operations -> QUERIES edges
        for match in self._QUERY_RE.finditer(text):
            model_name = match.group(1)
            operation = match.group(2)
            line = text[: match.start()].count("\n") + 1

            target_id = seen_models.get(
                model_name, f"sequelize:{ctx.file_path}:model:{model_name}"
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
