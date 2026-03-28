"""OSSCodeIQ server — unified REST API + MCP on a single port."""

from __future__ import annotations

from osscodeiq.server.app import create_app

__all__ = ["create_app"]
