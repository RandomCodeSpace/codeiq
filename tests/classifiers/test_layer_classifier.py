"""Tests for LayerClassifier deterministic layer assignment."""

from code_intelligence.classifiers.layer_classifier import LayerClassifier
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation


def _node(id: str, kind: NodeKind, file_path: str, **props) -> GraphNode:
    return GraphNode(
        id=id, kind=kind, label=id,
        location=SourceLocation(file_path=file_path),
        properties=props,
    )


def test_frontend_component_classified():
    node = _node("c1", NodeKind.COMPONENT, "src/components/App.tsx")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "frontend"


def test_backend_endpoint_classified():
    node = _node("e1", NodeKind.ENDPOINT, "src/controllers/users.py")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "backend"


def test_infra_resource_classified():
    node = _node("i1", NodeKind.INFRA_RESOURCE, "infra/main.tf")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "infra"


def test_config_file_classified_shared():
    node = _node("cf1", NodeKind.CONFIG_FILE, "config/app.json")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "shared"


def test_tsx_file_classified_frontend():
    node = _node("m1", NodeKind.METHOD, "src/components/Button.tsx")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "frontend"


def test_unknown_fallback():
    node = _node("x1", NodeKind.CLASS, "lib/utils.py")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "unknown"


def test_framework_property_frontend():
    node = _node("r1", NodeKind.CLASS, "app/page.ts", framework="react")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "frontend"


def test_framework_property_backend():
    node = _node("b1", NodeKind.CLASS, "app/service.py", framework="django")
    LayerClassifier().classify([node])
    assert node.properties["layer"] == "backend"


def test_determinism():
    nodes1 = [
        _node("a", NodeKind.METHOD, "src/components/Foo.tsx"),
        _node("b", NodeKind.ENDPOINT, "api/routes.py"),
        _node("c", NodeKind.INFRA_RESOURCE, "deploy/main.tf"),
        _node("d", NodeKind.CLASS, "lib/utils.java"),
    ]
    nodes2 = [
        _node("a", NodeKind.METHOD, "src/components/Foo.tsx"),
        _node("b", NodeKind.ENDPOINT, "api/routes.py"),
        _node("c", NodeKind.INFRA_RESOURCE, "deploy/main.tf"),
        _node("d", NodeKind.CLASS, "lib/utils.java"),
    ]
    LayerClassifier().classify(nodes1)
    LayerClassifier().classify(nodes2)
    for n1, n2 in zip(nodes1, nodes2):
        assert n1.properties["layer"] == n2.properties["layer"]
