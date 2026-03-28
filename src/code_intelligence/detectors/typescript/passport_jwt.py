"""Passport.js and JWT authentication detector."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation


class PassportJwtDetector:
    """Detects Passport.js strategy registrations, authenticate calls, and JWT verification."""

    name: str = "typescript.passport_jwt"
    supported_languages: tuple[str, ...] = ("typescript", "javascript")

    # passport.use(new JwtStrategy(...)) or passport.use(new LocalStrategy(...))
    _PASSPORT_USE_PATTERN = re.compile(
        r"passport\.use\(\s*new\s+(\w+Strategy)\s*\("
    )

    # passport.authenticate('jwt') or passport.authenticate("local", ...)
    _PASSPORT_AUTH_PATTERN = re.compile(
        r"passport\.authenticate\(\s*['\"](\w+)['\"]"
    )

    # jwt.verify(token, secret) or jwt.verify(token, secret, options)
    _JWT_VERIFY_PATTERN = re.compile(
        r"jwt\.verify\s*\("
    )

    # require('express-jwt') or require("express-jwt")
    _REQUIRE_EXPRESS_JWT_PATTERN = re.compile(
        r"""require\(\s*['"]express-jwt['"]\s*\)"""
    )

    # import { expressjwt } from 'express-jwt' (and variants)
    _IMPORT_EXPRESS_JWT_PATTERN = re.compile(
        r"""import\s+\{[^}]*\bexpressjwt\b[^}]*\}\s+from\s+['"]express-jwt['"]"""
    )

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        # Detect passport.use(new XxxStrategy(...))
        for match in self._PASSPORT_USE_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            strategy_name = match.group(1)
            node_id = f"auth:{ctx.file_path}:passport.use({strategy_name}):{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.GUARD,
                label=f"passport.use({strategy_name})",
                fqn=f"{ctx.file_path}::passport.use({strategy_name})",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "passport",
                    "strategy": strategy_name,
                },
            ))

        # Detect passport.authenticate('xxx')
        for match in self._PASSPORT_AUTH_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            strategy = match.group(1)
            node_id = f"auth:{ctx.file_path}:passport.authenticate({strategy}):{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.MIDDLEWARE,
                label=f"passport.authenticate('{strategy}')",
                fqn=f"{ctx.file_path}::passport.authenticate({strategy})",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "jwt",
                    "strategy": strategy,
                },
            ))

        # Detect jwt.verify(...)
        for match in self._JWT_VERIFY_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"auth:{ctx.file_path}:jwt.verify:{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.MIDDLEWARE,
                label="jwt.verify()",
                fqn=f"{ctx.file_path}::jwt.verify",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "jwt",
                },
            ))

        # Detect require('express-jwt')
        for match in self._REQUIRE_EXPRESS_JWT_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"auth:{ctx.file_path}:require(express-jwt):{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.MIDDLEWARE,
                label="require('express-jwt')",
                fqn=f"{ctx.file_path}::require(express-jwt)",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "jwt",
                    "library": "express-jwt",
                },
            ))

        # Detect import { expressjwt } from 'express-jwt'
        for match in self._IMPORT_EXPRESS_JWT_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"auth:{ctx.file_path}:import(expressjwt):{line}"
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.MIDDLEWARE,
                label="import { expressjwt }",
                fqn=f"{ctx.file_path}::import(expressjwt)",
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=line),
                properties={
                    "auth_type": "jwt",
                    "library": "express-jwt",
                },
            ))

        return result
