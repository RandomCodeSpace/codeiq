# Explorer UI — Design Spec

**Date:** 2026-03-28
**Status:** Approved
**UI Framework:** NiceGUI (Python, mounted on existing FastAPI via `ui.run_with`)

## Overview

A progressive drill-down code explorer UI for OSSCodeIQ's `serve` command. Built with NiceGUI — entire UI written in Python, no JS/HTML authoring. Includes an MCP tool console for interactive queries.

**Principles:**
- No full graph loading — each drill-down fetches only what's needed
- Consistent card behavior at every level
- Client-side search on visible cards
- All text copyable
- Light, dark, and system theme support
- Animations and transitions for a polished feel
- UI is for exploration; MCP handles triage

## Technology Stack

- **NiceGUI** — Python UI framework built on Quasar (Vue) + Tailwind CSS
- **Integration** — `ui.run_with(app)` mounts on existing FastAPI app; REST API + MCP + UI all on same port
- **Theme** — `dark=None` for auto/system theme; runtime toggle via `ui.dark_mode()` (light/dark/system)
- **Components used:** `ui.card`, `ui.dialog`, `ui.tabs`, `ui.grid`, `ui.xterm`, `ui.expansion`, Quasar transitions
- **New dependency:** `nicegui>=3.9` added to pyproject.toml core deps

## Navigation Hierarchy

```
Node Kinds (top level)
  → Individual Nodes (paginated, 50 per page)
    → Node Detail (modal)
```

Two levels of cards, plus a detail modal. Simple, flat, predictable.

### Level 1: Node Kinds

Top level shows one card per `NodeKind` that has nodes in the graph.

Each card shows:
- Kind name and icon
- Total node count
- Preview: first 3-5 node labels as preview items

Example cards: Endpoints (342), Entities (89), Classes (1,200), Configs (156), Guards (12), Queues (8)

### Level 2: Individual Nodes

Clicking "Explore" on a kind card shows all nodes of that kind.

Each card shows:
- Node label (e.g., `GET /api/users`)
- Module (if set)
- File path
- Edge count
- Key properties from the node's properties dict (framework, http_method, etc.)

Paginated: 50 cards per page, page navigation at bottom.

### Detail Modal

Clicking "Details" on any card (at any level) opens a `ui.dialog()` modal showing:
- Name and kind
- Layer tags (backend, infra, frontend, etc.)
- Properties table (all key-value pairs)
- Relationships list (edge kind + direction + target label)
- Source location (file path, line number)
- All text is selectable/copyable

## Card Behavior (Consistent at Every Level)

| Element | Action |
|---|---|
| Card body | No action (prevents accidental navigation) |
| "Explore" button | Replaces grid with children cards. Disabled on leaf level. |
| "Details" button | Opens `ui.dialog()` modal with full node info. Available at every level. |

## Breadcrumb Navigation

- Shows current path: `Home > Endpoints > GET /api/users`
- Each segment is clickable — navigates back to that level
- Root is "Home" (shows all kinds)
- Implemented with Quasar breadcrumb component or custom `ui.row` with links

## Search

- `ui.input` with search icon above the card grid
- Client-side filtering on currently visible cards only
- Matches against: card title, subtitle, preview item text
- As-you-type: non-matching cards hide with transition
- No API call
- Search state resets when navigating to a new level

## Pagination

- Default page size: 50 cards
- Page controls at bottom of card grid
- Shows: "Showing 1-50 of 1,200" with Previous/Next buttons
- Server-side pagination via `?limit=50&offset=0`

## MCP Tool Console

An interactive terminal for executing MCP tools directly from the UI.

- **Component:** `ui.xterm` (xterm.js wrapper) in a dedicated tab/panel
- **Access:** Tab in the main navigation — "Explorer" | "Flow" | "MCP Console"
- **Functionality:**
  - List available MCP tools
  - Execute tools with parameters (e.g., `search_graph query="auth"`)
  - Display JSON results in the terminal
  - Command history (up/down arrow)
- **Backend:** Python handler receives terminal input, calls the MCP tool functions directly (same `CodeIQService` instance), returns JSON output
- **Not a full shell** — only MCP tool commands, not arbitrary system commands

## Theming

### Three Modes
- **Light** — clean white/gray surfaces
- **Dark** — dark surfaces matching current flow visualization aesthetic
- **System** — auto-detect via `dark=None`, follows OS preference

### Runtime Toggle
- Theme switcher in header (sun/moon/auto icon)
- Uses `ui.dark_mode()` with bound value
- Persists choice in `ui.storage.browser` (requires `storage_secret`)

### Styling Approach
- Tailwind CSS utilities for layout and spacing (built into NiceGUI)
- Quasar component props for component-specific styling
- Custom CSS via `ui.add_head_html()` for animations and brand colors
- Brand color: `#6366f1` (indigo, matching existing theme)

## Animations

NiceGUI renders in the browser via Quasar/Vue, so CSS and Vue transitions work:

- **Card entrance:** Staggered fade-in when cards load (CSS `@keyframes` + `animation-delay`)
- **Drill-down transition:** Cards fade out, new cards slide in from right
- **Modal:** Quasar's built-in dialog transition (scale + fade)
- **Theme toggle:** Smooth color transition via `transition: background-color 0.3s, color 0.3s` on body
- **Hover effects:** Subtle border glow and translateY on cards
- **Search filter:** Cards that don't match fade out with opacity transition

All animations via CSS injected through `ui.add_head_html()` — no server round-trip needed for visual effects.

## Page Structure

```
┌─────────────────────────────────────────────────────┐
│ Header: Logo | Project Name | Stats | Theme Toggle  │
├─────────────────────────────────────────────────────┤
│ Tabs: [Explorer] [Flow Diagrams] [MCP Console]      │
├─────────────────────────────────────────────────────┤
│ Breadcrumb: Home > Endpoints                         │
├─────────────────────────────────────────────────────┤
│ Search: [🔍 Search nodes...]                         │
├─────────────────────────────────────────────────────┤
│                                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │ Card     │ │ Card     │ │ Card     │            │
│  │ preview  │ │ preview  │ │ preview  │            │
│  │          │ │          │ │          │            │
│  │[Explore] │ │[Explore] │ │[Explore] │            │
│  │[Details] │ │[Details] │ │[Details] │            │
│  └──────────┘ └──────────┘ └──────────┘            │
│                                                      │
│  Showing 1-50 of 342  [< Prev] [Next >]            │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## API Changes

### New Endpoints

**`GET /api/kinds`**

Returns all node kinds with counts and preview nodes.

```json
{
  "kinds": [
    {
      "kind": "endpoint",
      "count": 342,
      "preview": [
        {"id": "ep:...", "label": "GET /api/users"},
        {"id": "ep:...", "label": "POST /api/orders"},
        {"id": "ep:...", "label": "DELETE /api/users/{id}"}
      ]
    },
    {
      "kind": "entity",
      "count": 89,
      "preview": [
        {"id": "ent:...", "label": "Order"},
        {"id": "ent:...", "label": "User"},
        {"id": "ent:...", "label": "Payment"}
      ]
    }
  ],
  "total_nodes": 12847,
  "total_edges": 18392
}
```

**`GET /api/kinds/{kind}?limit=50&offset=0`**

Returns nodes of a specific kind, paginated.

```json
{
  "kind": "endpoint",
  "total": 342,
  "limit": 50,
  "offset": 0,
  "nodes": [
    {
      "id": "ep:src/main/java/.../UserController.java:GET:/api/users",
      "label": "GET /api/users",
      "module": "users",
      "file_path": "src/main/java/com/app/users/UserController.java",
      "line_start": 45,
      "edge_count": 5,
      "properties": {
        "http_method": "GET",
        "framework": "spring"
      }
    }
  ]
}
```

**`GET /api/nodes/{id}` (already exists — enhance response)**

Add edge details to the existing node response:

```json
{
  "node": {
    "id": "...",
    "kind": "endpoint",
    "label": "GET /api/users",
    "fqn": "com.app.users.UserController.getUsers",
    "module": "users",
    "file_path": "src/main/java/com/app/users/UserController.java",
    "line_start": 45,
    "line_end": 62,
    "layer": "backend",
    "properties": { "http_method": "GET", "framework": "spring" }
  },
  "edges_out": [
    {"kind": "calls", "target_id": "...", "target_label": "UserService.findAll", "label": ""}
  ],
  "edges_in": [
    {"kind": "protects", "source_id": "...", "source_label": "JwtGuard", "label": ""}
  ]
}
```

## Frontend Architecture (NiceGUI)

### Integration with Existing Server

```python
# In server/app.py
from nicegui import ui

def create_app(...) -> FastAPI:
    app = FastAPI(...)
    # ... existing REST + MCP setup ...

    # NiceGUI pages
    from osscodeiq.server.ui import setup_ui
    setup_ui(service)

    # Mount NiceGUI on existing app
    ui.run_with(app, dark=None, title="OSSCodeIQ", storage_secret="osscodeiq")
    return app
```

### File Structure

```
server/
  app.py              # Modified — adds ui.run_with()
  routes.py           # Modified — add /api/kinds, /api/kinds/{kind}
  service.py          # Modified — add list_kinds(), nodes_by_kind_paginated(), node_detail_with_edges()
  mcp_server.py       # No change
  ui/
    __init__.py       # setup_ui() entry point
    explorer.py       # Explorer page — card grid, drill-down, search, pagination
    flow_view.py      # Flow diagrams page (wraps existing Cytoscape)
    mcp_console.py    # MCP tool console page
    components.py     # Shared components — node_card(), detail_modal(), breadcrumb()
    theme.py          # Theme config, CSS animations, brand colors
```

### State Management

NiceGUI manages state server-side per session:
- `current_level`: "kinds" | "nodes"
- `current_kind`: None | str
- `breadcrumb_path`: list of dicts
- `search_query`: str (bound to input)
- `page_offset`: int
- `page_limit`: int (50)

### Data Flow

```
User clicks "Explore" on Endpoints card
  → Python handler: service.nodes_by_kind_paginated("endpoint", limit=50, offset=0)
  → Clear card grid container, render new cards
  → Update breadcrumb
  → Animate transition via CSS class toggle

User clicks "Details" on GET /api/users card
  → Python handler: service.node_detail_with_edges(node_id)
  → Populate and open ui.dialog()

User types in search box
  → Client-side: Quasar/Vue binding filters visible cards (no server round-trip for filtering)
```

## File Changes Summary

| File | Change |
|---|---|
| `pyproject.toml` | Add `nicegui>=2.0` to dependencies |
| `server/app.py` | Add `ui.run_with()`, import UI setup |
| `server/routes.py` | Add `GET /api/kinds`, `GET /api/kinds/{kind}` |
| `server/service.py` | Add `list_kinds()`, `nodes_by_kind_paginated()`, `node_detail_with_edges()` |
| `server/ui/__init__.py` | New — `setup_ui()` entry point |
| `server/ui/explorer.py` | New — explorer page with cards, drill-down, search, pagination |
| `server/ui/flow_view.py` | New — flow diagrams tab (wraps existing Cytoscape) |
| `server/ui/mcp_console.py` | New — MCP tool console with xterm |
| `server/ui/components.py` | New — shared card, modal, breadcrumb components |
| `server/ui/theme.py` | New — theme config, CSS animations |
| `server/templates/welcome.html` | Removed — replaced by NiceGUI explorer |

## What This Does NOT Change

- REST API endpoints (existing ones stay, new ones added)
- MCP server (unchanged, same tools)
- Flow engine internals (flow_view.py wraps existing FlowEngine output)
- Analysis pipeline
- All existing tests must pass
