"""FastAPI application assembly — mounts REST API, MCP server, and welcome page."""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import HTMLResponse

from code_intelligence.server.middleware import AuthMiddleware
from code_intelligence.server.mcp_server import get_mcp_app, set_service
from code_intelligence.server.routes import create_router
from code_intelligence.server.service import CodeIQService


def create_app(
    codebase_path: Path = Path("."),
    backend: str = "networkx",
    config_path: Path | None = None,
) -> FastAPI:
    """Create and configure the unified Code IQ server."""
    service = CodeIQService(
        path=codebase_path, backend=backend, config_path=config_path
    )

    # Set up MCP server
    set_service(service)
    mcp_app = get_mcp_app()

    # Create FastAPI with MCP lifespan
    app = FastAPI(
        title="Code IQ",
        description="Code Intelligence — graph queries, flow diagrams, and codebase analysis",
        lifespan=mcp_app.lifespan,
    )

    # Auth middleware stub (no-op, ready for future auth)
    app.add_middleware(AuthMiddleware)

    # Mount MCP at /mcp (streamable HTTP)
    app.mount("/mcp", mcp_app)

    # Include REST routes at /api
    router = create_router(service)
    app.include_router(router)

    # Welcome page at /
    @app.get("/", response_class=HTMLResponse, include_in_schema=False)
    async def welcome():
        template_path = Path(__file__).parent / "templates" / "welcome.html"
        return HTMLResponse(template_path.read_text(encoding="utf-8"))

    return app
