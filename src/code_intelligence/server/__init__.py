"""Code IQ server — unified REST API + MCP on a single port."""

from __future__ import annotations

from code_intelligence.server.app import create_app

__all__ = ["create_app"]
