"""Tests for the OSSCodeIQ MCP Console module."""
from __future__ import annotations

from osscodeiq.server.ui.mcp_console import (
    MCP_TOOL_NAMES,
    _coerce_arg,
    _get_tool_fn,
    get_tool_map,
    parse_mcp_command,
)


class TestMCPToolNames:
    def test_mcp_tool_names_populated(self) -> None:
        assert "get_stats" in MCP_TOOL_NAMES
        assert "search_graph" in MCP_TOOL_NAMES
        assert len(MCP_TOOL_NAMES) >= 18

    def test_all_tool_names_are_strings(self) -> None:
        for name in MCP_TOOL_NAMES:
            assert isinstance(name, str)
            assert name.strip() == name  # no leading/trailing whitespace

    def test_no_duplicate_tool_names(self) -> None:
        assert len(MCP_TOOL_NAMES) == len(set(MCP_TOOL_NAMES))


class TestParseMcpCommand:
    def test_parse_mcp_command_simple(self) -> None:
        tool, kwargs = parse_mcp_command("get_stats")
        assert tool == "get_stats"
        assert kwargs == {}

    def test_parse_mcp_command_with_args(self) -> None:
        tool, kwargs = parse_mcp_command('search_graph query="auth" limit=10')
        assert tool == "search_graph"
        assert kwargs["query"] == "auth"
        assert kwargs["limit"] == 10

    def test_parse_mcp_command_empty(self) -> None:
        tool, kwargs = parse_mcp_command("")
        assert tool == ""
        assert kwargs == {}

    def test_parse_mcp_command_whitespace_only(self) -> None:
        tool, kwargs = parse_mcp_command("   ")
        assert tool == ""
        assert kwargs == {}

    def test_parse_mcp_command_unquoted_string_arg(self) -> None:
        tool, kwargs = parse_mcp_command("find_callers target_id=some:node:id")
        assert tool == "find_callers"
        assert kwargs["target_id"] == "some:node:id"

    def test_parse_mcp_command_multiple_quoted(self) -> None:
        tool, kwargs = parse_mcp_command(
            'find_shortest_path source="node:a" target="node:b"'
        )
        assert tool == "find_shortest_path"
        assert kwargs["source"] == "node:a"
        assert kwargs["target"] == "node:b"

    def test_parse_mcp_command_leading_trailing_whitespace(self) -> None:
        tool, kwargs = parse_mcp_command("  get_stats  ")
        assert tool == "get_stats"
        assert kwargs == {}

    def test_parse_mcp_command_mixed_arg_types(self) -> None:
        tool, kwargs = parse_mcp_command('query_nodes kind="endpoint" limit=20 offset=0')
        assert tool == "query_nodes"
        assert kwargs["kind"] == "endpoint"
        assert kwargs["limit"] == 20
        assert kwargs["offset"] == 0

    def test_parse_mcp_command_special_chars_in_quoted_value(self) -> None:
        tool, kwargs = parse_mcp_command('search_graph q="hello world & foo"')
        assert tool == "search_graph"
        assert kwargs["q"] == "hello world & foo"

    def test_parse_mcp_command_path_with_colons(self) -> None:
        tool, kwargs = parse_mcp_command("get_node_neighbors node_id=ep:src/routes.py:endpoint:GET")
        assert tool == "get_node_neighbors"
        assert kwargs["node_id"] == "ep:src/routes.py:endpoint:GET"

    def test_parse_mcp_command_single_arg_no_value(self) -> None:
        """A bare tool name with no args returns empty kwargs."""
        tool, kwargs = parse_mcp_command("find_cycles")
        assert tool == "find_cycles"
        assert kwargs == {}

    def test_parse_mcp_command_numeric_string_arg(self) -> None:
        """Numeric values as unquoted args are coerced to int."""
        tool, kwargs = parse_mcp_command("get_ego_graph radius=3")
        assert tool == "get_ego_graph"
        assert kwargs["radius"] == 3
        assert isinstance(kwargs["radius"], int)


class TestCoerceArg:
    def test_coerce_arg_int(self) -> None:
        assert _coerce_arg("10") == 10

    def test_coerce_arg_string(self) -> None:
        assert _coerce_arg("hello") == "hello"

    def test_coerce_arg_negative_int(self) -> None:
        assert _coerce_arg("-5") == -5

    def test_coerce_arg_zero(self) -> None:
        assert _coerce_arg("0") == 0

    def test_coerce_arg_float_stays_string(self) -> None:
        result = _coerce_arg("3.14")
        assert result == "3.14"
        assert isinstance(result, str)

    def test_coerce_arg_empty_string(self) -> None:
        result = _coerce_arg("")
        assert result == ""
        assert isinstance(result, str)

    def test_coerce_arg_large_int(self) -> None:
        assert _coerce_arg("999999") == 999999

    def test_coerce_arg_bool_like_stays_string(self) -> None:
        result = _coerce_arg("true")
        assert result == "true"
        assert isinstance(result, str)

    def test_coerce_arg_path_string(self) -> None:
        result = _coerce_arg("src/routes.py")
        assert result == "src/routes.py"
        assert isinstance(result, str)


class TestGetToolMap:
    """Tests for the extracted get_tool_map function."""

    def test_returns_dict(self) -> None:
        tool_map = get_tool_map()
        assert isinstance(tool_map, dict)

    def test_contains_all_tool_names(self) -> None:
        tool_map = get_tool_map()
        for name in MCP_TOOL_NAMES:
            assert name in tool_map, f"Missing tool: {name}"

    def test_all_values_are_callable(self) -> None:
        tool_map = get_tool_map()
        for name, fn in tool_map.items():
            assert callable(fn), f"Tool {name} is not callable"

    def test_tool_count_matches(self) -> None:
        tool_map = get_tool_map()
        assert len(tool_map) == len(MCP_TOOL_NAMES)

    def test_get_stats_is_mapped(self) -> None:
        tool_map = get_tool_map()
        assert "get_stats" in tool_map
        assert callable(tool_map["get_stats"])

    def test_find_producers_is_mapped(self) -> None:
        """find_producers was previously a special case — verify it's in the map."""
        tool_map = get_tool_map()
        assert "find_producers" in tool_map
        assert callable(tool_map["find_producers"])


class TestGetToolFn:
    """Tests for _get_tool_fn lookup helper."""

    def test_known_tool_returns_callable(self) -> None:
        fn = _get_tool_fn("get_stats")
        assert fn is not None
        assert callable(fn)

    def test_unknown_tool_returns_none(self) -> None:
        fn = _get_tool_fn("nonexistent_tool")
        assert fn is None

    def test_all_mcp_tools_resolvable(self) -> None:
        for name in MCP_TOOL_NAMES:
            fn = _get_tool_fn(name)
            assert fn is not None, f"_get_tool_fn returned None for {name}"
