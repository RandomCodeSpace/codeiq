"""Detector for OpenAPI 3.x and Swagger 2.0 specification files."""

from __future__ import annotations

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)


class OpenApiDetector:
    """Detects API endpoints and schemas from OpenAPI/Swagger specifications."""

    name: str = "openapi"
    supported_languages: tuple[str, ...] = ("json", "yaml")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        data = ctx.parsed_data
        if not isinstance(data, dict) or not isinstance(data.get("data"), dict):
            return result

        spec = data["data"]

        # Only trigger if this is an OpenAPI or Swagger spec
        if "openapi" not in spec and "swagger" not in spec:
            return result

        filepath = ctx.file_path
        config_id = f"api:{filepath}"

        # Extract info metadata
        info = spec.get("info") if isinstance(spec.get("info"), dict) else {}
        api_title = info.get("title", filepath) if isinstance(info.get("title"), str) else filepath
        api_version = info.get("version", "") if isinstance(info.get("version"), str) else ""
        spec_version = spec.get("openapi") or spec.get("swagger") or ""

        # CONFIG_FILE node for the spec
        result.nodes.append(GraphNode(
            id=config_id,
            kind=NodeKind.CONFIG_FILE,
            label=api_title,
            fqn=filepath,
            module=ctx.module_name,
            location=SourceLocation(file_path=filepath),
            properties={
                "config_type": "openapi",
                "api_title": api_title,
                "api_version": api_version,
                "spec_version": str(spec_version),
            },
        ))

        # ENDPOINT nodes for each path + method combination
        paths = spec.get("paths")
        if isinstance(paths, dict):
            for path, path_item in paths.items():
                if not isinstance(path, str) or not isinstance(path_item, dict):
                    continue
                for method, operation in path_item.items():
                    if not isinstance(method, str):
                        continue
                    # Skip non-HTTP-method keys (e.g. "parameters", "summary")
                    if method.lower() not in (
                        "get", "post", "put", "patch", "delete",
                        "head", "options", "trace",
                    ):
                        continue
                    method_upper = method.upper()
                    endpoint_id = f"api:{filepath}:{method.lower()}:{path}"
                    props: dict[str, object] = {
                        "http_method": method_upper,
                        "path": path,
                    }
                    if isinstance(operation, dict):
                        op_id = operation.get("operationId")
                        if isinstance(op_id, str):
                            props["operation_id"] = op_id
                        summary = operation.get("summary")
                        if isinstance(summary, str):
                            props["summary"] = summary

                    result.nodes.append(GraphNode(
                        id=endpoint_id,
                        kind=NodeKind.ENDPOINT,
                        label=f"{method_upper} {path}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=filepath),
                        properties=props,
                    ))
                    result.edges.append(GraphEdge(
                        source=config_id,
                        target=endpoint_id,
                        kind=EdgeKind.CONTAINS,
                        label=f"{api_title} contains {method_upper} {path}",
                    ))

        # ENTITY nodes for schemas — OpenAPI 3.x and Swagger 2.0
        schemas = _extract_schemas(spec)
        for schema_name, schema_def in schemas.items():
            if not isinstance(schema_name, str):
                continue
            schema_id = f"api:{filepath}:schema:{schema_name}"
            schema_props: dict[str, object] = {"schema_name": schema_name}
            if isinstance(schema_def, dict):
                schema_type = schema_def.get("type")
                if isinstance(schema_type, str):
                    schema_props["schema_type"] = schema_type

            result.nodes.append(GraphNode(
                id=schema_id,
                kind=NodeKind.ENTITY,
                label=schema_name,
                module=ctx.module_name,
                location=SourceLocation(file_path=filepath),
                properties=schema_props,
            ))
            result.edges.append(GraphEdge(
                source=config_id,
                target=schema_id,
                kind=EdgeKind.CONTAINS,
                label=f"{api_title} defines schema {schema_name}",
            ))

            # DEPENDS_ON edges for $ref references within this schema
            if isinstance(schema_def, dict):
                refs = _collect_refs(schema_def)
                for ref in refs:
                    ref_name = _ref_to_schema_name(ref)
                    if ref_name and ref_name != schema_name and ref_name in schemas:
                        result.edges.append(GraphEdge(
                            source=schema_id,
                            target=f"api:{filepath}:schema:{ref_name}",
                            kind=EdgeKind.DEPENDS_ON,
                            label=f"{schema_name} references {ref_name}",
                        ))

        return result


def _extract_schemas(spec: dict) -> dict:
    """Extract schema definitions from both OpenAPI 3.x and Swagger 2.0."""
    # OpenAPI 3.x: components.schemas
    components = spec.get("components")
    if isinstance(components, dict):
        schemas = components.get("schemas")
        if isinstance(schemas, dict):
            return schemas

    # Swagger 2.0: definitions
    definitions = spec.get("definitions")
    if isinstance(definitions, dict):
        return definitions

    return {}


def _collect_refs(obj: dict | list, _seen: set | None = None) -> list[str]:
    """Recursively collect all $ref values from a schema definition."""
    if _seen is None:
        _seen = set()
    refs: list[str] = []
    obj_id = id(obj)
    if obj_id in _seen:
        return refs
    _seen.add(obj_id)

    if isinstance(obj, dict):
        ref = obj.get("$ref")
        if isinstance(ref, str):
            refs.append(ref)
        for value in obj.values():
            if isinstance(value, (dict, list)):
                refs.extend(_collect_refs(value, _seen))
    elif isinstance(obj, list):
        for item in obj:
            if isinstance(item, (dict, list)):
                refs.extend(_collect_refs(item, _seen))
    return refs


def _ref_to_schema_name(ref: str) -> str | None:
    """Extract a schema name from a $ref string like '#/components/schemas/User'."""
    if not ref.startswith("#/"):
        return None
    parts = ref.split("/")
    if len(parts) >= 2:
        return parts[-1]
    return None
