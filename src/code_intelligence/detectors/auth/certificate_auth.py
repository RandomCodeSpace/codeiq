"""Certificate-based authentication detector (mTLS, X.509, TLS config, Azure AD)."""

from __future__ import annotations

import re
from dataclasses import dataclass

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation


@dataclass(frozen=True)
class _PatternDef:
    """A pattern definition with its auth_type and optional property extractor."""

    regex: re.Pattern[str]
    auth_type: str
    prop_key: str | None = None


# -- mTLS patterns --
_MTLS_PATTERNS: list[_PatternDef] = [
    _PatternDef(re.compile(r"\bssl_verify_client\b"), "mtls"),
    _PatternDef(re.compile(r"\brequestCert\s*:\s*true\b"), "mtls"),
    _PatternDef(re.compile(r'\bclientAuth\s*=\s*"true"'), "mtls"),
    _PatternDef(re.compile(r"\bX509AuthenticationFilter\b"), "mtls"),
    _PatternDef(re.compile(r"\bAddCertificateForwarding\b"), "mtls"),
]

# -- X.509 patterns --
_X509_PATTERNS: list[_PatternDef] = [
    _PatternDef(re.compile(r"\bX509AuthenticationFilter\b"), "x509"),
    _PatternDef(re.compile(r"\bCertificateAuthenticationDefaults\b"), "x509"),
    _PatternDef(re.compile(r"\.x509\s*\("), "x509"),
]

# -- TLS config patterns --
_TLS_CONFIG_PATTERNS: list[_PatternDef] = [
    _PatternDef(re.compile(r"\bjavax\.net\.ssl\.keyStore\b"), "tls_config"),
    _PatternDef(re.compile(r"\bssl\.SSLContext\b"), "tls_config"),
    _PatternDef(re.compile(r"\btls\.createServer\b"), "tls_config"),
    _PatternDef(
        re.compile(r"""(?:cert|key|ca)\s*[=:]\s*(?:fs\.readFileSync\s*\(|['"][\w/.\\-]+\.(?:pem|crt|key|cert)['"])"""),
        "tls_config",
        "cert_path",
    ),
    _PatternDef(re.compile(r"\btrustStore\b"), "tls_config"),
]

# -- Azure AD patterns --
_AZURE_AD_PATTERNS: list[_PatternDef] = [
    _PatternDef(re.compile(r"\bAzureAd\b"), "azure_ad"),
    _PatternDef(re.compile(r"\bAZURE_TENANT_ID\b"), "azure_ad", "tenant_id"),
    _PatternDef(re.compile(r"\bAZURE_CLIENT_ID\b"), "azure_ad"),
    _PatternDef(re.compile(r"""\bmsal\b"""), "azure_ad"),
    _PatternDef(re.compile(r"""['"]@azure/msal-browser['"]"""), "azure_ad"),
    _PatternDef(re.compile(r"\bAddMicrosoftIdentityWebApi\b"), "azure_ad"),
    _PatternDef(re.compile(r"\bClientCertificateCredential\b"), "azure_ad"),
]

_ALL_PATTERNS: list[_PatternDef] = (
    _MTLS_PATTERNS + _X509_PATTERNS + _TLS_CONFIG_PATTERNS + _AZURE_AD_PATTERNS
)

# Dedup: when the same line matches both mTLS and x509 via X509AuthenticationFilter,
# prefer the more specific auth_type already recorded.

_CERT_PATH_RE = re.compile(
    r"""['"]([^'"]*\.(?:pem|crt|key|cert|pfx|p12))['"]"""
)
_TENANT_ID_RE = re.compile(
    r"""AZURE_TENANT_ID\s*[=:]\s*['"]?([a-f0-9-]+)['"]?"""
)


class CertificateAuthDetector:
    """Detects certificate-based authentication patterns across multiple languages."""

    name: str = "certificate_auth"
    supported_languages: tuple[str, ...] = (
        "java", "python", "typescript", "csharp", "json", "yaml",
    )

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        # Track which lines already produced a node (first match wins per line).
        seen_lines: set[int] = set()

        for line_idx, line in enumerate(lines):
            for pdef in _ALL_PATTERNS:
                if line_idx in seen_lines:
                    break
                if pdef.regex.search(line):
                    seen_lines.add(line_idx)
                    line_num = line_idx + 1
                    matched_text = line.strip()

                    properties: dict[str, str] = {
                        "auth_type": pdef.auth_type,
                        "language": ctx.language,
                        "pattern": matched_text[:120],
                    }

                    # Extract cert_path if present
                    cert_m = _CERT_PATH_RE.search(line)
                    if cert_m:
                        properties["cert_path"] = cert_m.group(1)

                    # Extract tenant_id if present
                    tenant_m = _TENANT_ID_RE.search(line)
                    if tenant_m:
                        properties["tenant_id"] = tenant_m.group(1)

                    # Detect auth_flow for Azure AD
                    if pdef.auth_type == "azure_ad":
                        if "ClientCertificateCredential" in line:
                            properties["auth_flow"] = "client_certificate"
                        elif "msal" in line.lower():
                            properties["auth_flow"] = "msal"

                    node = GraphNode(
                        id=f"auth:{ctx.file_path}:cert:{line_num}",
                        kind=NodeKind.GUARD,
                        label=f"Certificate auth ({pdef.auth_type}): {matched_text[:60]}",
                        module=ctx.module_name,
                        location=SourceLocation(
                            file_path=ctx.file_path,
                            line_start=line_num,
                            line_end=line_num,
                        ),
                        properties=properties,
                    )
                    result.nodes.append(node)

        return result
