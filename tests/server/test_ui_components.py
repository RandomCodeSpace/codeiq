"""Tests for the OSSCodeIQ UI components module."""

from __future__ import annotations

from osscodeiq.server.ui.components import (
    build_detail_data,
    build_kind_card_data,
    build_node_card_data,
)


class TestBuildKindCardData:
    def test_basic_transform(self) -> None:
        kind_info = {
            "kind": "endpoint",
            "count": 42,
            "preview": ["GET /api/users", "POST /api/auth"],
        }
        result = build_kind_card_data(kind_info)
        assert result["kind"] == "endpoint"
        assert result["title"] == "endpoint"
        assert result["count"] == 42
        assert result["icon"] is not None
        assert result["color"] is not None
        assert result["preview"] == ["GET /api/users", "POST /api/auth"]

    def test_icon_and_color_populated(self) -> None:
        kind_info = {"kind": "entity", "count": 5, "preview": []}
        result = build_kind_card_data(kind_info)
        assert isinstance(result["icon"], str)
        assert result["icon"] != ""
        assert result["color"].startswith("#")

    def test_missing_preview_defaults_empty(self) -> None:
        kind_info = {"kind": "class", "count": 10}
        result = build_kind_card_data(kind_info)
        assert result["preview"] == []

    def test_unknown_kind_gets_defaults(self) -> None:
        kind_info = {"kind": "unknown_thing", "count": 1, "preview": []}
        result = build_kind_card_data(kind_info)
        assert result["icon"] == "circle"
        assert result["color"].startswith("#")


class TestBuildNodeCardData:
    def test_basic_transform(self) -> None:
        node_info = {
            "id": "ep:src/routes.py:endpoint:GET /users",
            "name": "GET /users",
            "module": "routes",
            "file_path": "src/routes.py",
            "edge_count": 3,
            "properties": {"http_method": "GET", "path": "/users"},
        }
        result = build_node_card_data(node_info)
        assert result["id"] == "ep:src/routes.py:endpoint:GET /users"
        assert result["title"] == "GET /users"
        assert isinstance(result["subtitle"], str)
        assert "routes" in result["subtitle"]
        assert result["module"] == "routes"
        assert result["properties"] == {"http_method": "GET", "path": "/users"}

    def test_subtitle_includes_file_path(self) -> None:
        node_info = {
            "id": "cls:app.py:class:UserService",
            "name": "UserService",
            "module": "app",
            "file_path": "app.py",
            "edge_count": 5,
        }
        result = build_node_card_data(node_info)
        assert "app.py" in result["subtitle"]

    def test_subtitle_includes_edge_count(self) -> None:
        node_info = {
            "id": "cls:app.py:class:UserService",
            "name": "UserService",
            "module": "app",
            "file_path": "app.py",
            "edge_count": 7,
        }
        result = build_node_card_data(node_info)
        assert "7" in result["subtitle"]

    def test_missing_optional_fields(self) -> None:
        node_info = {
            "id": "mod:utils.py:module:utils",
            "name": "utils",
        }
        result = build_node_card_data(node_info)
        assert result["title"] == "utils"
        assert result["module"] is None
        assert result["properties"] == {}


class TestBuildDetailData:
    def test_basic_transform(self) -> None:
        detail = {
            "id": "ep:src/routes.py:endpoint:GET /users",
            "name": "GET /users",
            "kind": "endpoint",
            "fqn": "routes.GET /users",
            "module": "routes",
            "file_path": "src/routes.py",
            "start_line": 10,
            "end_line": 25,
            "layer": "backend",
            "properties": {"http_method": "GET", "path": "/users"},
            "edges_out": [
                {
                    "kind": "calls",
                    "target_id": "cls:src/service.py:class:UserService",
                    "target_name": "UserService",
                }
            ],
            "edges_in": [
                {
                    "kind": "protects",
                    "source_id": "grd:src/guards.py:guard:AuthGuard",
                    "source_name": "AuthGuard",
                }
            ],
        }
        result = build_detail_data(detail)
        assert result["name"] == "GET /users"
        assert result["kind"] == "endpoint"

        # Properties should be a list of tuples
        assert isinstance(result["properties"], list)
        prop_keys = [p[0] for p in result["properties"]]
        assert "FQN" in prop_keys
        assert "Module" in prop_keys
        assert "Location" in prop_keys
        assert "Layer" in prop_keys

        # Edges preserved
        assert len(result["edges_out"]) == 1
        assert len(result["edges_in"]) == 1

    def test_properties_include_custom(self) -> None:
        detail = {
            "id": "ep:r.py:endpoint:POST /auth",
            "name": "POST /auth",
            "kind": "endpoint",
            "properties": {"auth_type": "jwt", "rate_limit": "100/min"},
            "edges_out": [],
            "edges_in": [],
        }
        result = build_detail_data(detail)
        prop_keys = [p[0] for p in result["properties"]]
        assert "auth_type" in prop_keys
        assert "rate_limit" in prop_keys

    def test_location_includes_line_numbers(self) -> None:
        detail = {
            "id": "cls:app.py:class:Foo",
            "name": "Foo",
            "kind": "class",
            "file_path": "app.py",
            "start_line": 5,
            "end_line": 50,
            "properties": {},
            "edges_out": [],
            "edges_in": [],
        }
        result = build_detail_data(detail)
        location_props = [p for p in result["properties"] if p[0] == "Location"]
        assert len(location_props) == 1
        loc_value = location_props[0][1]
        assert "app.py" in loc_value
        assert "5" in loc_value
        assert "50" in loc_value

    def test_empty_edges(self) -> None:
        detail = {
            "id": "mod:x.py:module:x",
            "name": "x",
            "kind": "module",
            "properties": {},
            "edges_out": [],
            "edges_in": [],
        }
        result = build_detail_data(detail)
        assert result["edges_out"] == []
        assert result["edges_in"] == []

    def test_missing_optional_fields(self) -> None:
        detail = {
            "id": "mod:x.py:module:x",
            "name": "x",
            "kind": "module",
            "properties": {},
            "edges_out": [],
            "edges_in": [],
        }
        result = build_detail_data(detail)
        # Should not crash, location should handle missing gracefully
        prop_keys = [p[0] for p in result["properties"]]
        # FQN, Module, Layer may be absent but should not error
        assert isinstance(result["properties"], list)
