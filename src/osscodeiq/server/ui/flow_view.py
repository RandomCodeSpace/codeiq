"""Flow View — wraps existing Cytoscape flow visualization."""
from __future__ import annotations

from nicegui import ui


def create_flow_page(service) -> None:
    """Build the Flow tab inside a NiceGUI page.

    Attempts to generate an overview flow diagram as HTML via the service.
    Falls back to a placeholder when no analysis data is available.
    """
    with ui.element("div").classes("max-w-7xl mx-auto px-4 w-full"):
        try:
            result = service.generate_flow("overview", "html")

            # generate_flow returns a dict; the HTML is in the "content" key
            html_content: str | None = None
            if isinstance(result, dict):
                html_content = result.get("content") or result.get("html")
            elif isinstance(result, str):
                html_content = result

            if html_content:
                ui.html(html_content).classes("w-full")
            else:
                _show_placeholder()

        except Exception:  # noqa: BLE001
            _show_placeholder()


def _show_placeholder() -> None:
    """Show a professional centered placeholder when no flow data is available."""
    with ui.card().classes("w-full max-w-md mx-auto mt-16"):
        with ui.card_section().classes("items-center text-center"):
            with ui.column().classes("items-center gap-3 py-8"):
                ui.icon("account_tree", size="64px").classes("opacity-30")
                ui.label("No flow data available").classes(
                    "text-xl font-medium opacity-70"
                )
                ui.label(
                    "Run 'osscodeiq analyze <path>' to scan a codebase, "
                    "then refresh this page."
                ).classes("text-sm opacity-50 max-w-xs text-center")
