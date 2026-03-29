"""Flow View — serves existing Cytoscape flow visualization via iframe."""
from __future__ import annotations

from typing import Any

from nicegui import app, ui


def create_flow_page(service: Any) -> None:
    """Build the Flow tab inside a NiceGUI page.

    The flow engine generates a full self-contained HTML page (DOCTYPE + Cytoscape.js
    + vendor JS). This cannot be embedded as a fragment via ui.html(). Instead, we
    serve it at a dedicated route and embed it in an iframe.
    """
    _flow_html: str | None = None

    try:
        result = service.generate_flow("overview", "html")
        if isinstance(result, str) and result.strip().startswith("<!"):
            _flow_html = result
        elif isinstance(result, dict):
            content = result.get("content") or result.get("html", "")
            if content and content.strip().startswith("<!"):
                _flow_html = content
    except Exception:  # noqa: BLE001
        _flow_html = None

    if _flow_html:
        # Register a dedicated route to serve the full-page HTML
        @app.get("/flow-embed", include_in_schema=False)
        async def _serve_flow_html():
            from starlette.responses import HTMLResponse
            return HTMLResponse(_flow_html)

        with ui.column().classes("w-full h-full"):
            ui.html(
                '<iframe src="/flow-embed" '
                'style="width:100%;height:calc(100vh - 160px);border:none;" '
                'loading="lazy"></iframe>'
            )
    else:
        _show_placeholder()


def _show_placeholder() -> None:
    """Show a professional centered placeholder when no flow data is available."""
    with ui.column().classes("w-full items-center justify-center py-20"):
        with ui.card().classes("max-w-md text-center"):
            with ui.card_section():
                with ui.column().classes("items-center gap-4 py-8"):
                    ui.icon("account_tree", size="64px").classes("opacity-30")
                    ui.label("No flow data available").classes(
                        "text-xl font-medium opacity-70"
                    )
                    ui.label(
                        "Run 'osscodeiq analyze <path>' to generate flow diagrams, "
                        "then refresh this page."
                    ).classes("text-sm opacity-50")
