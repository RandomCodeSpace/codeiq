"""Tests for React component and hook detector."""

from osscodeiq.detectors.base import DetectorContext
from osscodeiq.detectors.frontend.react_components import ReactComponentDetector
from osscodeiq.models.graph import EdgeKind, NodeKind


def _ctx(content: str, file_path: str = "src/components/App.tsx") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="typescript",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestFunctionComponents:
    def test_export_default_function(self):
        source = """\
import React from 'react';

export default function Dashboard(props) {
  return <div>Hello</div>;
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "Dashboard"
        assert components[0].properties["framework"] == "react"
        assert components[0].properties["component_type"] == "function"
        assert components[0].id == "react:src/components/App.tsx:component:Dashboard"

    def test_export_const_arrow(self):
        source = """\
import React from 'react';

export const UserProfile = (props) => {
  return <div>{props.name}</div>;
};
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "UserProfile"
        assert components[0].properties["component_type"] == "function"

    def test_export_const_react_fc(self):
        source = """\
import React from 'react';

export const Sidebar: React.FC<SidebarProps> = ({ items }) => {
  return <nav>{items.map(i => <li key={i.id}>{i.label}</li>)}</nav>;
};
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "Sidebar"
        assert components[0].properties["component_type"] == "function"

    def test_multiple_function_components(self):
        source = """\
export default function Header(props) {
  return <h1>Title</h1>;
}

export const Footer = (props) => {
  return <footer>Bottom</footer>;
};
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 2
        labels = {c.label for c in components}
        assert labels == {"Header", "Footer"}


class TestClassComponents:
    def test_extends_react_component(self):
        source = """\
import React from 'react';

class TodoList extends React.Component {
  render() {
    return <ul>{this.props.items.map(i => <li>{i}</li>)}</ul>;
  }
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "TodoList"
        assert components[0].properties["component_type"] == "class"
        assert components[0].properties["framework"] == "react"

    def test_extends_component(self):
        source = """\
import { Component } from 'react';

class ErrorBoundary extends Component {
  render() {
    return this.props.children;
  }
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "ErrorBoundary"
        assert components[0].properties["component_type"] == "class"


class TestCustomHooks:
    def test_export_function_hook(self):
        source = """\
import { useState } from 'react';

export function useAuth() {
  const [user, setUser] = useState(null);
  return { user, setUser };
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source, "src/hooks/useAuth.ts"))
        hooks = [n for n in result.nodes if n.kind == NodeKind.HOOK]
        assert len(hooks) == 1
        assert hooks[0].label == "useAuth"
        assert hooks[0].properties["framework"] == "react"
        assert hooks[0].id == "react:src/hooks/useAuth.ts:hook:useAuth"

    def test_export_const_hook(self):
        source = """\
export const useFetch = (url: string) => {
  const [data, setData] = useState(null);
  return data;
};
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source, "src/hooks/useFetch.ts"))
        hooks = [n for n in result.nodes if n.kind == NodeKind.HOOK]
        assert len(hooks) == 1
        assert hooks[0].label == "useFetch"

    def test_hooks_not_detected_as_components(self):
        source = """\
export function useCounter() {
  return {};
}

export default function CounterPage() {
  const counter = useCounter();
  return <div>{counter}</div>;
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        hooks = [n for n in result.nodes if n.kind == NodeKind.HOOK]
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(hooks) == 1
        assert hooks[0].label == "useCounter"
        assert len(components) == 1
        assert components[0].label == "CounterPage"


class TestRendersEdges:
    def test_jsx_child_tags(self):
        source = """\
import Header from './Header';
import Sidebar from './Sidebar';

export default function Layout(props) {
  return (
    <div>
      <Header title="My App" />
      <Sidebar items={props.items} />
      <main>{props.children}</main>
    </div>
  );
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "Layout"

        renders_edges = [e for e in result.edges if e.kind == EdgeKind.RENDERS]
        assert len(renders_edges) == 2
        targets = {e.target for e in renders_edges}
        assert targets == {"Header", "Sidebar"}
        for edge in renders_edges:
            assert edge.source == "react:src/components/App.tsx:component:Layout"

    def test_no_self_render_edge(self):
        """Components should not have RENDERS edges to themselves."""
        source = """\
export default function Card(props) {
  return <Card>{props.children}</Card>;
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        renders_edges = [e for e in result.edges if e.kind == EdgeKind.RENDERS]
        targets = {e.target for e in renders_edges}
        assert "Card" not in targets


class TestStatelessAndDeterministic:
    def test_deterministic(self):
        source = """\
export default function App() {
  return <Header />;
}
"""
        detector = ReactComponentDetector()
        r1 = detector.detect(_ctx(source))
        r2 = detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert len(r1.edges) == len(r2.edges)
        for n1, n2 in zip(r1.nodes, r2.nodes):
            assert n1.id == n2.id
            assert n1.kind == n2.kind

    def test_stateless(self):
        source1 = """\
export default function Foo() { return <Bar />; }
"""
        source2 = """\
export default function Baz() { return <Qux />; }
"""
        detector = ReactComponentDetector()
        r1 = detector.detect(_ctx(source1))
        r2 = detector.detect(_ctx(source2))
        assert r1.nodes[0].label == "Foo"
        assert r2.nodes[0].label == "Baz"


class TestEdgeCases:
    def test_empty_file(self):
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(""))
        assert result.nodes == []
        assert result.edges == []

    def test_no_components(self):
        source = """\
export function add(a: number, b: number) {
  return a + b;
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        assert result.nodes == []
        assert result.edges == []

    def test_line_numbers_accurate(self):
        source = """\
// line 1
// line 2
// line 3
export default function MyComp() {
  return <div />;
}
"""
        detector = ReactComponentDetector()
        result = detector.detect(_ctx(source))
        assert result.nodes[0].location.line_start == 4

    def test_javascript_language(self):
        source = """\
export default function Widget(props) {
  return <div>hello</div>;
}
"""
        ctx = DetectorContext(
            file_path="src/Widget.jsx",
            language="javascript",
            content=source.encode("utf-8"),
            module_name="test-module",
        )
        detector = ReactComponentDetector()
        result = detector.detect(ctx)
        assert len(result.nodes) == 1
        assert result.nodes[0].label == "Widget"
