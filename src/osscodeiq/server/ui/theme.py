"""Theme constants and helpers for the OSSCodeIQ Explorer UI."""

from __future__ import annotations

BRAND_COLOR = "#6366f1"
DEFAULT_COLOR = "#6366f1"

KIND_ICONS: dict[str, str] = {
    "endpoint": "api",
    "entity": "storage",
    "class": "code",
    "method": "functions",
    "module": "inventory_2",
    "package": "folder_zip",
    "repository": "source",
    "query": "manage_search",
    "topic": "forum",
    "queue": "queue",
    "event": "bolt",
    "config_file": "settings",
    "config_key": "vpn_key",
    "component": "widgets",
    "guard": "shield",
    "middleware": "layers",
    "hook": "webhook",
    "infra_resource": "cloud",
    "database_connection": "database",
    "interface": "share",
    "abstract_class": "architecture",
    "enum": "format_list_numbered",
    "migration": "upgrade",
    "rmi_interface": "swap_horiz",
    "websocket_endpoint": "sync_alt",
    "annotation_type": "label",
    "protocol_message": "mail",
    "config_definition": "tune",
    "azure_resource": "cloud_queue",
    "azure_function": "cloud_circle",
    "message_queue": "message",
}

KIND_COLORS: dict[str, str] = {
    "endpoint": "#06b6d4",
    "entity": "#8b5cf6",
    "class": "#f59e0b",
    "method": "#10b981",
    "module": "#3b82f6",
    "package": "#6366f1",
    "repository": "#ec4899",
    "query": "#14b8a6",
    "topic": "#f97316",
    "queue": "#a855f7",
    "event": "#ef4444",
    "config_file": "#64748b",
    "config_key": "#94a3b8",
    "component": "#22d3ee",
    "guard": "#e11d48",
    "middleware": "#7c3aed",
    "hook": "#84cc16",
    "infra_resource": "#0ea5e9",
    "database_connection": "#d946ef",
    "interface": "#2dd4bf",
    "abstract_class": "#fbbf24",
    "enum": "#fb923c",
    "migration": "#78716c",
    "rmi_interface": "#4ade80",
    "websocket_endpoint": "#38bdf8",
    "annotation_type": "#c084fc",
    "protocol_message": "#f472b6",
    "config_definition": "#a78bfa",
    "azure_resource": "#60a5fa",
    "azure_function": "#818cf8",
    "message_queue": "#c084fc",
}


def get_kind_color(kind: str) -> str:
    """Return the hex color for a node kind, falling back to DEFAULT_COLOR."""
    return KIND_COLORS.get(kind, DEFAULT_COLOR)


def get_kind_icon(kind: str) -> str:
    """Return the Material icon name for a node kind, falling back to 'circle'."""
    return KIND_ICONS.get(kind, "circle")


def get_animation_css() -> str:
    """Return CSS string with keyframe animations for the Explorer UI."""
    staggered = "\n".join(
        f".card-animate-{i} {{ animation-delay: {i * 0.05:.2f}s; }}"
        for i in range(1, 11)
    )
    return f"""
@keyframes fadeInUp {{
    from {{
        opacity: 0;
        transform: translateY(16px);
    }}
    to {{
        opacity: 1;
        transform: translateY(0);
    }}
}}

@keyframes fadeIn {{
    from {{ opacity: 0; }}
    to {{ opacity: 1; }}
}}

.card-animate {{
    animation: fadeInUp 0.35s ease-out both;
}}

{staggered}

.search-fade-out {{
    animation: fadeIn 0.2s ease-out reverse both;
}}

.search-fade-in {{
    animation: fadeIn 0.2s ease-out both;
}}
"""
