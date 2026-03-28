"""LDAP authentication detector for Java, Python, TypeScript, and C# source files."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation

# -- Java patterns --
_JAVA_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"\bLdapContextSource\b"),
    re.compile(r"\bLdapTemplate\b"),
    re.compile(r"\bActiveDirectoryLdapAuthenticationProvider\b"),
    re.compile(r"@EnableLdapRepositories\b"),
]

# -- Python patterns --
_PYTHON_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"\bldap3\.Connection\b"),
    re.compile(r"\bldap3\.Server\b"),
    re.compile(r"\bAUTH_LDAP_SERVER_URI\b"),
    re.compile(r"\bAUTH_LDAP_BIND_DN\b"),
]

# -- TypeScript patterns --
_TS_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"""require\s*\(\s*['"]ldapjs['"]\s*\)"""),
    re.compile(r"""(?:import\s+.*\s+from\s+['"]ldapjs['"]|import\s+ldapjs\b)"""),
    re.compile(r"""['"]passport-ldapauth['"]"""),
]

# -- C# patterns --
_CSHARP_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"\bSystem\.DirectoryServices\b"),
    re.compile(r"\bLdapConnection\b"),
    re.compile(r"\bDirectoryEntry\b"),
]

_LANGUAGE_PATTERNS: dict[str, list[re.Pattern[str]]] = {
    "java": _JAVA_PATTERNS,
    "python": _PYTHON_PATTERNS,
    "typescript": _TS_PATTERNS,
    "csharp": _CSHARP_PATTERNS,
}


class LdapAuthDetector:
    """Detects LDAP authentication patterns across multiple languages."""

    name: str = "ldap_auth"
    supported_languages: tuple[str, ...] = ("java", "python", "typescript", "csharp")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        if ctx.language not in _LANGUAGE_PATTERNS:
            return result

        text = decode_text(ctx)
        lines = text.split("\n")
        patterns = _LANGUAGE_PATTERNS[ctx.language]
        seen_lines: set[int] = set()

        for line_idx, line in enumerate(lines):
            for pattern in patterns:
                if pattern.search(line) and line_idx not in seen_lines:
                    seen_lines.add(line_idx)
                    line_num = line_idx + 1
                    matched_text = line.strip()
                    node = GraphNode(
                        id=f"auth:{ctx.file_path}:ldap:{line_num}",
                        kind=NodeKind.GUARD,
                        label=f"LDAP auth: {matched_text[:80]}",
                        module=ctx.module_name,
                        location=SourceLocation(
                            file_path=ctx.file_path,
                            line_start=line_num,
                            line_end=line_num,
                        ),
                        properties={
                            "auth_type": "ldap",
                            "language": ctx.language,
                            "pattern": matched_text[:120],
                        },
                    )
                    result.nodes.append(node)

        return result
