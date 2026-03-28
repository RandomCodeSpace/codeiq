"""NestJS guard and role-based access control detector."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation


class NestJSGuardsDetector:
    """Detects NestJS guard decorators, role decorators, and canActivate implementations."""

    name: str = "typescript.nestjs_guards"
    supported_languages: tuple[str, ...] = ("typescript",)

    # @UseGuards(JwtAuthGuard) or @UseGuards(JwtAuthGuard, RolesGuard)
    _USE_GUARDS_PATTERN = re.compile(
        r"@UseGuards\(\s*([^)]+)\)"
    )

    # @Roles('admin', 'user') or @Roles("admin", "user")
    _ROLES_PATTERN = re.compile(
        r"@Roles\(\s*([^)]+)\)"
    )

    # canActivate(context): boolean/Promise<boolean>/Observable<boolean>
    _CAN_ACTIVATE_PATTERN = re.compile(
        r"(?:async\s+)?canActivate\s*\("
    )

    # AuthGuard('jwt') or AuthGuard("local")
    _AUTH_GUARD_PATTERN = re.compile(
        r"AuthGuard\(\s*['\"](\w+)['\"]\s*\)"
    )

    @staticmethod
    def _parse_roles(raw: str) -> list[str]:
        """Extract role strings from a @Roles(...) argument list."""
        roles: list[str] = []
        for match in re.finditer(r"""['"]([\w\-]+)['"]""", raw):
            roles.append(match.group(1))
        return roles

    @staticmethod
    def _parse_guard_names(raw: str) -> list[str]:
        """Extract guard class names from a @UseGuards(...) argument list."""
        names: list[str] = []
        for token in raw.split(","):
            token = token.strip()
            if token and re.match(r"^\w+$", token):
                names.append(token)
        return names

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        # Detect @UseGuards(...)
        for match in self._USE_GUARDS_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            guard_names = self._parse_guard_names(match.group(1))
            for guard_name in guard_names:
                node_id = f"auth:{ctx.file_path}:UseGuards({guard_name}):{line}"
                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.GUARD,
                    label=f"UseGuards({guard_name})",
                    fqn=f"{ctx.file_path}::UseGuards({guard_name})",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    annotations=["@UseGuards"],
                    properties={
                        "auth_type": "nestjs_guard",
                        "guard_name": guard_name,
                        "roles": [],
                    },
                ))

        # Detect @Roles(...)
        for match in self._ROLES_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            roles = self._parse_roles(match.group(1))
            node_id = f"auth:{ctx.file_path}:Roles:{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.GUARD,
                label=f"Roles({', '.join(roles)})",
                fqn=f"{ctx.file_path}::Roles",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                annotations=["@Roles"],
                properties={
                    "auth_type": "nestjs_guard",
                    "roles": roles,
                },
            ))

        # Detect canActivate() implementations
        for match in self._CAN_ACTIVATE_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"auth:{ctx.file_path}:canActivate:{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.GUARD,
                label="canActivate()",
                fqn=f"{ctx.file_path}::canActivate",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "nestjs_guard",
                    "guard_impl": "canActivate",
                    "roles": [],
                },
            ))

        # Detect AuthGuard('jwt') etc.
        for match in self._AUTH_GUARD_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            strategy = match.group(1)
            node_id = f"auth:{ctx.file_path}:AuthGuard({strategy}):{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.GUARD,
                label=f"AuthGuard('{strategy}')",
                fqn=f"{ctx.file_path}::AuthGuard({strategy})",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                annotations=["AuthGuard"],
                properties={
                    "auth_type": "nestjs_guard",
                    "strategy": strategy,
                    "roles": [],
                },
            ))

        return result
