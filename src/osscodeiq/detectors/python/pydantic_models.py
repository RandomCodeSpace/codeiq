"""Pydantic model detector."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import EdgeKind, GraphEdge, GraphNode, NodeKind, SourceLocation


class PydanticModelDetector:
    """Detects Pydantic model and settings definitions."""

    name: str = "python.pydantic_models"
    supported_languages: tuple[str, ...] = ("python",)

    _PYDANTIC_CLASS_RE = re.compile(
        r"^class\s+(\w+)\s*\(\s*(\w*(?:BaseModel|BaseSettings)\w*)\s*\)", re.MULTILINE
    )
    _FIELD_RE = re.compile(r"^\s+(\w+)\s*:\s*(\w[\w\[\], |]*)", re.MULTILINE)
    _VALIDATOR_RE = re.compile(
        r"@(?:validator|field_validator)\s*\(\s*[\"'](\w+)", re.MULTILINE
    )
    _CONFIG_CLASS_RE = re.compile(r"^\s+class\s+Config\s*:", re.MULTILINE)
    _CONFIG_ATTR_RE = re.compile(r"^\s{8}(\w+)\s*=\s*(.+)", re.MULTILINE)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        # Track known model names for inheritance edges
        known_models: dict[str, str] = {}

        for match in self._PYDANTIC_CLASS_RE.finditer(text):
            class_name = match.group(1)
            base_class = match.group(2)
            line = text[: match.start()].count("\n") + 1

            is_settings = "BaseSettings" in base_class

            # Determine class body boundaries
            class_start = match.start()
            next_class = re.search(r"\nclass\s+\w+", text[match.end() :])
            class_body = (
                text[class_start : match.end() + next_class.start()]
                if next_class
                else text[class_start:]
            )

            # Extract fields
            fields = []
            field_types: dict[str, str] = {}
            for fm in self._FIELD_RE.finditer(class_body):
                fname = fm.group(1)
                ftype = fm.group(2).strip()
                if fname not in ("class", "Config", "model_config"):
                    fields.append(fname)
                    field_types[fname] = ftype

            # Extract validators
            validators = [
                vm.group(1) for vm in self._VALIDATOR_RE.finditer(class_body)
            ]

            # Extract Config class properties
            config_props: dict[str, str] = {}
            config_match = self._CONFIG_CLASS_RE.search(class_body)
            if config_match:
                config_block_start = config_match.end()
                # Find next dedented line or end
                config_block_end = len(class_body)
                for cm in re.finditer(r"\n\S", class_body[config_block_start:]):
                    config_block_end = config_block_start + cm.start()
                    break
                config_block = class_body[config_block_start:config_block_end]
                for attr_match in self._CONFIG_ATTR_RE.finditer(config_block):
                    config_props[attr_match.group(1)] = attr_match.group(2).strip()

            node_kind = NodeKind.CONFIG_DEFINITION if is_settings else NodeKind.ENTITY
            node_id = f"pydantic:{ctx.file_path}:model:{class_name}"

            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=node_kind,
                    label=class_name,
                    fqn=f"{ctx.file_path}::{class_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    annotations=validators,
                    properties={
                        "fields": fields,
                        "field_types": field_types,
                        "framework": "pydantic",
                        "base_class": base_class,
                        **({"config": config_props} if config_props else {}),
                    },
                )
            )

            known_models[class_name] = node_id

            # Check for inheritance from a known model
            if base_class in known_models:
                result.edges.append(
                    GraphEdge(
                        source=node_id,
                        target=known_models[base_class],
                        kind=EdgeKind.EXTENDS,
                        label=f"{class_name} extends {base_class}",
                    )
                )

        return result
