"""JDBC/ODBC database connectivity detector for Java source files."""

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

# DriverManager.getConnection("jdbc:...")
_DRIVER_MANAGER_RE = re.compile(
    r'DriverManager\s*\.\s*getConnection\s*\(\s*"(jdbc:[^"]+)"'
)

# JdbcTemplate / NamedParameterJdbcTemplate / JdbcClient field or constructor usage
_JDBC_TEMPLATE_RE = re.compile(
    r"(?:private|protected|public|final|\s)+"
    r"(?:final\s+)?"
    r"(JdbcTemplate|NamedParameterJdbcTemplate|JdbcClient)"
    r"\s+\w+"
)

# DataSource bean definitions or annotations
_DATASOURCE_BEAN_RE = re.compile(
    r"(?:@Bean|DataSource)\s*(?:\(|\.)"
)

# spring.datasource.url property
_SPRING_DATASOURCE_RE = re.compile(
    r"spring\.datasource\.url\s*=\s*(jdbc:[^\s]+)"
)

# Generic JDBC URL pattern (captures db type and host info)
_JDBC_URL_RE = re.compile(
    r"jdbc:(mysql|postgresql|sqlserver|oracle|db2|h2|sqlite|mariadb)"
    r"(?::(?:thin:)?(?:@)?)?(?://([^/\"'\s;?]+))?"
)


def _parse_jdbc_url(url: str) -> dict[str, str]:
    """Extract db_type and host from a JDBC URL."""
    props: dict[str, str] = {"connection_url": url}
    m = _JDBC_URL_RE.search(url)
    if m:
        props["db_type"] = m.group(1)
        if m.group(2):
            props["host"] = m.group(2)
    return props


class JdbcDetector:
    """Detects Java database connectivity patterns (JDBC, JdbcTemplate, DataSource)."""

    name: str = "jdbc"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        # Fast-bail
        if (
            "JdbcTemplate" not in text
            and "DriverManager" not in text
            and "DataSource" not in text
            and "NamedParameterJdbcTemplate" not in text
            and "JdbcClient" not in text
        ):
            return result

        lines = text.split("\n")

        # Find class name
        class_name: str | None = None
        for line in lines:
            cm = _CLASS_RE.search(line)
            if cm:
                class_name = cm.group(1)
                break

        if not class_name:
            return result

        class_node_id = f"{ctx.file_path}:{class_name}"
        seen_dbs: set[str] = set()

        def _ensure_db_node(
            db_id: str,
            label: str,
            line_num: int | None,
            properties: dict[str, Any],
        ) -> str:
            if db_id not in seen_dbs:
                seen_dbs.add(db_id)
                result.nodes.append(GraphNode(
                    id=db_id,
                    kind=NodeKind.DATABASE_CONNECTION,
                    label=label,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=ctx.file_path,
                        line_start=line_num,
                    ) if line_num else None,
                    properties=properties,
                ))
            return db_id

        def _add_connect_edge(db_id: str, label: str) -> None:
            result.edges.append(GraphEdge(
                source=class_node_id,
                target=db_id,
                kind=EdgeKind.CONNECTS_TO,
                label=label,
            ))

        # Pattern 1: DriverManager.getConnection("jdbc:...")
        for i, line in enumerate(lines):
            m = _DRIVER_MANAGER_RE.search(line)
            if not m:
                continue
            url = m.group(1)
            props = _parse_jdbc_url(url)
            db_type = props.get("db_type", "unknown")
            host = props.get("host", "unknown")
            db_id = f"db:{db_type}:{host}"
            _ensure_db_node(db_id, f"{db_type}@{host}", i + 1, props)
            _add_connect_edge(db_id, f"{class_name} connects to {db_type}@{host}")

        # Pattern 2: JdbcTemplate / NamedParameterJdbcTemplate / JdbcClient usage
        for i, line in enumerate(lines):
            m = _JDBC_TEMPLATE_RE.search(line)
            if not m:
                continue
            template_type = m.group(1)
            db_id = f"{ctx.file_path}:jdbc:{class_name}"
            _ensure_db_node(
                db_id,
                f"{class_name} ({template_type})",
                i + 1,
                {"template_type": template_type},
            )
            _add_connect_edge(db_id, f"{class_name} uses {template_type}")

        # Pattern 3: DataSource bean definitions
        for i, line in enumerate(lines):
            m = _DATASOURCE_BEAN_RE.search(line)
            if not m:
                continue
            db_id = f"{ctx.file_path}:jdbc:{class_name}"
            _ensure_db_node(
                db_id,
                f"{class_name} (DataSource)",
                i + 1,
                {"datasource": True},
            )

        # Pattern 4: spring.datasource.url in properties content
        if "spring.datasource" in text:
            for i, line in enumerate(lines):
                m = _SPRING_DATASOURCE_RE.search(line)
                if not m:
                    continue
                url = m.group(1)
                props = _parse_jdbc_url(url)
                db_type = props.get("db_type", "unknown")
                host = props.get("host", "unknown")
                db_id = f"db:{db_type}:{host}"
                _ensure_db_node(db_id, f"{db_type}@{host}", i + 1, props)

        # Pattern 5: Standalone JDBC URL strings (not already caught above)
        for i, line in enumerate(lines):
            # Skip lines already matched by DriverManager or spring.datasource
            if "DriverManager" in line or "spring.datasource" in line:
                continue
            urls = re.findall(r'"(jdbc:[^"]+)"', line)
            for url in urls:
                props = _parse_jdbc_url(url)
                db_type = props.get("db_type", "unknown")
                host = props.get("host", "unknown")
                db_id = f"db:{db_type}:{host}"
                _ensure_db_node(db_id, f"{db_type}@{host}", i + 1, props)
                _add_connect_edge(
                    db_id, f"{class_name} connects to {db_type}@{host}"
                )

        return result
