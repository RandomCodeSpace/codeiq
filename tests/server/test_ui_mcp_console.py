"""Tests for the OSSCodeIQ MCP Console module."""
from __future__ import annotations

from osscodeiq.server.ui.mcp_console import (
    MCP_TOOL_NAMES,
    _coerce_arg,
    parse_mcp_command,
)


class TestMCPToolNames:
    def test_mcp_tool_names_populated(self) -> None:
        assert "get_stats" in MCP_TOOL_NAMES
        assert "search_graph" in MCP_TOOL_NAMES
        assert len(MCP_TOOL_NAMES) >= 18


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
