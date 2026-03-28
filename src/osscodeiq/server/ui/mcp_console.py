"""MCP Tool Console — interactive terminal for executing MCP tools."""
from __future__ import annotations

import json
import re
from typing import Any

from nicegui import ui

MCP_TOOL_NAMES: list[str] = [
    "get_stats",
    "query_nodes",
    "query_edges",
    "get_node_neighbors",
    "get_ego_graph",
    "find_cycles",
    "find_shortest_path",
    "find_consumers",
    "find_producers",
    "find_callers",
    "find_dependencies",
    "find_dependents",
    "generate_flow",
    "find_component_by_file",
    "trace_impact",
    "find_related_endpoints",
    "search_graph",
    "read_file",
]

_ARG_RE = re.compile(r'(\w+)=(?:"([^"]*)"|([\S]+))')


def _coerce_arg(val: str) -> int | str:
    """Try to cast *val* to int, otherwise return the string unchanged."""
    try:
        return int(val)
    except (ValueError, TypeError):
        return val


def parse_mcp_command(raw: str) -> tuple[str, dict[str, Any]]:
    """Parse a command string into (tool_name, kwargs).

    Format::

        tool_name key1="value1" key2=value2

    Returns ``("", {})`` for empty / blank input.
    """
    raw = raw.strip()
    if not raw:
        return ("", {})

    parts = raw.split(None, 1)
    tool_name = parts[0]
    kwargs: dict[str, Any] = {}

    if len(parts) > 1:
        for match in _ARG_RE.finditer(parts[1]):
            key = match.group(1)
            # group(2) is the quoted value, group(3) the unquoted value
            value = match.group(2) if match.group(2) is not None else match.group(3)
            kwargs[key] = _coerce_arg(value)

    return (tool_name, kwargs)


# ── MCP tool lookup table ──────────────────────────────────────────────────


def get_tool_map() -> dict[str, Any]:
    """Build and return the MCP tool name -> function mapping.

    This is separated from ``_get_tool_fn`` so it can be tested without a
    NiceGUI context.  The import is deferred so the module can be loaded
    without the full server stack at import time.
    """
    from osscodeiq.server.mcp_server import (  # noqa: C0415
        find_callers,
        find_component_by_file,
        find_consumers,
        find_cycles,
        find_dependencies,
        find_dependents,
        find_producers,
        find_related_endpoints,
        find_shortest_path,
        generate_flow,
        get_ego_graph,
        get_node_neighbors,
        get_stats,
        query_edges,
        query_nodes,
        read_file,
        search_graph,
        trace_impact,
    )

    return {
        "get_stats": get_stats,
        "query_nodes": query_nodes,
        "query_edges": query_edges,
        "get_node_neighbors": get_node_neighbors,
        "get_ego_graph": get_ego_graph,
        "find_cycles": find_cycles,
        "find_shortest_path": find_shortest_path,
        "find_consumers": find_consumers,
        "find_producers": find_producers,
        "find_callers": find_callers,
        "find_dependencies": find_dependencies,
        "find_dependents": find_dependents,
        "generate_flow": generate_flow,
        "find_component_by_file": find_component_by_file,
        "trace_impact": trace_impact,
        "find_related_endpoints": find_related_endpoints,
        "search_graph": search_graph,
        "read_file": read_file,
    }


def _get_tool_fn(name: str):
    """Import and return the MCP tool function by *name*, or None."""
    return get_tool_map().get(name)


# ── Console builder ────────────────────────────────────────────────────────


def create_mcp_console(service) -> None:  # noqa: ARG001 — service kept for API parity
    """Build the MCP Console tab inside a NiceGUI page."""

    ui.label("MCP Tool Console").classes("text-xl font-bold")
    ui.label("Execute MCP tools interactively").classes("text-sm text-gray-500")

    scroll = ui.scroll_area().classes("w-full border rounded").style("height: 480px")

    # Seed welcome message
    with scroll:
        output_col = ui.column().classes("w-full gap-1 p-2")

    with output_col:
        ui.label("Welcome to the MCP Tool Console.").classes("font-mono text-sm")
        ui.label('Type a tool name and arguments, or "help" to list tools.').classes(
            "font-mono text-sm text-gray-500"
        )

    # ── input row ───────────────────────────────────────────────────────
    with ui.row().classes("w-full items-center gap-2 mt-2"):
        ui.label("$").classes("font-mono text-lg")
        cmd_input = ui.input(placeholder="get_stats").classes("flex-grow font-mono")
        run_btn = ui.button("Run")

    # ── handler ─────────────────────────────────────────────────────────

    async def _execute() -> None:
        raw = cmd_input.value or ""
        raw = raw.strip()
        if not raw:
            return

        # Echo command
        with output_col:
            ui.label(f"$ {raw}").classes("font-mono text-sm font-bold mt-2")

        cmd_input.value = ""

        # Handle built-in "help"
        if raw.lower() == "help":
            with output_col:
                ui.label("Available tools:").classes("font-mono text-sm mt-1")
                for name in sorted(MCP_TOOL_NAMES):
                    ui.label(f"  {name}").classes("font-mono text-sm text-blue-600")
            scroll.scroll_to(percent=1.0)
            return

        tool_name, kwargs = parse_mcp_command(raw)

        fn = _get_tool_fn(tool_name)
        if fn is None:
            with output_col:
                ui.label(f"Unknown tool: {tool_name}").classes(
                    "font-mono text-sm text-red-600"
                )
            scroll.scroll_to(percent=1.0)
            return

        try:
            result = fn(**kwargs)

            # MCP tools return JSON strings — parse and re-format
            try:
                parsed = json.loads(result)
                formatted = json.dumps(parsed, indent=2)
            except (json.JSONDecodeError, TypeError):
                formatted = str(result)

            with output_col:
                pre = ui.element("pre").classes(
                    "font-mono text-sm bg-gray-50 p-2 rounded overflow-x-auto whitespace-pre-wrap"
                )
                with pre:
                    ui.label(formatted).classes("font-mono text-sm")

        except Exception as exc:  # noqa: BLE001
            with output_col:
                ui.label(f"Error: {exc}").classes("font-mono text-sm text-red-600")

        scroll.scroll_to(percent=1.0)

    run_btn.on_click(_execute)
    cmd_input.on("keydown.enter", _execute)
