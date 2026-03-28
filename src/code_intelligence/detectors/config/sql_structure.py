"""SQL structure detector for tables, views, indexes, procedures, and foreign keys."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_TABLE_RE = re.compile(
    r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:\w+\.)?(\w+)',
    re.IGNORECASE,
)
_VIEW_RE = re.compile(
    r'CREATE\s+(?:OR\s+REPLACE\s+)?VIEW\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:\w+\.)?(\w+)',
    re.IGNORECASE,
)
_INDEX_RE = re.compile(
    r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:\w+\.)?(\w+)',
    re.IGNORECASE,
)
_PROCEDURE_RE = re.compile(
    r'CREATE\s+(?:OR\s+REPLACE\s+)?PROCEDURE\s+(?:\w+\.)?(\w+)',
    re.IGNORECASE,
)
_FK_RE = re.compile(
    r'REFERENCES\s+(?:\w+\.)?(\w+)',
    re.IGNORECASE,
)


class SqlStructureDetector:
    """Detects SQL structures: tables, views, indexes, procedures, and foreign key relationships."""

    name: str = "sql_structure"
    supported_languages: tuple[str, ...] = ("sql",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        try:
            text = ctx.content.decode("utf-8", errors="replace")
        except Exception:
            return result

        filepath = ctx.file_path
        lines = text.split("\n")

        # Track current table for FK association
        current_table: str | None = None
        current_table_id: str | None = None

        for i, line in enumerate(lines):
            line_num = i + 1

            # Tables
            m = _TABLE_RE.search(line)
            if m:
                table_name = m.group(1)
                current_table = table_name
                current_table_id = f"sql:{filepath}:table:{table_name}"

                result.nodes.append(GraphNode(
                    id=current_table_id,
                    kind=NodeKind.ENTITY,
                    label=table_name,
                    fqn=table_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=line_num,
                    ),
                    properties={"entity_type": "table"},
                ))
                continue

            # Views
            m = _VIEW_RE.search(line)
            if m:
                view_name = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"sql:{filepath}:view:{view_name}",
                    kind=NodeKind.ENTITY,
                    label=view_name,
                    fqn=view_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=line_num,
                    ),
                    properties={"entity_type": "view"},
                ))
                current_table = None
                current_table_id = None
                continue

            # Indexes
            m = _INDEX_RE.search(line)
            if m:
                index_name = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"sql:{filepath}:index:{index_name}",
                    kind=NodeKind.CONFIG_DEFINITION,
                    label=index_name,
                    fqn=index_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=line_num,
                    ),
                    properties={"definition_type": "index"},
                ))
                continue

            # Procedures
            m = _PROCEDURE_RE.search(line)
            if m:
                proc_name = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"sql:{filepath}:procedure:{proc_name}",
                    kind=NodeKind.ENTITY,
                    label=proc_name,
                    fqn=proc_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=line_num,
                    ),
                    properties={"entity_type": "procedure"},
                ))
                current_table = None
                current_table_id = None
                continue

            # Foreign key references
            m = _FK_RE.search(line)
            if m and current_table_id:
                ref_table = m.group(1)
                ref_table_id = f"sql:{filepath}:table:{ref_table}"
                result.edges.append(GraphEdge(
                    source=current_table_id,
                    target=ref_table_id,
                    kind=EdgeKind.DEPENDS_ON,
                    label=f"{current_table} references {ref_table}",
                    properties={"relationship": "foreign_key"},
                ))

        return result
