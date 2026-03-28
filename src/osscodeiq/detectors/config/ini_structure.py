"""Generic INI/CFG/CONF structure detector for all .ini/.cfg/.conf files."""

from __future__ import annotations

import configparser

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)


class IniStructureDetector:
    """Detects INI file structures: sections, keys, and file identity."""

    name: str = "ini_structure"
    supported_languages: tuple[str, ...] = ("ini",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        file_id = f"ini:{ctx.file_path}"

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
            properties={"format": "ini"},
        ))

        # Parse INI from raw content
        try:
            text = decode_text(ctx)
            parser = configparser.ConfigParser()
            parser.read_string(text)
        except Exception:
            return result

        for section in parser.sections():
            section_id = f"ini:{ctx.file_path}:{section}"

            # Create CONFIG_KEY node for the section
            result.nodes.append(GraphNode(
                id=section_id,
                kind=NodeKind.CONFIG_KEY,
                label=section,
                fqn=f"{ctx.file_path}:{section}",
                module=ctx.module_name,
                location=SourceLocation(
                    file_path=ctx.file_path,
                ),
                properties={"section": True},
            ))

            result.edges.append(GraphEdge(
                source=file_id,
                target=section_id,
                kind=EdgeKind.CONTAINS,
                label=f"{ctx.file_path} contains [{section}]",
            ))

            # Create CONFIG_KEY nodes for keys within the section
            for key in parser.options(section):
                # Skip keys inherited from DEFAULT
                if section != "DEFAULT" and key in parser.defaults() and parser.get(section, key) == parser.defaults()[key]:
                    continue

                key_id = f"ini:{ctx.file_path}:{section}:{key}"

                result.nodes.append(GraphNode(
                    id=key_id,
                    kind=NodeKind.CONFIG_KEY,
                    label=key,
                    fqn=f"{ctx.file_path}:{section}:{key}",
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=ctx.file_path,
                    ),
                    properties={"section": section},
                ))

                result.edges.append(GraphEdge(
                    source=section_id,
                    target=key_id,
                    kind=EdgeKind.CONTAINS,
                    label=f"[{section}] contains {key}",
                ))

        return result
