"""Tests for Remix route detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.typescript.remix_routes import RemixRouteDetector
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, path: str = "app/routes/users.tsx", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestRemixRouteDetector:
    def setup_method(self):
        self.detector = RemixRouteDetector()

    # --- Positive tests ---

    def test_detects_loader(self):
        source = """\
import { json } from '@remix-run/node';

export async function loader({ request }: LoaderArgs) {
    const users = await getUsers();
    return json({ users });
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["type"] == "loader"
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["framework"] == "remix"
        assert endpoints[0].properties["route_path"] == "/users"

    def test_detects_sync_loader(self):
        source = """\
export function loader() {
    return { data: "static" };
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["type"] == "loader"

    def test_detects_action(self):
        source = """\
export async function action({ request }: ActionArgs) {
    const formData = await request.formData();
    await createUser(formData);
    return redirect('/users');
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["type"] == "action"
        assert endpoints[0].properties["http_method"] == "POST"

    def test_detects_default_component(self):
        source = """\
export default function UsersPage() {
    const data = useLoaderData();
    return <div>{data.users.map(u => <p>{u.name}</p>)}</div>;
}
"""
        result = self.detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "UsersPage"
        assert components[0].properties["type"] == "component"
        assert components[0].properties["uses_loader_data"] is True

    def test_detects_loader_action_and_component_together(self):
        source = """\
import { json, redirect } from '@remix-run/node';

export async function loader({ request }: LoaderArgs) {
    return json({ items: await getItems() });
}

export async function action({ request }: ActionArgs) {
    await createItem(await request.formData());
    return redirect('/items');
}

export default function ItemsPage() {
    const data = useLoaderData();
    const actionData = useActionData();
    return <div>Items</div>;
}
"""
        result = self.detector.detect(_ctx(source, path="app/routes/items.tsx"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(endpoints) == 2
        assert len(components) == 1
        types = {n.properties["type"] for n in endpoints}
        assert types == {"loader", "action"}
        assert components[0].properties["uses_loader_data"] is True
        assert components[0].properties["uses_action_data"] is True

    def test_derives_route_path_from_filename(self):
        source = """\
export async function loader() { return null; }
"""
        # Test basic route
        result = self.detector.detect(_ctx(source, path="app/routes/blog.tsx"))
        ep = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT][0]
        assert ep.properties["route_path"] == "/blog"

    def test_derives_route_path_with_params(self):
        source = """\
export async function loader() { return null; }
"""
        result = self.detector.detect(_ctx(source, path="app/routes/users.$id.tsx"))
        ep = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT][0]
        assert ep.properties["route_path"] == "/users/:id"

    def test_derives_route_path_index(self):
        source = """\
export async function loader() { return null; }
"""
        result = self.detector.detect(_ctx(source, path="app/routes/_index.tsx"))
        ep = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT][0]
        assert ep.properties["route_path"] == "/"

    def test_derives_nested_route_path(self):
        source = """\
export async function loader() { return null; }
"""
        result = self.detector.detect(_ctx(source, path="app/routes/blog.articles.tsx"))
        ep = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT][0]
        assert ep.properties["route_path"] == "/blog/articles"

    # --- Negative tests ---

    def test_empty_file_returns_nothing(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0

    def test_non_export_functions_ignored(self):
        source = """\
function loader() {
    return { data: "not exported" };
}

function action() {
    return null;
}

function MyComponent() {
    return <div>Not exported</div>;
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_non_route_file_no_route_path(self):
        source = """\
export async function loader() { return null; }
"""
        result = self.detector.detect(_ctx(source, path="src/utils/helper.ts"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert "route_path" not in endpoints[0].properties

    # --- Determinism test ---

    def test_determinism(self):
        source = """\
export async function loader() { return null; }
export async function action() { return null; }
export default function Page() { return <div />; }
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]

    def test_node_id_format(self):
        source = """\
export async function loader() { return null; }
export async function action() { return null; }
export default function MyPage() { return <div />; }
"""
        result = self.detector.detect(_ctx(source, path="app/routes/test.tsx"))
        ids = [n.id for n in result.nodes]
        assert any(i.startswith("remix:app/routes/test.tsx:loader:") for i in ids)
        assert any(i.startswith("remix:app/routes/test.tsx:action:") for i in ids)
        assert "remix:app/routes/test.tsx:component:MyPage" in ids
