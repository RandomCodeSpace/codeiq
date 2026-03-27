"""Public API method detector for Java source files using tree-sitter AST."""

from __future__ import annotations

from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

# Methods that are boilerplate / Object overrides -- skip them.
_SKIP_METHODS = frozenset({"toString", "hashCode", "equals", "clone", "finalize"})


def _is_trivial_accessor(name: str, param_count: int, body_lines: int) -> bool:
    """Return True for simple getters/setters (get*/set*/is* with <=1 param, <=1 body line)."""
    if param_count > 1:
        return False
    if body_lines > 1:
        return False
    if name.startswith("get") or name.startswith("set") or name.startswith("is"):
        return True
    return False


def _body_line_count(method_node) -> int:  # type: ignore[no-untyped-def]
    """Count the number of non-empty lines inside the method body (excluding braces)."""
    body = method_node.child_by_field_name("body")
    if body is None:
        return 0
    start_line = body.start_point[0]
    end_line = body.end_point[0]
    # Subtract the lines with opening/closing braces themselves.
    return max(0, end_line - start_line - 1)


def _param_types(params_node) -> list[str]:  # type: ignore[no-untyped-def]
    """Extract simple type names from a formal_parameters node."""
    types: list[str] = []
    if params_node is None:
        return types
    for child in params_node.children:
        if child.type == "formal_parameter":
            type_node = child.child_by_field_name("type")
            if type_node is not None:
                types.append(type_node.text.decode("utf-8", errors="replace"))
    return types


def _find_child_by_type(node, type_name: str):  # type: ignore[no-untyped-def]
    """Find the first child of *node* with the given type (by iterating children)."""
    for child in node.children:
        if child.type == type_name:
            return child
    return None


def _has_modifier(modifiers_node, modifier_text: str) -> bool:  # type: ignore[no-untyped-def]
    """Check whether *modifiers_node* contains a specific keyword like 'public' or 'static'."""
    if modifiers_node is None:
        return False
    for child in modifiers_node.children:
        if child.text is not None and child.text.decode("utf-8", errors="replace") == modifier_text:
            return True
    return False


class PublicApiDetector:
    """Detects public and protected methods in Java classes and interfaces."""

    name: str = "java.public_api"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        if ctx.tree is None:
            return result

        root = ctx.tree.root_node
        for child in root.children:
            if child.type == "class_declaration":
                self._process_class(child, ctx, result, is_interface=False)
            elif child.type == "interface_declaration":
                self._process_class(child, ctx, result, is_interface=True)

        return result

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _process_class(
        self,
        class_node,  # type: ignore[no-untyped-def]
        ctx: DetectorContext,
        result: DetectorResult,
        *,
        is_interface: bool,
    ) -> None:
        name_node = class_node.child_by_field_name("name")
        if name_node is None:
            return
        class_name: str = name_node.text.decode("utf-8", errors="replace")
        class_node_id = f"{ctx.file_path}:{class_name}"

        body_field = "interface_body" if is_interface else "class_body"
        body = class_node.child_by_field_name("body")
        if body is None:
            return

        for member in body.children:
            if member.type == "method_declaration":
                self._process_method(member, class_name, class_node_id, ctx, result, is_interface=is_interface)

    def _process_method(
        self,
        method_node,  # type: ignore[no-untyped-def]
        class_name: str,
        class_node_id: str,
        ctx: DetectorContext,
        result: DetectorResult,
        *,
        is_interface: bool,
    ) -> None:
        # --- Method name ---
        name_node = method_node.child_by_field_name("name")
        if name_node is None:
            return
        method_name: str = name_node.text.decode("utf-8", errors="replace")

        # Skip Object boilerplate
        if method_name in _SKIP_METHODS:
            return

        # --- Modifiers ---
        # NOTE: In tree-sitter-java, "modifiers" is a child node type, NOT a
        # named field, so child_by_field_name("modifiers") returns None.
        # We must locate it by iterating children.
        modifiers_node = _find_child_by_type(method_node, "modifiers")

        # In a class, only public / protected methods qualify.
        # Interface methods are implicitly public.
        if is_interface:
            visibility = "public"
        else:
            if _has_modifier(modifiers_node, "public"):
                visibility = "public"
            elif _has_modifier(modifiers_node, "protected"):
                visibility = "protected"
            else:
                # private or package-private -- skip
                return

        is_static = _has_modifier(modifiers_node, "static")
        is_abstract = _has_modifier(modifiers_node, "abstract")

        # --- Parameters ---
        params_node = method_node.child_by_field_name("parameters")
        ptypes = _param_types(params_node)

        # Skip trivial getters / setters
        body_lines = _body_line_count(method_node)
        if _is_trivial_accessor(method_name, len(ptypes), body_lines):
            return

        # --- Return type ---
        type_node = method_node.child_by_field_name("type")
        return_type = type_node.text.decode("utf-8", errors="replace") if type_node is not None else "void"

        # --- Build node ---
        param_sig = ",".join(ptypes)
        method_id = f"{ctx.file_path}:{class_name}:{method_name}({param_sig})"
        line = method_node.start_point[0] + 1

        properties: dict[str, Any] = {
            "visibility": visibility,
            "return_type": return_type,
            "parameters": ptypes,
            "is_static": is_static,
            "is_abstract": is_abstract,
        }

        node = GraphNode(
            id=method_id,
            kind=NodeKind.METHOD,
            label=f"{class_name}.{method_name}",
            fqn=f"{class_name}.{method_name}({param_sig})",
            module=ctx.module_name,
            location=SourceLocation(file_path=ctx.file_path, line_start=line),
            properties=properties,
        )
        result.nodes.append(node)

        edge = GraphEdge(
            source=class_node_id,
            target=method_id,
            kind=EdgeKind.DEFINES,
            label=f"{class_name} defines {method_name}",
        )
        result.edges.append(edge)
