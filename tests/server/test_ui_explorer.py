"""Tests for the OSSCodeIQ Explorer page state management and JS generation."""

from __future__ import annotations

from osscodeiq.server.ui.explorer import (
    ExplorerState,
    _nav_to,
    _on_drill_down,
    _on_page_change,
    build_filter_js,
)


class TestExplorerStateInitial:
    def test_initial_state(self) -> None:
        state = ExplorerState()
        assert state.level == "kinds"
        assert state.current_kind is None
        assert state.page_offset == 0
        assert state.page_limit == 50
        assert len(state.breadcrumb) == 1
        assert state.breadcrumb[0]["label"] == "Home"
        assert state.breadcrumb[0]["level"] == "kinds"
        assert state.breadcrumb[0]["kind"] is None

    def test_custom_page_limit(self) -> None:
        state = ExplorerState(page_limit=25)
        assert state.page_limit == 25

    def test_initial_breadcrumb_auto_created(self) -> None:
        state = ExplorerState()
        assert state.breadcrumb == [{"label": "Home", "level": "kinds", "kind": None}]


class TestExplorerStateDrillDown:
    def test_drill_down(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        assert state.level == "nodes"
        assert state.current_kind == "endpoint"
        assert state.page_offset == 0
        assert len(state.breadcrumb) == 2
        assert state.breadcrumb[1]["label"] == "endpoint"
        assert state.breadcrumb[1]["level"] == "nodes"
        assert state.breadcrumb[1]["kind"] == "endpoint"

    def test_drill_down_resets_offset(self) -> None:
        state = ExplorerState()
        state.page_offset = 100
        state.drill_down("entity")
        assert state.page_offset == 0

    def test_drill_down_preserves_home_breadcrumb(self) -> None:
        state = ExplorerState()
        state.drill_down("class")
        assert state.breadcrumb[0]["label"] == "Home"
        assert state.breadcrumb[0]["level"] == "kinds"

    def test_multiple_drill_downs_build_breadcrumb(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.drill_down("guard")
        assert len(state.breadcrumb) == 3
        assert state.breadcrumb[1]["label"] == "endpoint"
        assert state.breadcrumb[2]["label"] == "guard"
        assert state.current_kind == "guard"

    def test_drill_down_different_kinds(self) -> None:
        state = ExplorerState()
        for kind in ["endpoint", "entity", "class", "module"]:
            state.drill_down(kind)
        assert len(state.breadcrumb) == 5
        assert state.current_kind == "module"


class TestExplorerStateGoHome:
    def test_go_home(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 50
        state.go_home()
        assert state.level == "kinds"
        assert state.current_kind is None
        assert state.page_offset == 0
        assert len(state.breadcrumb) == 1
        assert state.breadcrumb[0]["label"] == "Home"

    def test_go_home_from_home(self) -> None:
        state = ExplorerState()
        state.go_home()
        assert state.level == "kinds"
        assert len(state.breadcrumb) == 1

    def test_go_home_after_multiple_drill_downs(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.drill_down("guard")
        state.drill_down("entity")
        state.go_home()
        assert state.level == "kinds"
        assert state.current_kind is None
        assert len(state.breadcrumb) == 1
        assert state.breadcrumb[0]["label"] == "Home"

    def test_go_home_resets_offset(self) -> None:
        state = ExplorerState()
        state.drill_down("class")
        state.page_offset = 200
        state.go_home()
        assert state.page_offset == 0


class TestExplorerStateNavigateTo:
    def test_navigate_to_home(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.navigate_to(0)
        assert state.level == "kinds"
        assert state.current_kind is None
        assert len(state.breadcrumb) == 1
        assert state.breadcrumb[0]["label"] == "Home"

    def test_navigate_to_preserves_path(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        # Breadcrumb: [Home, endpoint]
        # Navigate to index 1 (endpoint) — stays there
        state.navigate_to(1)
        assert state.level == "nodes"
        assert state.current_kind == "endpoint"
        assert len(state.breadcrumb) == 2

    def test_navigate_to_resets_offset(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 100
        state.navigate_to(0)
        assert state.page_offset == 0

    def test_navigate_to_negative_goes_home(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.navigate_to(-1)
        assert state.level == "kinds"
        assert state.current_kind is None

    def test_navigate_to_out_of_bounds_ignored(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        # Index 5 is out of bounds — should be a no-op
        state.navigate_to(5)
        assert state.level == "nodes"
        assert state.current_kind == "endpoint"
        assert len(state.breadcrumb) == 2

    def test_navigate_to_middle_of_trail(self) -> None:
        """Navigating to index 1 when trail is [Home, endpoint, guard] trims to [Home, endpoint]."""
        state = ExplorerState()
        state.drill_down("endpoint")
        state.drill_down("guard")
        assert len(state.breadcrumb) == 3
        state.navigate_to(1)
        assert len(state.breadcrumb) == 2
        assert state.current_kind == "endpoint"
        assert state.level == "nodes"

    def test_navigate_to_resets_offset_from_deep(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.drill_down("guard")
        state.page_offset = 150
        state.navigate_to(1)
        assert state.page_offset == 0


class TestExplorerStatePagination:
    """Test page_offset boundary conditions."""

    def test_page_forward(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 0
        # Simulate page forward
        state.page_offset += state.page_limit
        assert state.page_offset == 50

    def test_page_backward_from_second_page(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 50
        state.page_offset -= state.page_limit
        assert state.page_offset == 0

    def test_page_offset_not_negative(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 0
        # Simulate what _on_page_change does
        new_offset = state.page_offset - state.page_limit
        if new_offset < 0:
            new_offset = 0
        state.page_offset = new_offset
        assert state.page_offset == 0

    def test_drill_down_always_resets_page(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 150
        state.drill_down("guard")
        assert state.page_offset == 0

    def test_navigate_to_home_resets_page(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 100
        state.navigate_to(0)
        assert state.page_offset == 0


class TestBuildFilterJs:
    """Tests for the extracted build_filter_js function."""

    def test_returns_string(self) -> None:
        result = build_filter_js("test")
        assert isinstance(result, str)

    def test_contains_query(self) -> None:
        result = build_filter_js("mySearch")
        assert "mySearch" in result

    def test_default_container(self) -> None:
        result = build_filter_js("test")
        assert ".explorer-card" in result

    def test_custom_container(self) -> None:
        result = build_filter_js("test", ".custom-card")
        assert ".custom-card" in result
        assert ".explorer-card" not in result

    def test_empty_query(self) -> None:
        result = build_filter_js("")
        assert isinstance(result, str)
        # Should produce valid JS with empty query string
        assert '("")' in result

    def test_escapes_double_quotes(self) -> None:
        result = build_filter_js('say "hello"')
        assert '\\"hello\\"' in result
        # Should not have unescaped quotes breaking the JS
        assert 'say \\"hello\\"' in result

    def test_escapes_backslashes(self) -> None:
        result = build_filter_js("path\\to\\file")
        assert "path\\\\to\\\\file" in result

    def test_is_self_executing_function(self) -> None:
        result = build_filter_js("test")
        assert result.strip().startswith("(function(query)")
        assert result.strip().endswith('("test")')

    def test_contains_querySelectorAll(self) -> None:
        result = build_filter_js("x")
        assert "querySelectorAll" in result

    def test_contains_opacity_logic(self) -> None:
        result = build_filter_js("x")
        assert "opacity" in result
        assert "pointerEvents" in result

    def test_special_chars_in_query(self) -> None:
        result = build_filter_js("<script>alert(1)</script>")
        assert isinstance(result, str)
        # The angle brackets pass through — they're inside a JS string
        assert "<script>" in result


class TestOnDrillDown:
    """Tests for the _on_drill_down navigation helper."""

    def test_drill_down_updates_state(self) -> None:
        state = ExplorerState()
        calls = []
        _on_drill_down("endpoint", state, lambda: calls.append(1))
        assert state.level == "nodes"
        assert state.current_kind == "endpoint"

    def test_drill_down_calls_refresh(self) -> None:
        state = ExplorerState()
        calls = []
        _on_drill_down("endpoint", state, lambda: calls.append(1))
        assert len(calls) == 1

    def test_drill_down_resets_offset(self) -> None:
        state = ExplorerState()
        state.page_offset = 100
        _on_drill_down("guard", state, lambda: None)
        assert state.page_offset == 0


class TestOnPageChange:
    """Tests for the _on_page_change pagination helper."""

    def test_page_forward(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        calls = []
        _on_page_change(50, state, 200, lambda: calls.append(1))
        assert state.page_offset == 50
        assert len(calls) == 1

    def test_page_backward(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 100
        calls = []
        _on_page_change(-50, state, 200, lambda: calls.append(1))
        assert state.page_offset == 50
        assert len(calls) == 1

    def test_page_backward_clamps_to_zero(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 20
        calls = []
        _on_page_change(-50, state, 200, lambda: calls.append(1))
        assert state.page_offset == 0
        assert len(calls) == 1

    def test_page_forward_past_total_is_noop(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 150
        calls = []
        _on_page_change(50, state, 200, lambda: calls.append(1))
        # 150 + 50 = 200, which is >= total, so no change
        assert state.page_offset == 150
        assert len(calls) == 0

    def test_page_forward_exact_total_is_noop(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 100
        calls = []
        _on_page_change(50, state, 150, lambda: calls.append(1))
        # 100 + 50 = 150, which is >= total (150), so no change
        assert state.page_offset == 100
        assert len(calls) == 0

    def test_page_forward_below_total(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.page_offset = 50
        calls = []
        _on_page_change(50, state, 150, lambda: calls.append(1))
        assert state.page_offset == 100
        assert len(calls) == 1

    def test_zero_delta_is_noop_at_zero(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        calls = []
        # 0 + 0 = 0, which is < total, so it proceeds
        _on_page_change(0, state, 100, lambda: calls.append(1))
        assert state.page_offset == 0
        assert len(calls) == 1


class TestNavTo:
    """Tests for the _nav_to breadcrumb navigation helper."""

    def test_nav_to_home(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        calls = []
        _nav_to(0, state, lambda: calls.append(1))
        assert state.level == "kinds"
        assert len(calls) == 1

    def test_nav_to_calls_refresh(self) -> None:
        state = ExplorerState()
        state.drill_down("endpoint")
        state.drill_down("guard")
        calls = []
        _nav_to(1, state, lambda: calls.append(1))
        assert state.current_kind == "endpoint"
        assert len(calls) == 1
