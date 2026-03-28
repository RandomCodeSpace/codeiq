# Explorer UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a NiceGUI-powered progressive drill-down explorer UI with MCP console, integrated into the existing FastAPI server.

**Architecture:** NiceGUI mounted on existing FastAPI via `ui.run_with()`. Three tabs: Explorer (card grid drill-down), Flow (wraps existing Cytoscape), MCP Console (xterm terminal). New API endpoints for kind-based queries. All UI logic in Python.

**Tech Stack:** NiceGUI 3.9+, FastAPI, Pydantic, Quasar (via NiceGUI), Tailwind CSS (via NiceGUI), xterm.js (via ui.xterm)

---

### Task 1: Add NiceGUI Dependency

**Files:**
- Modify: `pyproject.toml`
- Run: `uv lock`

- [ ] **Step 1: Add nicegui to pyproject.toml dependencies**

In `pyproject.toml`, add `"nicegui>=3.9"` to the `dependencies` list under `[project]`:

```toml
"nicegui>=3.9",
```

- [ ] **Step 2: Run uv lock**

Run: `uv lock`
Expected: Lock file regenerated without errors.

- [ ] **Step 3: Install in dev mode**

Run: `uv pip install -e ".[dev]"`
Expected: nicegui 3.9.0 installed alongside existing deps.

- [ ] **Step 4: Verify import**

Run: `python -c "import nicegui; print(nicegui.__version__)"`
Expected: `3.9.0`

- [ ] **Step 5: Commit**

```bash
git add pyproject.toml uv.lock
git commit -m "feat: add nicegui>=3.9 dependency for explorer UI"
```

---

### Task 2: Add Service Methods (list_kinds, nodes_by_kind_paginated, node_detail_with_edges)

**Files:**
- Modify: `src/osscodeiq/server/service.py`
- Test: `tests/server/test_service.py`

- [ ] **Step 1: Write failing tests for list_kinds**

Add to `tests/server/test_service.py`:

```python
def test_list_kinds(service_with_data):
    """list_kinds returns node kinds with counts and preview labels."""
    result = service_with_data.list_kinds()
    assert "kinds" in result
    assert "total_nodes" in result
    assert "total_edges" in result
    # Should have at least endpoint and entity kinds from fixture data
    kind_names = [k["kind"] for k in result["kinds"]]
    assert "endpoint" in kind_names
    counts = {k["kind"]: k["count"] for k in result["kinds"]}
    assert counts["endpoint"] > 0
    # Preview should have up to 5 items
    for k in result["kinds"]:
        assert "preview" in k
        assert len(k["preview"]) <= 5
        for p in k["preview"]:
            assert "id" in p
            assert "label" in p
```

- [ ] **Step 2: Write failing tests for nodes_by_kind_paginated**

```python
def test_nodes_by_kind_paginated(service_with_data):
    """nodes_by_kind_paginated returns paginated nodes of a specific kind."""
    result = service_with_data.nodes_by_kind_paginated("endpoint", limit=2, offset=0)
    assert result["kind"] == "endpoint"
    assert result["total"] > 0
    assert result["limit"] == 2
    assert result["offset"] == 0
    assert len(result["nodes"]) <= 2
    for node in result["nodes"]:
        assert "id" in node
        assert "label" in node
        assert "edge_count" in node

def test_nodes_by_kind_paginated_offset(service_with_data):
    """Pagination offset works correctly."""
    all_result = service_with_data.nodes_by_kind_paginated("endpoint", limit=100, offset=0)
    if all_result["total"] > 1:
        offset_result = service_with_data.nodes_by_kind_paginated("endpoint", limit=1, offset=1)
        assert offset_result["nodes"][0]["id"] == all_result["nodes"][1]["id"]

def test_nodes_by_kind_empty(service_with_data):
    """Returns empty list for non-existent kind."""
    result = service_with_data.nodes_by_kind_paginated("nonexistent_kind", limit=50, offset=0)
    assert result["total"] == 0
    assert result["nodes"] == []
```

- [ ] **Step 3: Write failing tests for node_detail_with_edges**

```python
def test_node_detail_with_edges(service_with_data):
    """node_detail_with_edges returns full node with incoming and outgoing edges."""
    # Use a known node ID from fixture
    nodes = service_with_data.store.all_nodes()
    node_id = nodes[0].id
    result = service_with_data.node_detail_with_edges(node_id)
    assert "node" in result
    assert result["node"]["id"] == node_id
    assert "edges_out" in result
    assert "edges_in" in result
    assert isinstance(result["edges_out"], list)
    assert isinstance(result["edges_in"], list)

def test_node_detail_not_found(service_with_data):
    """Returns None for non-existent node."""
    result = service_with_data.node_detail_with_edges("nonexistent:id")
    assert result is None
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `pytest tests/server/test_service.py -v -k "list_kinds or nodes_by_kind or node_detail"`
Expected: FAIL — methods don't exist yet.

- [ ] **Step 5: Implement list_kinds in service.py**

Add to `CodeIQService` class:

```python
def list_kinds(self) -> dict:
    """Return all node kinds with counts and preview labels."""
    from collections import Counter
    all_nodes = self.store.all_nodes()
    kind_counts: Counter[str] = Counter()
    kind_previews: dict[str, list[dict]] = {}
    for node in all_nodes:
        kv = node.kind.value
        kind_counts[kv] += 1
        if kv not in kind_previews:
            kind_previews[kv] = []
        if len(kind_previews[kv]) < 5:
            kind_previews[kv].append({"id": node.id, "label": node.label})
    kinds = sorted(
        [
            {"kind": k, "count": c, "preview": kind_previews.get(k, [])}
            for k, c in kind_counts.items()
        ],
        key=lambda x: x["count"],
        reverse=True,
    )
    return {
        "kinds": kinds,
        "total_nodes": self.store.node_count,
        "total_edges": self.store.edge_count,
    }
```

- [ ] **Step 6: Implement nodes_by_kind_paginated in service.py**

```python
def nodes_by_kind_paginated(self, kind: str, limit: int = 50, offset: int = 0) -> dict:
    """Return paginated nodes of a specific kind with edge counts."""
    from osscodeiq.models.graph import NodeKind
    try:
        node_kind = NodeKind(kind)
    except ValueError:
        return {"kind": kind, "total": 0, "limit": limit, "offset": offset, "nodes": []}
    all_of_kind = self.store.nodes_by_kind(node_kind)
    total = len(all_of_kind)
    page = all_of_kind[offset : offset + limit]
    all_edges = self.store.all_edges()
    edge_counts: dict[str, int] = {}
    for e in all_edges:
        edge_counts[e.source] = edge_counts.get(e.source, 0) + 1
        edge_counts[e.target] = edge_counts.get(e.target, 0) + 1
    nodes = []
    for n in page:
        loc = n.location
        nodes.append({
            "id": n.id,
            "label": n.label,
            "module": n.module,
            "file_path": loc.file_path if loc else None,
            "line_start": loc.line_start if loc else None,
            "edge_count": edge_counts.get(n.id, 0),
            "properties": n.properties,
        })
    return {"kind": kind, "total": total, "limit": limit, "offset": offset, "nodes": nodes}
```

- [ ] **Step 7: Implement node_detail_with_edges in service.py**

```python
def node_detail_with_edges(self, node_id: str) -> dict | None:
    """Return full node detail with incoming and outgoing edges."""
    node = self.store.get_node(node_id)
    if node is None:
        return None
    loc = node.location
    node_data = {
        "id": node.id,
        "kind": node.kind.value,
        "label": node.label,
        "fqn": node.fqn,
        "module": node.module,
        "file_path": loc.file_path if loc else None,
        "line_start": loc.line_start if loc else None,
        "line_end": loc.line_end if loc else None,
        "layer": node.properties.get("layer"),
        "properties": node.properties,
    }
    all_edges = self.store.all_edges()
    edges_out = []
    edges_in = []
    for e in all_edges:
        if e.source == node_id:
            target = self.store.get_node(e.target)
            edges_out.append({
                "kind": e.kind.value,
                "target_id": e.target,
                "target_label": target.label if target else e.target,
                "label": e.label or "",
            })
        elif e.target == node_id:
            source = self.store.get_node(e.source)
            edges_in.append({
                "kind": e.kind.value,
                "source_id": e.source,
                "source_label": source.label if source else e.source,
                "label": e.label or "",
            })
    return {"node": node_data, "edges_out": edges_out, "edges_in": edges_in}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `pytest tests/server/test_service.py -v -k "list_kinds or nodes_by_kind or node_detail"`
Expected: All PASS.

- [ ] **Step 9: Commit**

```bash
git add src/osscodeiq/server/service.py tests/server/test_service.py
git commit -m "feat: add list_kinds, nodes_by_kind_paginated, node_detail_with_edges to service"
```

---

### Task 3: Add API Routes (/api/kinds, /api/kinds/{kind}, enhance /api/nodes/{id})

**Files:**
- Modify: `src/osscodeiq/server/routes.py`
- Test: `tests/server/test_routes.py`

- [ ] **Step 1: Write failing tests for /api/kinds**

Add to `tests/server/test_routes.py`:

```python
def test_get_kinds(client):
    response = client.get("/api/kinds")
    assert response.status_code == 200
    data = response.json()
    assert "kinds" in data
    assert "total_nodes" in data
    assert "total_edges" in data
    assert isinstance(data["kinds"], list)
    for k in data["kinds"]:
        assert "kind" in k
        assert "count" in k
        assert "preview" in k
```

- [ ] **Step 2: Write failing tests for /api/kinds/{kind}**

```python
def test_get_nodes_by_kind(client):
    response = client.get("/api/kinds/endpoint?limit=10&offset=0")
    assert response.status_code == 200
    data = response.json()
    assert data["kind"] == "endpoint"
    assert "total" in data
    assert "nodes" in data
    assert data["limit"] == 10
    assert data["offset"] == 0

def test_get_nodes_by_kind_pagination(client):
    response = client.get("/api/kinds/endpoint?limit=1&offset=0")
    assert response.status_code == 200
    data = response.json()
    assert len(data["nodes"]) <= 1

def test_get_nodes_by_kind_empty(client):
    response = client.get("/api/kinds/nonexistent_kind")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 0
```

- [ ] **Step 3: Write failing test for enhanced /api/nodes/{id}**

```python
def test_get_node_detail_with_edges(client):
    """GET /api/nodes/{id}/detail returns node with edges."""
    response = client.get("/api/nodes/ep1/detail")
    assert response.status_code == 200
    data = response.json()
    assert "node" in data
    assert "edges_out" in data
    assert "edges_in" in data
    assert data["node"]["id"] == "ep1"

def test_get_node_detail_not_found(client):
    response = client.get("/api/nodes/nonexistent/detail")
    assert response.status_code == 404
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `pytest tests/server/test_routes.py -v -k "kinds or node_detail"`
Expected: FAIL — routes don't exist.

- [ ] **Step 5: Add routes to routes.py**

Add these routes to `create_router()` in `routes.py`:

```python
@router.get("/kinds")
async def get_kinds():
    return service.list_kinds()

@router.get("/kinds/{kind}")
async def get_nodes_by_kind(
    kind: str,
    limit: int = 50,
    offset: int = 0,
):
    return service.nodes_by_kind_paginated(kind, limit=limit, offset=offset)
```

Add the detail route BEFORE the existing `{node_id:path}` route:

```python
@router.get("/nodes/{node_id:path}/detail")
async def get_node_detail(node_id: str):
    result = service.node_detail_with_edges(node_id)
    if result is None:
        raise HTTPException(status_code=404, detail="Node not found")
    return result
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `pytest tests/server/test_routes.py -v -k "kinds or node_detail"`
Expected: All PASS.

- [ ] **Step 7: Run full route tests**

Run: `pytest tests/server/test_routes.py -v`
Expected: All existing + new tests PASS.

- [ ] **Step 8: Commit**

```bash
git add src/osscodeiq/server/routes.py tests/server/test_routes.py
git commit -m "feat: add /api/kinds, /api/kinds/{kind}, /api/nodes/{id}/detail endpoints"
```

---

### Task 4: Create Theme Module

**Files:**
- Create: `src/osscodeiq/server/ui/__init__.py`
- Create: `src/osscodeiq/server/ui/theme.py`
- Test: `tests/server/test_ui_theme.py`

- [ ] **Step 1: Create server/ui package**

Create `src/osscodeiq/server/ui/__init__.py`:

```python
"""NiceGUI-based web UI for OSSCodeIQ."""
```

- [ ] **Step 2: Write failing test for theme**

Create `tests/server/test_ui_theme.py`:

```python
from osscodeiq.server.ui.theme import BRAND_COLOR, KIND_ICONS, get_kind_color, get_animation_css


def test_brand_color_is_string():
    assert isinstance(BRAND_COLOR, str)
    assert BRAND_COLOR.startswith("#")


def test_kind_icons_covers_common_kinds():
    assert "endpoint" in KIND_ICONS
    assert "entity" in KIND_ICONS
    assert "class" in KIND_ICONS


def test_get_kind_color_returns_string():
    color = get_kind_color("endpoint")
    assert isinstance(color, str)
    assert color.startswith("#")


def test_get_kind_color_fallback():
    color = get_kind_color("unknown_kind_xyz")
    assert isinstance(color, str)


def test_get_animation_css_returns_string():
    css = get_animation_css()
    assert isinstance(css, str)
    assert "@keyframes" in css
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pytest tests/server/test_ui_theme.py -v`
Expected: FAIL — module doesn't exist.

- [ ] **Step 4: Implement theme.py**

Create `src/osscodeiq/server/ui/theme.py`:

```python
"""Theme configuration, colors, icons, and CSS animations for OSSCodeIQ UI."""

from __future__ import annotations

BRAND_COLOR = "#6366f1"

KIND_ICONS = {
    "endpoint": "api",
    "entity": "storage",
    "class": "code",
    "method": "functions",
    "module": "folder",
    "package": "inventory_2",
    "repository": "database",
    "query": "manage_search",
    "migration": "move_up",
    "topic": "forum",
    "queue": "queue",
    "event": "bolt",
    "config_file": "settings",
    "config_key": "key",
    "config_definition": "tune",
    "interface": "device_hub",
    "abstract_class": "category",
    "enum": "list",
    "component": "widgets",
    "guard": "shield",
    "middleware": "filter_alt",
    "hook": "webhook",
    "infra_resource": "cloud",
    "database_connection": "cable",
    "protocol_message": "mail",
    "websocket_endpoint": "sync_alt",
    "rmi_interface": "swap_horiz",
    "annotation_type": "label",
    "message_queue": "mark_as_unread",
    "azure_resource": "cloud_circle",
    "azure_function": "cloud_sync",
}

KIND_COLORS: dict[str, str] = {
    "endpoint": "#06b6d4",
    "entity": "#8b5cf6",
    "class": "#6366f1",
    "method": "#3b82f6",
    "module": "#f59e0b",
    "package": "#f97316",
    "repository": "#8b5cf6",
    "query": "#a855f7",
    "topic": "#ec4899",
    "queue": "#ec4899",
    "event": "#ef4444",
    "config_file": "#64748b",
    "config_key": "#64748b",
    "component": "#10b981",
    "guard": "#f59e0b",
    "middleware": "#f97316",
    "hook": "#22c55e",
    "infra_resource": "#7c3aed",
    "database_connection": "#06b6d4",
    "interface": "#2563eb",
    "abstract_class": "#2563eb",
    "enum": "#64748b",
}

DEFAULT_COLOR = "#6366f1"


def get_kind_color(kind: str) -> str:
    """Return the display color for a node kind."""
    return KIND_COLORS.get(kind, DEFAULT_COLOR)


def get_kind_icon(kind: str) -> str:
    """Return the Material icon name for a node kind."""
    return KIND_ICONS.get(kind, "circle")


def get_animation_css() -> str:
    """Return CSS for card animations and transitions."""
    return """
    @keyframes fadeInUp {
        from { opacity: 0; transform: translateY(12px); }
        to { opacity: 1; transform: translateY(0); }
    }
    @keyframes fadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
    }
    .card-animate {
        animation: fadeInUp 0.3s ease-out both;
    }
    .card-animate:nth-child(1) { animation-delay: 0.02s; }
    .card-animate:nth-child(2) { animation-delay: 0.04s; }
    .card-animate:nth-child(3) { animation-delay: 0.06s; }
    .card-animate:nth-child(4) { animation-delay: 0.08s; }
    .card-animate:nth-child(5) { animation-delay: 0.10s; }
    .card-animate:nth-child(6) { animation-delay: 0.12s; }
    .card-animate:nth-child(7) { animation-delay: 0.14s; }
    .card-animate:nth-child(8) { animation-delay: 0.16s; }
    .card-animate:nth-child(9) { animation-delay: 0.18s; }
    .card-animate:nth-child(10) { animation-delay: 0.20s; }
    .search-fade-out { opacity: 0.15; transition: opacity 0.2s ease; }
    .search-fade-in { opacity: 1; transition: opacity 0.2s ease; }
    """
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `pytest tests/server/test_ui_theme.py -v`
Expected: All PASS.

- [ ] **Step 6: Commit**

```bash
git add src/osscodeiq/server/ui/__init__.py src/osscodeiq/server/ui/theme.py tests/server/test_ui_theme.py
git commit -m "feat: add UI theme module with colors, icons, and animations"
```

---

### Task 5: Create Components Module (shared card, modal, breadcrumb)

**Files:**
- Create: `src/osscodeiq/server/ui/components.py`
- Test: `tests/server/test_ui_components.py`

- [ ] **Step 1: Write failing tests**

Create `tests/server/test_ui_components.py`:

```python
from osscodeiq.server.ui.components import build_kind_card_data, build_node_card_data, build_detail_data


def test_build_kind_card_data():
    kind_info = {"kind": "endpoint", "count": 42, "preview": [{"id": "e1", "label": "GET /api"}]}
    data = build_kind_card_data(kind_info)
    assert data["title"] == "endpoint"
    assert data["count"] == 42
    assert data["icon"] is not None
    assert data["color"] is not None
    assert len(data["preview"]) == 1


def test_build_node_card_data():
    node_info = {
        "id": "ep:test",
        "label": "GET /api/users",
        "module": "users",
        "file_path": "src/users.py",
        "line_start": 10,
        "edge_count": 3,
        "properties": {"framework": "fastapi"},
    }
    data = build_node_card_data(node_info)
    assert data["title"] == "GET /api/users"
    assert data["subtitle"] is not None
    assert "id" in data


def test_build_detail_data():
    detail = {
        "node": {
            "id": "ep:test",
            "kind": "endpoint",
            "label": "GET /api",
            "fqn": None,
            "module": "auth",
            "file_path": "src/auth.py",
            "line_start": 10,
            "line_end": 20,
            "layer": "backend",
            "properties": {"framework": "fastapi"},
        },
        "edges_out": [{"kind": "calls", "target_id": "t1", "target_label": "Svc", "label": ""}],
        "edges_in": [{"kind": "protects", "source_id": "s1", "source_label": "Guard", "label": ""}],
    }
    data = build_detail_data(detail)
    assert data["name"] == "GET /api"
    assert data["kind"] == "endpoint"
    assert len(data["properties"]) > 0
    assert len(data["edges_out"]) == 1
    assert len(data["edges_in"]) == 1
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tests/server/test_ui_components.py -v`
Expected: FAIL.

- [ ] **Step 3: Implement components.py**

Create `src/osscodeiq/server/ui/components.py`:

```python
"""Shared data helpers for building UI components."""

from __future__ import annotations

from osscodeiq.server.ui.theme import get_kind_color, get_kind_icon


def build_kind_card_data(kind_info: dict) -> dict:
    """Transform a kind summary dict into card display data."""
    kind = kind_info["kind"]
    return {
        "kind": kind,
        "title": kind,
        "count": kind_info["count"],
        "icon": get_kind_icon(kind),
        "color": get_kind_color(kind),
        "preview": kind_info.get("preview", []),
    }


def build_node_card_data(node_info: dict) -> dict:
    """Transform a node summary dict into card display data."""
    parts = []
    if node_info.get("module"):
        parts.append(node_info["module"])
    if node_info.get("file_path"):
        parts.append(node_info["file_path"])
    subtitle = " · ".join(parts) if parts else ""
    edge_count = node_info.get("edge_count", 0)
    if edge_count:
        subtitle += f" · {edge_count} edges"
    return {
        "id": node_info["id"],
        "title": node_info["label"],
        "subtitle": subtitle,
        "module": node_info.get("module"),
        "properties": node_info.get("properties", {}),
    }


def build_detail_data(detail: dict) -> dict:
    """Transform a node detail response into modal display data."""
    node = detail["node"]
    properties = []
    if node.get("fqn"):
        properties.append(("FQN", node["fqn"]))
    if node.get("module"):
        properties.append(("Module", node["module"]))
    if node.get("file_path"):
        loc = node["file_path"]
        if node.get("line_start"):
            loc += f":{node['line_start']}"
            if node.get("line_end"):
                loc += f"-{node['line_end']}"
        properties.append(("Location", loc))
    if node.get("layer"):
        properties.append(("Layer", node["layer"]))
    for k, v in node.get("properties", {}).items():
        if k != "layer":
            properties.append((k.replace("_", " ").title(), str(v)))
    return {
        "name": node["label"],
        "kind": node["kind"],
        "properties": properties,
        "edges_out": detail.get("edges_out", []),
        "edges_in": detail.get("edges_in", []),
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest tests/server/test_ui_components.py -v`
Expected: All PASS.

- [ ] **Step 5: Commit**

```bash
git add src/osscodeiq/server/ui/components.py tests/server/test_ui_components.py
git commit -m "feat: add shared UI component data helpers"
```

---

### Task 6: Create Explorer Page

**Files:**
- Create: `src/osscodeiq/server/ui/explorer.py`
- Test: `tests/server/test_ui_explorer.py`

- [ ] **Step 1: Write test for explorer page setup**

Create `tests/server/test_ui_explorer.py`:

```python
"""Tests for the explorer UI page module."""

from osscodeiq.server.ui.explorer import ExplorerState


def test_explorer_state_initial():
    state = ExplorerState()
    assert state.level == "kinds"
    assert state.current_kind is None
    assert state.breadcrumb == [{"label": "Home", "level": "kinds", "kind": None}]
    assert state.page_offset == 0
    assert state.page_limit == 50


def test_explorer_state_drill_down():
    state = ExplorerState()
    state.drill_down("endpoint")
    assert state.level == "nodes"
    assert state.current_kind == "endpoint"
    assert len(state.breadcrumb) == 2
    assert state.breadcrumb[1]["label"] == "endpoint"
    assert state.page_offset == 0


def test_explorer_state_go_home():
    state = ExplorerState()
    state.drill_down("endpoint")
    state.go_home()
    assert state.level == "kinds"
    assert state.current_kind is None
    assert len(state.breadcrumb) == 1


def test_explorer_state_navigate_breadcrumb():
    state = ExplorerState()
    state.drill_down("endpoint")
    state.navigate_to(0)
    assert state.level == "kinds"
    assert state.current_kind is None
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tests/server/test_ui_explorer.py -v`
Expected: FAIL.

- [ ] **Step 3: Implement explorer.py**

Create `src/osscodeiq/server/ui/explorer.py`:

```python
"""Explorer page — progressive drill-down card grid."""

from __future__ import annotations

from dataclasses import dataclass, field

from nicegui import ui

from osscodeiq.server.ui.components import build_detail_data, build_kind_card_data, build_node_card_data
from osscodeiq.server.ui.theme import get_animation_css, get_kind_color, get_kind_icon


@dataclass
class ExplorerState:
    """Tracks the current navigation state for the explorer."""

    level: str = "kinds"
    current_kind: str | None = None
    breadcrumb: list[dict] = field(default_factory=lambda: [{"label": "Home", "level": "kinds", "kind": None}])
    page_offset: int = 0
    page_limit: int = 50

    def drill_down(self, kind: str) -> None:
        self.level = "nodes"
        self.current_kind = kind
        self.breadcrumb.append({"label": kind, "level": "nodes", "kind": kind})
        self.page_offset = 0

    def go_home(self) -> None:
        self.level = "kinds"
        self.current_kind = None
        self.breadcrumb = [{"label": "Home", "level": "kinds", "kind": None}]
        self.page_offset = 0

    def navigate_to(self, index: int) -> None:
        if index == 0:
            self.go_home()
        elif index < len(self.breadcrumb):
            target = self.breadcrumb[index]
            self.breadcrumb = self.breadcrumb[: index + 1]
            self.level = target["level"]
            self.current_kind = target["kind"]
            self.page_offset = 0


def create_explorer_page(service) -> None:
    """Build the explorer tab content. Called inside a NiceGUI page context."""

    state = ExplorerState()
    card_container = None
    breadcrumb_container = None
    pagination_container = None
    search_input = None

    ui.add_head_html(f"<style>{get_animation_css()}</style>")

    # -- Breadcrumb --
    with ui.row().classes("w-full items-center gap-1 px-4 py-2") as bc:
        breadcrumb_container = bc

    # -- Search --
    with ui.row().classes("w-full px-4 py-2"):
        search_input = ui.input(placeholder="Search nodes...").props(
            'outlined dense clearable'
        ).classes("w-full max-w-md").on(
            "update:model-value", lambda e: _filter_cards(e.args, card_container)
        )

    # -- Card grid --
    with ui.element("div").classes("w-full px-4 py-2") as cc:
        card_container = cc

    # -- Pagination --
    with ui.row().classes("w-full items-center justify-center gap-2 px-4 py-4") as pg:
        pagination_container = pg

    def _render_breadcrumb():
        breadcrumb_container.clear()
        with breadcrumb_container:
            for i, crumb in enumerate(state.breadcrumb):
                if i > 0:
                    ui.label("/").classes("text-gray-500 text-sm mx-1")
                if i < len(state.breadcrumb) - 1:
                    idx = i
                    ui.button(crumb["label"], on_click=lambda _, idx=idx: _on_breadcrumb(idx)).props(
                        "flat dense no-caps"
                    ).classes("text-sm")
                else:
                    ui.label(crumb["label"]).classes("text-sm font-bold")

    def _on_breadcrumb(index: int):
        state.navigate_to(index)
        _render()

    def _render():
        _render_breadcrumb()
        if search_input:
            search_input.set_value("")
        if state.level == "kinds":
            _render_kinds()
        else:
            _render_nodes()

    def _render_kinds():
        card_container.clear()
        pagination_container.clear()
        data = service.list_kinds()
        with card_container:
            with ui.grid(columns="repeat(auto-fill, minmax(280px, 1fr))").classes("w-full gap-4"):
                for kind_info in data["kinds"]:
                    card_data = build_kind_card_data(kind_info)
                    _kind_card(card_data)

    def _kind_card(data: dict):
        with ui.card().classes("card-animate").style(
            f"border-left: 3px solid {data['color']}"
        ):
            with ui.card_section():
                with ui.row().classes("items-center gap-2"):
                    ui.icon(data["icon"]).style(f"color: {data['color']}; font-size: 24px")
                    ui.label(data["title"]).classes("text-lg font-bold capitalize")
                ui.label(f"{data['count']} nodes").classes("text-sm text-gray-500")
            if data["preview"]:
                with ui.card_section():
                    for p in data["preview"][:3]:
                        ui.label(p["label"]).classes("text-xs text-gray-400 truncate")
            with ui.card_actions().classes("justify-end"):
                ui.button(
                    "Explore",
                    icon="arrow_forward",
                    on_click=lambda _, k=data["kind"]: _drill_down(k),
                ).props("flat dense no-caps color=primary")
                ui.button(
                    "Details",
                    icon="info",
                    on_click=lambda _, k=data: _show_kind_detail(k),
                ).props("flat dense no-caps")

    def _drill_down(kind: str):
        state.drill_down(kind)
        _render()

    def _render_nodes():
        card_container.clear()
        pagination_container.clear()
        data = service.nodes_by_kind_paginated(
            state.current_kind, limit=state.page_limit, offset=state.page_offset
        )
        with card_container:
            with ui.grid(columns="repeat(auto-fill, minmax(280px, 1fr))").classes("w-full gap-4"):
                for node_info in data["nodes"]:
                    card_data = build_node_card_data(node_info)
                    _node_card(card_data)
        # Pagination
        total = data["total"]
        if total > state.page_limit:
            with pagination_container:
                ui.label(
                    f"Showing {state.page_offset + 1}-{min(state.page_offset + state.page_limit, total)} of {total}"
                ).classes("text-sm text-gray-500")
                ui.button(
                    "Prev",
                    on_click=lambda: _page(-1),
                    icon="chevron_left",
                ).props("flat dense").bind_enabled_from(
                    state, "page_offset", backward=lambda o: o > 0
                )
                ui.button(
                    "Next",
                    on_click=lambda: _page(1),
                    icon="chevron_right",
                ).props("flat dense").bind_enabled_from(
                    state, "page_offset", backward=lambda o: o + state.page_limit < total
                )

    def _page(direction: int):
        state.page_offset += direction * state.page_limit
        state.page_offset = max(0, state.page_offset)
        _render_nodes()

    def _node_card(data: dict):
        kind = state.current_kind or ""
        color = get_kind_color(kind)
        with ui.card().classes("card-animate").style(f"border-left: 3px solid {color}"):
            with ui.card_section():
                ui.label(data["title"]).classes("text-base font-bold truncate")
                if data["subtitle"]:
                    ui.label(data["subtitle"]).classes("text-xs text-gray-400 truncate")
                if data["properties"]:
                    with ui.row().classes("gap-1 mt-1 flex-wrap"):
                        for k, v in list(data["properties"].items())[:3]:
                            ui.badge(f"{k}: {v}").props("outline").classes("text-xs")
            with ui.card_actions().classes("justify-end"):
                ui.button(
                    "Details",
                    icon="info",
                    on_click=lambda _, nid=data["id"]: _show_node_detail(nid),
                ).props("flat dense no-caps")

    def _show_kind_detail(kind_data: dict):
        with ui.dialog() as dialog, ui.card().classes("w-full max-w-lg"):
            with ui.card_section():
                with ui.row().classes("items-center gap-2"):
                    ui.icon(kind_data["icon"]).style(f"color: {kind_data['color']}; font-size: 28px")
                    ui.label(kind_data["title"]).classes("text-xl font-bold capitalize")
                ui.label(f"{kind_data['count']} nodes").classes("text-sm text-gray-500 mt-1")
            with ui.card_section():
                ui.label("Preview Nodes").classes("text-xs text-gray-500 uppercase tracking-wide mb-2")
                for p in kind_data["preview"]:
                    ui.label(p["label"]).classes("text-sm py-1 border-b border-gray-700")
            with ui.card_actions().classes("justify-end"):
                ui.button("Close", on_click=dialog.close).props("flat")
        dialog.open()

    def _show_node_detail(node_id: str):
        detail = service.node_detail_with_edges(node_id)
        if detail is None:
            ui.notify("Node not found", type="warning")
            return
        data = build_detail_data(detail)
        with ui.dialog() as dialog, ui.card().classes("w-full max-w-lg max-h-[80vh] overflow-y-auto"):
            with ui.card_section():
                ui.label(data["name"]).classes("text-xl font-bold select-all")
                ui.label(data["kind"]).classes("text-sm text-indigo-400 capitalize")
            with ui.card_section():
                ui.label("Properties").classes("text-xs text-gray-500 uppercase tracking-wide mb-2")
                for key, val in data["properties"]:
                    with ui.row().classes("justify-between py-1 border-b border-gray-700 w-full"):
                        ui.label(key).classes("text-sm text-gray-400")
                        ui.label(val).classes("text-sm select-all text-right")
            if data["edges_out"]:
                with ui.card_section():
                    ui.label(f"Outgoing ({len(data['edges_out'])})").classes(
                        "text-xs text-gray-500 uppercase tracking-wide mb-2"
                    )
                    for edge in data["edges_out"]:
                        with ui.row().classes("items-center gap-2 py-1"):
                            ui.badge(edge["kind"]).props("color=primary outline").classes("text-xs")
                            ui.label("→").classes("text-gray-500")
                            ui.label(edge["target_label"]).classes("text-sm select-all")
            if data["edges_in"]:
                with ui.card_section():
                    ui.label(f"Incoming ({len(data['edges_in'])})").classes(
                        "text-xs text-gray-500 uppercase tracking-wide mb-2"
                    )
                    for edge in data["edges_in"]:
                        with ui.row().classes("items-center gap-2 py-1"):
                            ui.label(edge["source_label"]).classes("text-sm select-all")
                            ui.label("→").classes("text-gray-500")
                            ui.badge(edge["kind"]).props("color=primary outline").classes("text-xs")
            with ui.card_actions().classes("justify-end"):
                ui.button("Close", on_click=dialog.close).props("flat")
        dialog.open()

    # Initial render
    _render()


def _filter_cards(query, container):
    """Client-side card filtering via JS."""
    if container is None:
        return
    q = (query or "").lower().strip()
    js = f"""
    document.querySelectorAll('[id="{container.id}"] .q-card').forEach(card => {{
        const text = card.textContent.toLowerCase();
        const match = !'{q}' || text.includes('{q}');
        card.style.opacity = match ? '1' : '0.15';
        card.style.transition = 'opacity 0.2s ease';
    }});
    """
    ui.run_javascript(js)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest tests/server/test_ui_explorer.py -v`
Expected: All PASS.

- [ ] **Step 5: Commit**

```bash
git add src/osscodeiq/server/ui/explorer.py tests/server/test_ui_explorer.py
git commit -m "feat: add explorer page with card grid, drill-down, pagination, search"
```

---

### Task 7: Create MCP Console Page

**Files:**
- Create: `src/osscodeiq/server/ui/mcp_console.py`
- Test: `tests/server/test_ui_mcp_console.py`

- [ ] **Step 1: Write failing tests**

Create `tests/server/test_ui_mcp_console.py`:

```python
from osscodeiq.server.ui.mcp_console import parse_mcp_command, MCP_TOOL_NAMES


def test_mcp_tool_names_populated():
    assert len(MCP_TOOL_NAMES) > 0
    assert "get_stats" in MCP_TOOL_NAMES
    assert "search_graph" in MCP_TOOL_NAMES


def test_parse_mcp_command_simple():
    tool, kwargs = parse_mcp_command("get_stats")
    assert tool == "get_stats"
    assert kwargs == {}


def test_parse_mcp_command_with_args():
    tool, kwargs = parse_mcp_command('search_graph query="auth" limit=10')
    assert tool == "search_graph"
    assert kwargs["query"] == "auth"
    assert kwargs["limit"] == "10"


def test_parse_mcp_command_unknown():
    tool, kwargs = parse_mcp_command("not_a_tool")
    assert tool == "not_a_tool"
    assert kwargs == {}


def test_parse_mcp_command_empty():
    tool, kwargs = parse_mcp_command("")
    assert tool == ""
    assert kwargs == {}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tests/server/test_ui_mcp_console.py -v`
Expected: FAIL.

- [ ] **Step 3: Implement mcp_console.py**

Create `src/osscodeiq/server/ui/mcp_console.py`:

```python
"""MCP tool console — interactive terminal for executing MCP tools."""

from __future__ import annotations

import json
import re

from nicegui import ui

MCP_TOOL_NAMES = [
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


def parse_mcp_command(raw: str) -> tuple[str, dict]:
    """Parse a command string into tool name and keyword arguments.

    Format: ``tool_name key1="value1" key2=value2``
    """
    raw = raw.strip()
    if not raw:
        return "", {}
    parts = raw.split(maxsplit=1)
    tool = parts[0]
    kwargs: dict[str, str] = {}
    if len(parts) > 1:
        for m in re.finditer(r'(\w+)=(?:"([^"]*)"|(\S+))', parts[1]):
            key = m.group(1)
            val = m.group(2) if m.group(2) is not None else m.group(3)
            kwargs[key] = val
    return tool, kwargs


def _coerce_arg(val: str) -> int | str:
    """Try to cast to int, otherwise return as-is."""
    try:
        return int(val)
    except ValueError:
        return val


def create_mcp_console(service) -> None:
    """Build the MCP console tab. Called inside a NiceGUI page context."""

    from osscodeiq.server.mcp_server import (
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
        search_graph,
        read_file,
        trace_impact,
    )

    tool_map = {
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

    output_log = None
    history: list[str] = []

    with ui.column().classes("w-full h-full gap-0"):
        ui.label("MCP Tool Console").classes("text-lg font-bold px-4 pt-4 pb-2")
        ui.label(
            "Execute MCP tools directly. Type 'help' for available commands."
        ).classes("text-sm text-gray-500 px-4 pb-2")

        with ui.scroll_area().classes("flex-grow w-full px-4") as sa:
            output_log = ui.column().classes("w-full gap-1 font-mono text-sm")
            with output_log:
                ui.label("Welcome to OSSCodeIQ MCP Console").classes("text-indigo-400")
                ui.label("Type 'help' to see available tools.\n").classes("text-gray-500")

        with ui.row().classes("w-full items-center gap-2 px-4 py-3"):
            ui.label("$").classes("text-indigo-400 font-mono font-bold")
            cmd_input = ui.input(placeholder="get_stats").props(
                "outlined dense"
            ).classes("flex-grow font-mono")
            ui.button("Run", icon="play_arrow", on_click=lambda: _run()).props(
                "dense color=primary"
            )

    async def _run():
        raw = cmd_input.value or ""
        cmd_input.set_value("")
        if not raw.strip():
            return
        history.append(raw)
        with output_log:
            ui.label(f"$ {raw}").classes("text-indigo-300 mt-2")

        if raw.strip() == "help":
            with output_log:
                ui.label("Available tools:").classes("text-gray-400")
                for name in sorted(MCP_TOOL_NAMES):
                    ui.label(f"  {name}").classes("text-gray-300")
                ui.label('\nUsage: tool_name key="value" key2=value2').classes("text-gray-500")
            return

        tool_name, kwargs = parse_mcp_command(raw)
        if tool_name not in tool_map:
            with output_log:
                ui.label(f"Unknown tool: {tool_name}").classes("text-red-400")
                ui.label("Type 'help' for available tools.").classes("text-gray-500")
            return

        coerced = {k: _coerce_arg(v) for k, v in kwargs.items()}
        try:
            result = tool_map[tool_name](**coerced)
            # MCP tools return JSON strings
            if isinstance(result, str):
                try:
                    parsed = json.loads(result)
                    formatted = json.dumps(parsed, indent=2)
                except json.JSONDecodeError:
                    formatted = result
            else:
                formatted = str(result)
            with output_log:
                ui.html(f"<pre style='white-space:pre-wrap;word-break:break-all;user-select:all;'>{formatted}</pre>").classes("text-green-300 text-xs")
        except Exception as exc:
            with output_log:
                ui.label(f"Error: {exc}").classes("text-red-400")

    cmd_input.on("keydown.enter", _run)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest tests/server/test_ui_mcp_console.py -v`
Expected: All PASS.

- [ ] **Step 5: Commit**

```bash
git add src/osscodeiq/server/ui/mcp_console.py tests/server/test_ui_mcp_console.py
git commit -m "feat: add MCP tool console with command parsing and execution"
```

---

### Task 8: Create Flow View Page

**Files:**
- Create: `src/osscodeiq/server/ui/flow_view.py`

- [ ] **Step 1: Implement flow_view.py**

Create `src/osscodeiq/server/ui/flow_view.py`:

```python
"""Flow diagrams tab — wraps existing Cytoscape interactive flow."""

from __future__ import annotations

from nicegui import ui


def create_flow_page(service) -> None:
    """Build the flow diagrams tab content."""

    with ui.column().classes("w-full h-full"):
        try:
            flow_data = service.generate_all_flows()
            flow_html_result = service.generate_flow("overview", "html")
            html_content = flow_html_result.get("html", "")
            if html_content:
                ui.html(html_content).classes("w-full flex-grow")
            else:
                with ui.column().classes("w-full items-center justify-center py-20"):
                    ui.icon("account_tree").classes("text-6xl text-gray-500")
                    ui.label("No flow data available").classes("text-xl text-gray-400 mt-4")
                    ui.label("Run 'osscodeiq analyze' first to generate flow diagrams.").classes(
                        "text-sm text-gray-500"
                    )
        except Exception:
            with ui.column().classes("w-full items-center justify-center py-20"):
                ui.icon("error_outline").classes("text-6xl text-gray-500")
                ui.label("Flow visualization unavailable").classes("text-xl text-gray-400 mt-4")
                ui.label("Analyze a codebase first with 'osscodeiq analyze'").classes(
                    "text-sm text-gray-500"
                )
```

- [ ] **Step 2: Commit**

```bash
git add src/osscodeiq/server/ui/flow_view.py
git commit -m "feat: add flow view page wrapping existing Cytoscape visualization"
```

---

### Task 9: Wire Everything into app.py — Main UI Setup

**Files:**
- Modify: `src/osscodeiq/server/ui/__init__.py`
- Modify: `src/osscodeiq/server/app.py`
- Test: `tests/server/test_app.py`

- [ ] **Step 1: Implement setup_ui in __init__.py**

Update `src/osscodeiq/server/ui/__init__.py`:

```python
"""NiceGUI-based web UI for OSSCodeIQ."""

from __future__ import annotations

from typing import TYPE_CHECKING

from nicegui import app as nicegui_app, ui

from osscodeiq.server.ui.theme import BRAND_COLOR

if TYPE_CHECKING:
    from osscodeiq.server.service import CodeIQService


def setup_ui(service: CodeIQService) -> None:
    """Register NiceGUI pages on the existing FastAPI app."""

    @ui.page("/")
    def index():
        # Theme toggle
        dark = ui.dark_mode(value=None)

        # Header
        with ui.header().classes("items-center justify-between px-4 py-2"):
            with ui.row().classes("items-center gap-3"):
                ui.icon("hub").style(f"color: {BRAND_COLOR}; font-size: 28px")
                ui.label("OSSCodeIQ").classes("text-lg font-bold")
            with ui.row().classes("items-center gap-2"):
                try:
                    stats = service.get_stats()
                    ui.badge(f"{stats.get('total_nodes', 0):,} nodes").props(
                        "color=primary outline"
                    )
                    ui.badge(f"{stats.get('total_edges', 0):,} edges").props(
                        "color=positive outline"
                    )
                except Exception:
                    pass
                ui.button(icon="light_mode", on_click=lambda: dark.set_value(False)).props(
                    "flat dense round"
                ).tooltip("Light theme")
                ui.button(icon="dark_mode", on_click=lambda: dark.set_value(True)).props(
                    "flat dense round"
                ).tooltip("Dark theme")
                ui.button(icon="contrast", on_click=lambda: dark.set_value(None)).props(
                    "flat dense round"
                ).tooltip("System theme")

        # Tabs
        with ui.tabs().classes("w-full") as tabs:
            explorer_tab = ui.tab("Explorer", icon="explore")
            flow_tab = ui.tab("Flow", icon="account_tree")
            console_tab = ui.tab("MCP Console", icon="terminal")

        with ui.tab_panels(tabs, value=explorer_tab).classes("w-full flex-grow"):
            with ui.tab_panel(explorer_tab):
                from osscodeiq.server.ui.explorer import create_explorer_page

                create_explorer_page(service)
            with ui.tab_panel(flow_tab):
                from osscodeiq.server.ui.flow_view import create_flow_page

                create_flow_page(service)
            with ui.tab_panel(console_tab):
                from osscodeiq.server.ui.mcp_console import create_mcp_console

                create_mcp_console(service)
```

- [ ] **Step 2: Modify app.py to integrate NiceGUI**

In `src/osscodeiq/server/app.py`, replace the welcome page route and add `ui.run_with`:

```python
"""FastAPI application assembly — mounts REST API, MCP server, and NiceGUI UI."""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI

from osscodeiq.server.middleware import AuthMiddleware
from osscodeiq.server.mcp_server import get_mcp_app, set_service
from osscodeiq.server.routes import create_router
from osscodeiq.server.service import CodeIQService


def create_app(
    codebase_path: Path = Path("."),
    backend: str = "networkx",
    config_path: Path | None = None,
) -> FastAPI:
    """Create and configure the unified OSSCodeIQ server."""
    service = CodeIQService(
        path=codebase_path, backend=backend, config_path=config_path
    )

    # Set up MCP server
    set_service(service)
    mcp_app = get_mcp_app()

    # Create FastAPI with MCP lifespan
    app = FastAPI(
        title="OSSCodeIQ",
        description="OSSCodeIQ — graph queries, flow diagrams, and codebase analysis",
        lifespan=mcp_app.lifespan,
    )

    # Auth middleware stub (no-op, ready for future auth)
    app.add_middleware(AuthMiddleware)

    # Mount MCP at /mcp (streamable HTTP)
    app.mount("/mcp", mcp_app)

    # Include REST routes at /api
    router = create_router(service)
    app.include_router(router)

    # NiceGUI UI at / (explorer, flow, MCP console)
    from osscodeiq.server.ui import setup_ui
    from nicegui import ui

    setup_ui(service)
    ui.run_with(
        app,
        dark=None,
        title="OSSCodeIQ",
        storage_secret="osscodeiq-ui",
        mount_path="/ui",
    )

    return app
```

- [ ] **Step 3: Add redirect from / to /ui**

Add a root redirect so `/` sends to the NiceGUI UI:

```python
from fastapi.responses import RedirectResponse

@app.get("/", include_in_schema=False)
async def root_redirect():
    return RedirectResponse(url="/ui")
```

- [ ] **Step 4: Run existing app tests**

Run: `pytest tests/server/test_app.py -v`
Expected: Tests pass (may need adjustments for the redirect).

- [ ] **Step 5: Commit**

```bash
git add src/osscodeiq/server/ui/__init__.py src/osscodeiq/server/app.py
git commit -m "feat: integrate NiceGUI explorer UI with existing FastAPI server"
```

---

### Task 10: Run Full Test Suite and Coverage

- [ ] **Step 1: Run full test suite**

Run: `pytest tests/ -x -q`
Expected: All 2,074+ tests pass.

- [ ] **Step 2: Run coverage on new UI code**

Run: `pytest tests/server/ -v --cov=osscodeiq.server.ui --cov-report=term-missing`
Expected: 85%+ coverage on `server/ui/` modules.

- [ ] **Step 3: Fix any failures and re-run**

- [ ] **Step 4: Commit any test fixes**

```bash
git add -A
git commit -m "fix: resolve test failures from NiceGUI integration"
```

---

### Task 11: Benchmark on testDir

- [ ] **Step 1: Run analysis benchmark on spring-boot**

Run: `time osscodeiq analyze ~/projects/testDir/spring-boot --backend sqlite`
Record: time, node count, edge count, memory usage.

- [ ] **Step 2: Start server and test UI**

Run: `osscodeiq serve ~/projects/testDir/spring-boot --backend sqlite`
Open browser, verify:
- Explorer tab loads with kind cards
- Drill-down works
- Details modal opens
- MCP console runs `get_stats`
- Theme toggle works

- [ ] **Step 3: Record results**

Document benchmark results in commit message.

---

### Task 12: Commit, Push, and Release

- [ ] **Step 1: Final commit with all changes**

```bash
git add -A
git status
git commit -m "feat: NiceGUI explorer UI with drill-down cards, MCP console, and theme support"
```

- [ ] **Step 2: Push to remote**

```bash
git push origin main
```

- [ ] **Step 3: Trigger PyPI release**

Run the publish workflow with the next version (0.1.0):

```bash
gh workflow run publish.yml -f version=0.1.0
```

- [ ] **Step 4: Verify release**

```bash
gh run list --workflow=publish.yml --limit=1
pip install osscodeiq==0.1.0
```
