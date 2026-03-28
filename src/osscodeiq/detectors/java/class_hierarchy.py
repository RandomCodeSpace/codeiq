"""Tree-sitter-based Java class hierarchy detector."""

from __future__ import annotations

from typing import Any

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)


def _extract_type_name(node) -> str | None:
    """Extract a simple type name from a tree-sitter type node.

    Handles plain identifiers, scoped_type_identifier, and generic_type nodes.
    """
    if node is None:
        return None
    if node.type == "type_identifier":
        return node.text.decode()
    if node.type == "scoped_type_identifier":
        return node.text.decode()
    if node.type == "generic_type":
        # The first child is the raw type identifier
        for child in node.children:
            if child.type in ("type_identifier", "scoped_type_identifier"):
                return child.text.decode()
    # Fallback: walk children for a type_identifier
    for child in node.children:
        name = _extract_type_name(child)
        if name is not None:
            return name
    return None


def _collect_type_names_from_type_list(node) -> list[str]:
    """Collect all type names from a type_list node."""
    names: list[str] = []
    if node is None:
        return names
    for child in node.children:
        if child.type in ("type_identifier", "generic_type", "scoped_type_identifier"):
            name = _extract_type_name(child)
            if name:
                names.append(name)
    return names


def _collect_type_names(container_node) -> list[str]:
    """Collect type names from a superclass, super_interfaces, or extends_interfaces node.

    These container nodes hold a ``type_list`` child which in turn holds the
    actual type identifier children.
    """
    if container_node is None:
        return []
    for child in container_node.children:
        if child.type == "type_list":
            return _collect_type_names_from_type_list(child)
    # Fallback: try the container itself (e.g. if it *is* the type_list)
    return _collect_type_names_from_type_list(container_node)


def _find_child_by_type(node, type_name: str):
    """Find the first direct child with the given node type."""
    for child in node.children:
        if child.type == type_name:
            return child
    return None


def _has_modifier(modifiers_node, modifier_type: str) -> bool:
    """Check if a modifiers node contains a specific modifier keyword."""
    if modifiers_node is None:
        return False
    for child in modifiers_node.children:
        if child.type == modifier_type:
            return True
    return False


def _get_visibility(modifiers_node) -> str:
    """Extract visibility from modifiers (public, protected, private, or package-private)."""
    if modifiers_node is None:
        return "package-private"
    for child in modifiers_node.children:
        if child.type in ("public", "protected", "private"):
            return child.type
    return "package-private"


class ClassHierarchyDetector:
    """Detects Java class hierarchies using tree-sitter AST."""

    name: str = "java.class_hierarchy"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        if ctx.tree is None:
            return result
        self._walk(ctx.tree.root_node, ctx, result, prefix="")
        return result

    def _walk(self, node, ctx: DetectorContext, result: DetectorResult, prefix: str) -> None:
        """Recursively walk AST nodes looking for type declarations."""
        for child in node.children:
            if child.type == "class_declaration":
                self._process_class(child, ctx, result, prefix)
            elif child.type == "interface_declaration":
                self._process_interface(child, ctx, result, prefix)
            elif child.type == "enum_declaration":
                self._process_enum(child, ctx, result, prefix)
            elif child.type == "annotation_type_declaration":
                self._process_annotation_type(child, ctx, result, prefix)

    def _process_class(
        self, node, ctx: DetectorContext, result: DetectorResult, prefix: str
    ) -> None:
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        simple_name = name_node.text.decode()
        qualified_name = f"{prefix}{simple_name}" if not prefix else f"{prefix}.{simple_name}"

        modifiers = _find_child_by_type(node, "modifiers")
        is_abstract = _has_modifier(modifiers, "abstract")
        is_final = _has_modifier(modifiers, "final")
        visibility = _get_visibility(modifiers)

        kind = NodeKind.ABSTRACT_CLASS if is_abstract else NodeKind.CLASS
        node_id = f"{ctx.file_path}:{qualified_name}"

        # Superclass (child_by_field_name works for "superclass")
        superclass_name: str | None = None
        superclass_node = node.child_by_field_name("superclass")
        if superclass_node is not None:
            superclass_name = _extract_type_name(superclass_node)

        # Interfaces (field "interfaces" maps to super_interfaces node)
        interfaces_node = _find_child_by_type(node, "super_interfaces")
        interface_names = _collect_type_names(interfaces_node)

        properties: dict[str, Any] = {
            "visibility": visibility,
            "is_abstract": is_abstract,
            "is_final": is_final,
        }
        if superclass_name:
            properties["superclass"] = superclass_name
        if interface_names:
            properties["interfaces"] = interface_names

        graph_node = GraphNode(
            id=node_id,
            kind=kind,
            label=qualified_name,
            fqn=qualified_name,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=ctx.file_path,
                line_start=name_node.start_point[0] + 1,
            ),
            properties=properties,
        )
        result.nodes.append(graph_node)

        # Edges for extends
        if superclass_name:
            result.edges.append(
                GraphEdge(
                    source=node_id,
                    target=f"*:{superclass_name}",
                    kind=EdgeKind.EXTENDS,
                    label=f"{qualified_name} extends {superclass_name}",
                )
            )

        # Edges for implements
        for iface in interface_names:
            result.edges.append(
                GraphEdge(
                    source=node_id,
                    target=f"*:{iface}",
                    kind=EdgeKind.IMPLEMENTS,
                    label=f"{qualified_name} implements {iface}",
                )
            )

        # Recurse into class body for nested types
        body = node.child_by_field_name("body")
        if body is not None:
            self._walk(body, ctx, result, prefix=qualified_name)

    def _process_interface(
        self, node, ctx: DetectorContext, result: DetectorResult, prefix: str
    ) -> None:
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        simple_name = name_node.text.decode()
        qualified_name = f"{prefix}{simple_name}" if not prefix else f"{prefix}.{simple_name}"

        modifiers = _find_child_by_type(node, "modifiers")
        visibility = _get_visibility(modifiers)

        node_id = f"{ctx.file_path}:{qualified_name}"

        # Extended interfaces (not a field; must walk children for the node type)
        extends_node = _find_child_by_type(node, "extends_interfaces")
        extended_names = _collect_type_names(extends_node)

        properties: dict[str, Any] = {
            "visibility": visibility,
            "is_abstract": False,
            "is_final": False,
        }
        if extended_names:
            properties["interfaces"] = extended_names

        graph_node = GraphNode(
            id=node_id,
            kind=NodeKind.INTERFACE,
            label=qualified_name,
            fqn=qualified_name,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=ctx.file_path,
                line_start=name_node.start_point[0] + 1,
            ),
            properties=properties,
        )
        result.nodes.append(graph_node)

        # Edges for extended interfaces
        for ext in extended_names:
            result.edges.append(
                GraphEdge(
                    source=node_id,
                    target=f"*:{ext}",
                    kind=EdgeKind.EXTENDS,
                    label=f"{qualified_name} extends {ext}",
                )
            )

        # Recurse into interface body for nested types
        body = node.child_by_field_name("body")
        if body is not None:
            self._walk(body, ctx, result, prefix=qualified_name)

    def _process_enum(
        self, node, ctx: DetectorContext, result: DetectorResult, prefix: str
    ) -> None:
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        simple_name = name_node.text.decode()
        qualified_name = f"{prefix}{simple_name}" if not prefix else f"{prefix}.{simple_name}"

        modifiers = _find_child_by_type(node, "modifiers")
        visibility = _get_visibility(modifiers)

        node_id = f"{ctx.file_path}:{qualified_name}"

        # Interfaces
        interfaces_node = _find_child_by_type(node, "super_interfaces")
        interface_names = _collect_type_names(interfaces_node)

        properties: dict[str, Any] = {
            "visibility": visibility,
            "is_abstract": False,
            "is_final": False,
        }
        if interface_names:
            properties["interfaces"] = interface_names

        graph_node = GraphNode(
            id=node_id,
            kind=NodeKind.ENUM,
            label=qualified_name,
            fqn=qualified_name,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=ctx.file_path,
                line_start=name_node.start_point[0] + 1,
            ),
            properties=properties,
        )
        result.nodes.append(graph_node)

        # Edges for implements
        for iface in interface_names:
            result.edges.append(
                GraphEdge(
                    source=node_id,
                    target=f"*:{iface}",
                    kind=EdgeKind.IMPLEMENTS,
                    label=f"{qualified_name} implements {iface}",
                )
            )

        # Recurse into enum body for nested types
        body = node.child_by_field_name("body")
        if body is not None:
            self._walk(body, ctx, result, prefix=qualified_name)

    def _process_annotation_type(
        self, node, ctx: DetectorContext, result: DetectorResult, prefix: str
    ) -> None:
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        simple_name = name_node.text.decode()
        qualified_name = f"{prefix}{simple_name}" if not prefix else f"{prefix}.{simple_name}"

        modifiers = _find_child_by_type(node, "modifiers")
        visibility = _get_visibility(modifiers)

        node_id = f"{ctx.file_path}:{qualified_name}"

        properties: dict[str, Any] = {
            "visibility": visibility,
            "is_abstract": False,
            "is_final": False,
        }

        graph_node = GraphNode(
            id=node_id,
            kind=NodeKind.ANNOTATION_TYPE,
            label=qualified_name,
            fqn=qualified_name,
            module=ctx.module_name,
            location=SourceLocation(
                file_path=ctx.file_path,
                line_start=name_node.start_point[0] + 1,
            ),
            properties=properties,
        )
        result.nodes.append(graph_node)

        # Recurse into annotation body for nested types
        body = node.child_by_field_name("body")
        if body is not None:
            self._walk(body, ctx, result, prefix=qualified_name)
