"""Tests for the OSSCodeIQ UI theme module."""

from __future__ import annotations

import re

from osscodeiq.server.ui.theme import (
    BRAND_COLOR,
    DEFAULT_COLOR,
    KIND_COLORS,
    KIND_ICONS,
    get_animation_css,
    get_kind_color,
    get_kind_icon,
)


class TestBrandColor:
    def test_brand_color_is_hex(self) -> None:
        assert re.fullmatch(r"#[0-9a-fA-F]{6}", BRAND_COLOR)

    def test_brand_color_value(self) -> None:
        assert BRAND_COLOR == "#6366f1"


class TestDefaultColor:
    def test_default_color_is_hex(self) -> None:
        assert re.fullmatch(r"#[0-9a-fA-F]{6}", DEFAULT_COLOR)


class TestKindIcons:
    REQUIRED_KINDS = [
        "endpoint",
        "entity",
        "class",
        "method",
        "module",
        "package",
        "repository",
        "query",
        "topic",
        "queue",
        "event",
        "config_file",
        "config_key",
        "component",
        "guard",
        "middleware",
        "hook",
        "infra_resource",
        "database_connection",
        "interface",
        "abstract_class",
        "enum",
    ]

    def test_covers_common_kinds(self) -> None:
        for kind in self.REQUIRED_KINDS:
            assert kind in KIND_ICONS, f"KIND_ICONS missing key: {kind}"

    def test_all_values_are_strings(self) -> None:
        for kind, icon in KIND_ICONS.items():
            assert isinstance(icon, str), f"Icon for {kind} is not a string"
            assert len(icon) > 0, f"Icon for {kind} is empty"


class TestKindColors:
    REQUIRED_KINDS = [
        "endpoint",
        "entity",
        "class",
        "method",
        "module",
        "package",
        "repository",
        "query",
        "component",
        "guard",
    ]

    def test_covers_common_kinds(self) -> None:
        for kind in self.REQUIRED_KINDS:
            assert kind in KIND_COLORS, f"KIND_COLORS missing key: {kind}"

    def test_all_values_are_hex(self) -> None:
        for kind, color in KIND_COLORS.items():
            assert re.fullmatch(
                r"#[0-9a-fA-F]{6}", color
            ), f"Color for {kind} is not valid hex: {color}"


class TestGetKindColor:
    def test_known_kind(self) -> None:
        result = get_kind_color("endpoint")
        assert re.fullmatch(r"#[0-9a-fA-F]{6}", result)

    def test_unknown_kind_returns_default(self) -> None:
        result = get_kind_color("nonexistent_kind_xyz")
        assert result == DEFAULT_COLOR

    def test_returns_correct_mapped_color(self) -> None:
        for kind, expected in KIND_COLORS.items():
            assert get_kind_color(kind) == expected


class TestGetKindIcon:
    def test_known_kind(self) -> None:
        result = get_kind_icon("endpoint")
        assert isinstance(result, str)
        assert len(result) > 0

    def test_unknown_kind_returns_circle(self) -> None:
        result = get_kind_icon("nonexistent_kind_xyz")
        assert result == "circle"

    def test_returns_correct_mapped_icon(self) -> None:
        for kind, expected in KIND_ICONS.items():
            assert get_kind_icon(kind) == expected


class TestGetAnimationCss:
    def test_contains_keyframes(self) -> None:
        css = get_animation_css()
        assert "@keyframes" in css

    def test_contains_fade_in_up(self) -> None:
        css = get_animation_css()
        assert "fadeInUp" in css

    def test_contains_fade_in(self) -> None:
        css = get_animation_css()
        assert "fadeIn" in css

    def test_contains_card_animate(self) -> None:
        css = get_animation_css()
        assert "card-animate" in css

    def test_contains_staggered_delays(self) -> None:
        css = get_animation_css()
        # Should have at least 10 staggered delay rules
        for i in range(1, 11):
            assert f"card-animate-{i}" in css or f":nth-child({i})" in css

    def test_contains_search_transitions(self) -> None:
        css = get_animation_css()
        assert "search-fade" in css

    def test_returns_string(self) -> None:
        css = get_animation_css()
        assert isinstance(css, str)
        assert len(css) > 100  # Should be substantial
