"""Explorer page for the OSSCodeIQ NiceGUI-based web UI."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from nicegui import ui

from osscodeiq.server.ui.components import (
    build_detail_data,
    build_kind_card_data,
    build_node_card_data,
)
from osscodeiq.server.ui.theme import get_animation_css, get_kind_color, get_kind_icon


@dataclass
class ExplorerState:
    """Tracks navigation state for the explorer page.

    Levels:
      - "kinds": shows all node kinds as cards
      - "nodes": shows nodes of a specific kind
    """

    level: str = "kinds"
    current_kind: str | None = None
    breadcrumb: list[dict[str, Any]] = field(default_factory=list)
    page_offset: int = 0
    page_limit: int = 50

    def __post_init__(self) -> None:
        if not self.breadcrumb:
            self.breadcrumb = [{"label": "Home", "level": "kinds", "kind": None}]

    def drill_down(self, kind: str) -> None:
        """Navigate from kinds level into a specific kind's nodes."""
        self.level = "nodes"
        self.current_kind = kind
        self.page_offset = 0
        self.breadcrumb.append({"label": kind, "level": "nodes", "kind": kind})

    def go_home(self) -> None:
        """Reset navigation to the top-level kinds view."""
        self.level = "kinds"
        self.current_kind = None
        self.page_offset = 0
        self.breadcrumb = [{"label": "Home", "level": "kinds", "kind": None}]

    def navigate_to(self, index: int) -> None:
        """Navigate to a specific breadcrumb index, trimming the trail."""
        if index <= 0:
            self.go_home()
            return
        if index < len(self.breadcrumb):
            target = self.breadcrumb[index]
            self.breadcrumb = self.breadcrumb[: index + 1]
            self.level = target["level"]
            self.current_kind = target["kind"]
            self.page_offset = 0


# ---------------------------------------------------------------------------
# Search filter JavaScript (client-side, no server round-trip)
# ---------------------------------------------------------------------------

_SEARCH_JS_TEMPLATE = """
(function(query) {{
    const cards = document.querySelectorAll('{container}');
    const lower = query.toLowerCase();
    cards.forEach(function(card) {{
        const text = card.textContent.toLowerCase();
        if (!lower || text.includes(lower)) {{
            card.style.opacity = '1';
            card.style.pointerEvents = 'auto';
            card.style.display = '';
        }} else {{
            card.style.opacity = '0.15';
            card.style.pointerEvents = 'none';
        }}
    }});
}})("{query}")
"""


def build_filter_js(query: str, container_selector: str = ".explorer-card") -> str:
    """Build a JavaScript snippet that filters cards by text content.

    Parameters
    ----------
    query:
        The search string to filter by. Double-quotes are escaped.
    container_selector:
        CSS selector for the card elements to filter.

    Returns
    -------
    A self-executing JavaScript string.
    """
    safe_query = query.replace("\\", "\\\\").replace('"', '\\"')
    return _SEARCH_JS_TEMPLATE.format(
        container=container_selector, query=safe_query
    )


# ---------------------------------------------------------------------------
# Reusable detail dialog (created once, populated dynamically)
# ---------------------------------------------------------------------------

_detail_dialog: ui.dialog | None = None
_detail_card_container: ui.card | None = None


def _ensure_detail_dialog() -> tuple[ui.dialog, ui.card]:
    """Return the singleton detail dialog, creating it on first call."""
    global _detail_dialog, _detail_card_container  # noqa: PLW0603
    if _detail_dialog is None:
        _detail_dialog = ui.dialog().props("maximized=false")
        _detail_dialog.props("position=standard")
        with _detail_dialog:
            _detail_card_container = ui.card().classes(
                "w-full max-w-2xl mx-auto"
            )
    return _detail_dialog, _detail_card_container  # type: ignore[return-value]


def _show_detail_modal(service: Any, node_id: str) -> None:
    """Open the reusable dialog showing full node details."""
    try:
        raw = service.node_detail_with_edges(node_id)
    except Exception as exc:  # noqa: BLE001
        ui.notify(f"Failed to load node details: {exc}", type="negative")
        return

    if raw is None:
        ui.notify("Node not found", type="warning")
        return

    data = build_detail_data(raw)
    dlg, card = _ensure_detail_dialog()

    # Clear previous content and rebuild
    card.clear()

    with card:
        # Header
        kind = data["kind"]
        color = get_kind_color(kind)
        with ui.row().classes("items-center gap-2 w-full"):
            ui.icon(get_kind_icon(kind)).classes("text-2xl").style(
                f"color: {color}"
            )
            ui.label(data["name"]).classes("text-xl font-bold")
            ui.badge(kind).props("outline").style(
                f"color: {color}; border-color: {color}"
            )

        ui.separator()

        # Properties table
        if data["properties"]:
            ui.label("Properties").classes("text-sm font-semibold opacity-60 mt-2")
            with ui.element("div").classes("w-full"):
                for key, value in data["properties"]:
                    with ui.row().classes("items-center gap-2 py-1"):
                        ui.label(key).classes(
                            "text-xs font-medium opacity-50 w-28 shrink-0"
                        )
                        ui.label(str(value)).classes(
                            "text-sm select-all break-all"
                        )

        # Outgoing edges
        if data["edges_out"]:
            ui.label("Outgoing Edges").classes(
                "text-sm font-semibold opacity-60 mt-4"
            )
            for edge in data["edges_out"]:
                with ui.row().classes("items-center gap-1 py-0.5"):
                    ui.badge(edge["kind"]).props("outline dense")
                    ui.icon("arrow_forward").classes("text-xs opacity-50")
                    ui.label(
                        edge.get("target_name", edge.get("target_id", "?"))
                    ).classes("text-sm select-all")

        # Incoming edges
        if data["edges_in"]:
            ui.label("Incoming Edges").classes(
                "text-sm font-semibold opacity-60 mt-4"
            )
            for edge in data["edges_in"]:
                with ui.row().classes("items-center gap-1 py-0.5"):
                    ui.label(
                        edge.get("source_name", edge.get("source_id", "?"))
                    ).classes("text-sm select-all")
                    ui.icon("arrow_forward").classes("text-xs opacity-50")
                    ui.badge(edge["kind"]).props("outline dense")

        # Close button
        with ui.row().classes("w-full justify-end mt-4"):
            ui.button("Close", on_click=dlg.close).props("flat")

    dlg.open()


# ---------------------------------------------------------------------------
# Kind summary modal (reusable dialog)
# ---------------------------------------------------------------------------

_kind_dialog: ui.dialog | None = None
_kind_card_container: ui.card | None = None


def _ensure_kind_dialog() -> tuple[ui.dialog, ui.card]:
    """Return the singleton kind summary dialog."""
    global _kind_dialog, _kind_card_container  # noqa: PLW0603
    if _kind_dialog is None:
        _kind_dialog = ui.dialog()
        with _kind_dialog:
            _kind_card_container = ui.card().classes("w-full max-w-md mx-auto")
    return _kind_dialog, _kind_card_container  # type: ignore[return-value]


def _show_kind_modal(kind_data: dict[str, Any]) -> None:
    """Open the reusable dialog showing a summary of a node kind."""
    dlg, card = _ensure_kind_dialog()
    card.clear()

    with card:
        with ui.row().classes("items-center gap-2"):
            ui.icon(kind_data["icon"]).classes("text-2xl").style(
                f"color: {kind_data['color']}"
            )
            ui.label(kind_data["title"]).classes("text-xl font-bold")

        ui.separator()

        ui.label(f"Total nodes: {kind_data['count']}").classes("text-sm")

        if kind_data.get("preview"):
            ui.label("Preview").classes("text-sm font-semibold opacity-60 mt-2")
            for item in kind_data["preview"][:10]:
                ui.label(f"  {item}").classes("text-xs opacity-50 select-all")

        with ui.row().classes("w-full justify-end mt-4"):
            ui.button("Close", on_click=dlg.close).props("flat")

    dlg.open()


# ---------------------------------------------------------------------------
# Card renderers
# ---------------------------------------------------------------------------

def _render_kind_card(
    kind_data: dict[str, Any],
    service: Any,
    state: ExplorerState,
    refresh_fn: Any,
    index: int,
) -> None:
    """Render a single kind card with left border accent color."""
    color = kind_data["color"]
    delay_cls = f"card-animate-{min(index + 1, 10)}"

    with ui.card().classes(
        f"explorer-card card-animate {delay_cls} w-full"
    ).style(
        f"border-left: 4px solid {color};"
    ):
        with ui.card_section():
            with ui.row().classes("items-center gap-2"):
                ui.icon(kind_data["icon"]).classes("text-xl").style(
                    f"color: {color}"
                )
                ui.label(kind_data["title"]).classes("text-lg font-semibold")
            ui.label(f"{kind_data['count']} nodes").classes(
                "text-sm opacity-60"
            )
            if kind_data.get("preview"):
                for item in kind_data["preview"][:3]:
                    ui.label(item).classes(
                        "text-xs opacity-50 truncate"
                    )

        with ui.card_actions().classes("justify-end"):
            ui.button(
                "Details",
                on_click=lambda kd=kind_data: _show_kind_modal(kd),
            ).props("flat dense")
            ui.button(
                "Explore",
                on_click=lambda k=kind_data["kind"]: _on_drill_down(
                    k, state, refresh_fn
                ),
            ).props("flat dense color=primary")


def _render_node_card(
    node_data: dict[str, Any],
    service: Any,
    index: int,
) -> None:
    """Render a single node card."""
    delay_cls = f"card-animate-{min(index + 1, 10)}"

    with ui.card().classes(
        f"explorer-card card-animate {delay_cls} w-full"
    ):
        with ui.card_section():
            ui.label(node_data["title"]).classes("text-base font-semibold")
            if node_data["subtitle"]:
                ui.label(node_data["subtitle"]).classes(
                    "text-xs opacity-60 truncate"
                )
            if node_data.get("properties"):
                with ui.row().classes("gap-1 flex-wrap mt-1"):
                    for key, val in list(node_data["properties"].items())[:5]:
                        ui.badge(f"{key}: {val}").props("outline dense")

        with ui.card_actions().classes("justify-end"):
            ui.button(
                "Details",
                on_click=lambda nid=node_data["id"]: _show_detail_modal(
                    service, nid
                ),
            ).props("flat dense")


# ---------------------------------------------------------------------------
# Empty state
# ---------------------------------------------------------------------------

def _render_empty_state(message: str, hint: str = "") -> None:
    """Render a centered empty-state card with icon and message."""
    with ui.card().classes("w-full max-w-md mx-auto mt-8"):
        with ui.card_section().classes("items-center text-center"):
            with ui.column().classes("items-center gap-2 py-4"):
                ui.icon("inbox", size="48px").classes("opacity-40")
                ui.label(message).classes("text-lg font-medium opacity-70")
                if hint:
                    ui.label(hint).classes("text-sm opacity-50")


# ---------------------------------------------------------------------------
# Navigation helpers
# ---------------------------------------------------------------------------

def _on_drill_down(kind: str, state: ExplorerState, refresh_fn: Any) -> None:
    """Handle drilling down into a kind."""
    state.drill_down(kind)
    refresh_fn()


def _on_page_change(
    delta: int, state: ExplorerState, total: int, refresh_fn: Any
) -> None:
    """Handle pagination offset change."""
    new_offset = state.page_offset + delta
    if new_offset < 0:
        new_offset = 0
    if new_offset >= total:
        return
    state.page_offset = new_offset
    refresh_fn()


# ---------------------------------------------------------------------------
# Main explorer page builder
# ---------------------------------------------------------------------------

def create_explorer_page(service: Any) -> None:
    """Build the explorer tab content within an existing NiceGUI page context.

    Parameters
    ----------
    service:
        A CodeIQService instance providing list_kinds(),
        nodes_by_kind_paginated(), and node_detail_with_edges().
    """
    state = ExplorerState()

    # Inject animation CSS
    ui.add_head_html(f"<style>{get_animation_css()}</style>")

    # -- Container that gets refreshed on navigation -----------------------

    @ui.refreshable
    def content() -> None:
        with ui.element("div").classes("max-w-7xl mx-auto px-4 w-full"):
            # Breadcrumb row
            with ui.row().classes("items-center gap-1 mb-2"):
                for idx, crumb in enumerate(state.breadcrumb):
                    if idx > 0:
                        ui.icon("chevron_right").classes("text-sm opacity-40")
                    if idx < len(state.breadcrumb) - 1:
                        # Use ui.button with flat/dense/no-caps for clickable breadcrumbs
                        ui.button(
                            crumb["label"],
                            on_click=lambda i=idx: _nav_to(i, state, content.refresh),
                        ).props("flat dense no-caps").classes(
                            "text-sm cursor-pointer"
                        ).style("color: var(--q-primary)")
                    else:
                        ui.label(crumb["label"]).classes(
                            "text-sm font-semibold"
                        )

            # Search input
            search_input = ui.input(
                placeholder="Filter cards...",
            ).classes("w-full max-w-sm mb-3").props("dense clearable outlined")

            search_input.on(
                "update:model-value",
                lambda e: ui.run_javascript(
                    build_filter_js(str(e.args if e.args else ""))
                ),
            )

            # Render based on level
            if state.level == "kinds":
                _render_kinds_grid(service, state, content.refresh)
            else:
                _render_nodes_grid(service, state, content.refresh)

    content()


def _nav_to(index: int, state: ExplorerState, refresh_fn: Any) -> None:
    """Navigate to a breadcrumb index and refresh."""
    state.navigate_to(index)
    refresh_fn()


def _render_kinds_grid(service: Any, state: ExplorerState, refresh_fn: Any) -> None:
    """Render the kind cards grid with loading and error states."""
    # Show spinner while loading
    spinner = ui.spinner("dots", size="lg").classes("mx-auto my-8")

    try:
        result = service.list_kinds()
    except Exception as exc:  # noqa: BLE001
        spinner.delete()
        ui.notify(f"Failed to load graph data: {exc}", type="negative")
        _render_empty_state(
            "Error loading data",
            "Check the server logs for details.",
        )
        return

    spinner.delete()

    kinds = result.get("kinds", [])

    if not kinds:
        _render_empty_state(
            "No data available",
            "Run 'osscodeiq analyze <path>' to scan a codebase first.",
        )
        return

    with ui.row().classes("text-xs opacity-50 mb-1"):
        ui.label(
            f"{result.get('total_nodes', 0)} nodes, "
            f"{result.get('total_edges', 0)} edges across "
            f"{len(kinds)} kinds"
        )

    with ui.element("div").classes(
        "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 w-full"
    ):
        for idx, kind_info in enumerate(kinds):
            card_data = build_kind_card_data(kind_info)
            _render_kind_card(card_data, service, state, refresh_fn, idx)


def _render_nodes_grid(service: Any, state: ExplorerState, refresh_fn: Any) -> None:
    """Render the node cards grid with pagination, loading, and error states."""
    kind = state.current_kind
    if kind is None:
        return

    # Show spinner while loading
    spinner = ui.spinner("dots", size="lg").classes("mx-auto my-8")

    try:
        result = service.nodes_by_kind_paginated(
            kind, state.page_limit, state.page_offset
        )
    except Exception as exc:  # noqa: BLE001
        spinner.delete()
        ui.notify(f"Failed to load {kind} nodes: {exc}", type="negative")
        _render_empty_state(
            f"Error loading {kind} nodes",
            "Check the server logs for details.",
        )
        return

    spinner.delete()

    total = result.get("total", 0)
    nodes = result.get("nodes", [])

    if not nodes and state.page_offset == 0:
        _render_empty_state(
            f"No {kind} nodes found",
            "This kind exists but has no nodes in the current graph.",
        )
        return

    # Summary line
    start = state.page_offset + 1
    end = min(state.page_offset + len(nodes), total)
    with ui.row().classes("text-xs opacity-50 mb-1"):
        ui.label(f"Showing {start}-{end} of {total} {kind} nodes")

    # Card grid
    with ui.element("div").classes(
        "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 w-full"
    ):
        for idx, node_info in enumerate(nodes):
            card_data = build_node_card_data(node_info)
            _render_node_card(card_data, service, idx)

    # Pagination controls
    with ui.row().classes("items-center justify-center gap-4 mt-4"):
        prev_disabled = state.page_offset <= 0
        prev_btn = ui.button(
            "Prev",
            on_click=lambda: _on_page_change(
                -state.page_limit, state, total, refresh_fn
            ),
        ).props("flat")
        prev_btn.set_enabled(not prev_disabled)

        ui.label(f"Page {state.page_offset // state.page_limit + 1}").classes(
            "text-sm opacity-60"
        )

        next_disabled = state.page_offset + state.page_limit >= total
        next_btn = ui.button(
            "Next",
            on_click=lambda: _on_page_change(
                state.page_limit, state, total, refresh_fn
            ),
        ).props("flat")
        next_btn.set_enabled(not next_disabled)
