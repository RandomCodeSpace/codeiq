"""Data helper functions for building UI component data structures."""

from __future__ import annotations

from typing import Any

from osscodeiq.server.ui.theme import get_kind_color, get_kind_icon


def build_kind_card_data(kind_info: dict[str, Any]) -> dict[str, Any]:
    """Transform a kind summary dict into card display data.

    Parameters
    ----------
    kind_info:
        Dict with keys: kind, count, preview (optional list of strings).

    Returns
    -------
    Dict with keys: kind, title, count, icon, color, preview.
    """
    kind = kind_info["kind"]
    return {
        "kind": kind,
        "title": kind,
        "count": kind_info.get("count", 0),
        "icon": get_kind_icon(kind),
        "color": get_kind_color(kind),
        "preview": kind_info.get("preview", []),
    }


def build_node_card_data(node_info: dict[str, Any]) -> dict[str, Any]:
    """Transform a node summary dict into card display data.

    Parameters
    ----------
    node_info:
        Dict with keys: id, name, module (optional), file_path (optional),
        edge_count (optional), properties (optional).

    Returns
    -------
    Dict with keys: id, title, subtitle, module, properties.
    """
    parts: list[str] = []
    module = node_info.get("module")
    file_path = node_info.get("file_path")
    edge_count = node_info.get("edge_count")

    if module:
        parts.append(module)
    if file_path:
        parts.append(file_path)
    if edge_count is not None:
        parts.append(f"{edge_count} edges")

    return {
        "id": node_info["id"],
        "title": node_info["name"],
        "subtitle": " \u00b7 ".join(parts) if parts else "",
        "module": module,
        "properties": node_info.get("properties", {}),
    }


def build_detail_data(detail: dict[str, Any]) -> dict[str, Any]:
    """Transform a node detail response into modal display data.

    Parameters
    ----------
    detail:
        Dict with keys: id, name, kind, fqn (optional), module (optional),
        file_path (optional), start_line (optional), end_line (optional),
        layer (optional), properties (dict), edges_out (list), edges_in (list).

    Returns
    -------
    Dict with keys: name, kind, properties (list of tuples), edges_out, edges_in.
    """
    props: list[tuple[str, str]] = []

    fqn = detail.get("fqn")
    if fqn:
        props.append(("FQN", fqn))

    module = detail.get("module")
    if module:
        props.append(("Module", module))

    file_path = detail.get("file_path")
    start_line = detail.get("start_line")
    end_line = detail.get("end_line")
    if file_path:
        location = file_path
        if start_line is not None and end_line is not None:
            location = f"{file_path}:{start_line}-{end_line}"
        elif start_line is not None:
            location = f"{file_path}:{start_line}"
        props.append(("Location", location))

    layer = detail.get("layer")
    if layer:
        props.append(("Layer", layer))

    # Append all custom properties from the properties dict
    for key, value in detail.get("properties", {}).items():
        props.append((key, str(value)))

    return {
        "name": detail["name"],
        "kind": detail["kind"],
        "properties": props,
        "edges_out": detail.get("edges_out", []),
        "edges_in": detail.get("edges_in", []),
    }
