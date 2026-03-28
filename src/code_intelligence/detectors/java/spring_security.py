"""Spring Security auth detector for Java source files."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation

# @Secured("ROLE_ADMIN") or @Secured({"ROLE_ADMIN", "ROLE_USER"})
_SECURED_RE = re.compile(
    r'@Secured\(\s*(?:\{([^}]*)\}|"([^"]*)")\s*\)'
)

# @PreAuthorize("hasRole('ADMIN')") and similar SpEL expressions
_PRE_AUTHORIZE_RE = re.compile(
    r'@PreAuthorize\(\s*"([^"]*)"\s*\)'
)

# @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"}) or @RolesAllowed("ROLE_ADMIN")
_ROLES_ALLOWED_RE = re.compile(
    r'@RolesAllowed\(\s*(?:\{([^}]*)\}|"([^"]*)")\s*\)'
)

# @EnableWebSecurity
_ENABLE_WEB_SECURITY_RE = re.compile(r'@EnableWebSecurity\b')

# @EnableMethodSecurity
_ENABLE_METHOD_SECURITY_RE = re.compile(r'@EnableMethodSecurity\b')

# SecurityFilterChain method declaration
_SECURITY_FILTER_CHAIN_RE = re.compile(
    r'(?:public\s+)?SecurityFilterChain\s+(\w+)\s*\('
)

# .authorizeHttpRequests() fluent call
_AUTHORIZE_HTTP_REQUESTS_RE = re.compile(
    r'\.authorizeHttpRequests\s*\('
)

# Helper to extract quoted role strings from annotation values
_ROLE_STR_RE = re.compile(r'"([^"]*)"')

# Extract roles from hasRole/hasAnyRole SpEL expressions
_HAS_ROLE_RE = re.compile(r"hasRole\(\s*'([^']*)'\s*\)")
_HAS_ANY_ROLE_RE = re.compile(r"hasAnyRole\(\s*([^)]+)\)")
_SINGLE_QUOTED_RE = re.compile(r"'([^']*)'")


def _extract_roles_from_annotation(groups: tuple[str | None, str | None]) -> list[str]:
    """Extract role names from a @Secured or @RolesAllowed annotation match groups."""
    multi, single = groups
    if single is not None:
        return [single]
    if multi is not None:
        return [m.group(1) for m in _ROLE_STR_RE.finditer(multi)]
    return []


def _extract_roles_from_spel(expr: str) -> list[str]:
    """Extract role names from a SpEL expression in @PreAuthorize."""
    roles: list[str] = []
    for m in _HAS_ROLE_RE.finditer(expr):
        roles.append(m.group(1))
    for m in _HAS_ANY_ROLE_RE.finditer(expr):
        inner = m.group(1)
        for q in _SINGLE_QUOTED_RE.finditer(inner):
            roles.append(q.group(1))
    return roles


def _line_number(text: str, pos: int) -> int:
    """Return 1-based line number for a character offset."""
    return text[:pos].count("\n") + 1


class SpringSecurityDetector:
    """Detects Spring Security auth patterns in Java source files."""

    name: str = "spring_security"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        # @Secured annotations
        for m in _SECURED_RE.finditer(text):
            line = _line_number(text, m.start())
            roles = _extract_roles_from_annotation((m.group(1), m.group(2)))
            result.nodes.append(GraphNode(
                id=f"auth:{ctx.file_path}:Secured:{line}",
                kind=NodeKind.GUARD,
                label="@Secured",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                annotations=["@Secured"],
                properties={
                    "auth_type": "spring_security",
                    "roles": roles,
                    "auth_required": True,
                },
            ))

        # @PreAuthorize annotations
        for m in _PRE_AUTHORIZE_RE.finditer(text):
            line = _line_number(text, m.start())
            expr = m.group(1)
            roles = _extract_roles_from_spel(expr)
            result.nodes.append(GraphNode(
                id=f"auth:{ctx.file_path}:PreAuthorize:{line}",
                kind=NodeKind.GUARD,
                label="@PreAuthorize",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                annotations=["@PreAuthorize"],
                properties={
                    "auth_type": "spring_security",
                    "roles": roles,
                    "expression": expr,
                    "auth_required": True,
                },
            ))

        # @RolesAllowed annotations
        for m in _ROLES_ALLOWED_RE.finditer(text):
            line = _line_number(text, m.start())
            roles = _extract_roles_from_annotation((m.group(1), m.group(2)))
            result.nodes.append(GraphNode(
                id=f"auth:{ctx.file_path}:RolesAllowed:{line}",
                kind=NodeKind.GUARD,
                label="@RolesAllowed",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                annotations=["@RolesAllowed"],
                properties={
                    "auth_type": "spring_security",
                    "roles": roles,
                    "auth_required": True,
                },
            ))

        # @EnableWebSecurity
        for m in _ENABLE_WEB_SECURITY_RE.finditer(text):
            line = _line_number(text, m.start())
            result.nodes.append(GraphNode(
                id=f"auth:{ctx.file_path}:EnableWebSecurity:{line}",
                kind=NodeKind.GUARD,
                label="@EnableWebSecurity",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                annotations=["@EnableWebSecurity"],
                properties={
                    "auth_type": "spring_security",
                    "roles": [],
                    "auth_required": True,
                },
            ))

        # @EnableMethodSecurity
        for m in _ENABLE_METHOD_SECURITY_RE.finditer(text):
            line = _line_number(text, m.start())
            result.nodes.append(GraphNode(
                id=f"auth:{ctx.file_path}:EnableMethodSecurity:{line}",
                kind=NodeKind.GUARD,
                label="@EnableMethodSecurity",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                annotations=["@EnableMethodSecurity"],
                properties={
                    "auth_type": "spring_security",
                    "roles": [],
                    "auth_required": True,
                },
            ))

        # SecurityFilterChain method declarations
        for m in _SECURITY_FILTER_CHAIN_RE.finditer(text):
            line = _line_number(text, m.start())
            method_name = m.group(1)
            result.nodes.append(GraphNode(
                id=f"auth:{ctx.file_path}:SecurityFilterChain:{line}",
                kind=NodeKind.GUARD,
                label=f"SecurityFilterChain:{method_name}",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "spring_security",
                    "roles": [],
                    "method_name": method_name,
                    "auth_required": True,
                },
            ))

        # .authorizeHttpRequests() calls
        for m in _AUTHORIZE_HTTP_REQUESTS_RE.finditer(text):
            line = _line_number(text, m.start())
            result.nodes.append(GraphNode(
                id=f"auth:{ctx.file_path}:authorizeHttpRequests:{line}",
                kind=NodeKind.GUARD,
                label=".authorizeHttpRequests()",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "spring_security",
                    "roles": [],
                    "auth_required": True,
                },
            ))

        return result
