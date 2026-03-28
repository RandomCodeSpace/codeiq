"""Flow View — wraps existing Cytoscape flow visualization."""
from __future__ import annotations

from nicegui import ui


def create_flow_page(service) -> None:
    """Build the Flow tab inside a NiceGUI page.

    Attempts to generate an overview flow diagram as HTML via the service.
    Falls back to a placeholder when no analysis data is available.
    """
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
    """Show a friendly placeholder when no flow data is available."""
    with ui.column().classes("w-full items-center justify-center py-16"):
        ui.icon("account_tree", size="64px").classes("text-gray-400")
        ui.label("No flow data available.").classes("text-xl text-gray-500 mt-4")
        ui.label("Run 'osscodeiq analyze' first.").classes(
            "text-sm text-gray-400 mt-1"
        )
