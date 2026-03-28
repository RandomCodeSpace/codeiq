"""Tests for the frontend route detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.frontend.frontend_routes import FrontendRouteDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, file_path: str = "routes.tsx") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="typescript",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestFrontendRouteDetector:
    def setup_method(self):
        self.detector = FrontendRouteDetector()

    # --- Protocol conformance ---

    def test_name(self):
        assert self.detector.name == "frontend.frontend_routes"

    def test_supported_languages(self):
        assert "typescript" in self.detector.supported_languages
        assert "javascript" in self.detector.supported_languages

    # =========================================================================
    # React Router
    # =========================================================================

    def test_react_route_with_component(self):
        source = """\
import { Route } from 'react-router-dom';

<Route path="/users" component={UserList}>
<Route path="/users/:id" component={UserDetail}>
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        paths = {n.properties["route_path"] for n in endpoints}
        assert paths == {"/users", "/users/:id"}
        assert all(n.properties["framework"] == "react" for n in endpoints)
        assert all(n.properties["protocol"] == "frontend_route" for n in endpoints)

        # RENDERS edges
        renders = [e for e in result.edges if e.kind == EdgeKind.RENDERS]
        assert len(renders) == 2
        targets = {e.target for e in renders}
        assert "UserList" in targets
        assert "UserDetail" in targets

    def test_react_route_with_element(self):
        source = """\
<Route path="/dashboard" element={<Dashboard />} />
<Route path="/settings" element={<Settings />} />
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        renders = [e for e in result.edges if e.kind == EdgeKind.RENDERS]
        assert len(renders) == 2
        targets = {e.target for e in renders}
        assert "Dashboard" in targets
        assert "Settings" in targets

    def test_react_route_bare(self):
        source = """\
<Route path="/about">
  <About />
</Route>
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["route_path"] == "/about"
        assert endpoints[0].properties["framework"] == "react"

    def test_react_route_no_duplicate(self):
        """A route with component= should not also appear as a bare route."""
        source = """\
<Route path="/home" component={Home}>
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1

    def test_react_route_id_format(self):
        source = '<Route path="/login" component={Login}>\n'
        result = self.detector.detect(_ctx(source, "src/App.tsx"))
        node = result.nodes[0]
        assert node.id == "route:src/App.tsx:react:/login"

    # =========================================================================
    # Vue Router
    # =========================================================================

    def test_vue_router_routes(self):
        source = """\
import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', component: Home },
  { path: '/about', component: About },
  { path: '/contact', component: ContactPage },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});
"""
        result = self.detector.detect(_ctx(source, "router.ts"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 3
        paths = {n.properties["route_path"] for n in endpoints}
        assert paths == {"/", "/about", "/contact"}
        assert all(n.properties["framework"] == "vue" for n in endpoints)

        renders = [e for e in result.edges if e.kind == EdgeKind.RENDERS]
        assert len(renders) == 3
        targets = {e.target for e in renders}
        assert targets == {"Home", "About", "ContactPage"}

    def test_vue_router_without_createRouter_ignored(self):
        """Path objects without createRouter or routes: are not treated as Vue routes."""
        source = """\
const config = { path: '/foo', component: Foo };
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 0

    def test_vue_router_with_routes_array_only(self):
        source = """\
export const routes: RouteRecordRaw[] = [
  { path: '/dashboard', component: Dashboard },
];

export default {
  routes: [
    { path: '/profile', component: Profile },
  ],
};
"""
        # routes: [ is present, so Vue detection triggers
        result = self.detector.detect(_ctx(source, "router/index.ts"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 2

    # =========================================================================
    # Next.js file-based routing
    # =========================================================================

    def test_nextjs_pages_index(self):
        result = self.detector.detect(_ctx("export default function Home() {}", "pages/index.tsx"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        node = endpoints[0]
        assert node.properties["framework"] == "nextjs"
        assert node.properties["route_path"] == "/"
        assert node.properties["protocol"] == "frontend_route"

    def test_nextjs_pages_nested(self):
        result = self.detector.detect(_ctx("export default function Users() {}", "pages/users/index.tsx"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["route_path"] == "/users"

    def test_nextjs_pages_dynamic(self):
        result = self.detector.detect(_ctx("export default function UserDetail() {}", "pages/users/[id].tsx"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["route_path"] == "/users/[id]"

    def test_nextjs_pages_simple_page(self):
        result = self.detector.detect(_ctx("export default function About() {}", "pages/about.tsx"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["route_path"] == "/about"

    def test_nextjs_app_router(self):
        result = self.detector.detect(_ctx("export default function Page() {}", "app/dashboard/page.tsx"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["route_path"] == "/dashboard"
        assert endpoints[0].properties["framework"] == "nextjs"

    def test_nextjs_app_router_nested(self):
        result = self.detector.detect(
            _ctx("export default function Page() {}", "app/settings/profile/page.tsx")
        )
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["route_path"] == "/settings/profile"

    def test_nextjs_non_page_file_ignored(self):
        """A regular .tsx file outside pages/ or app/ should not be detected."""
        result = self.detector.detect(_ctx("const x = 1;", "src/components/Button.tsx"))
        assert len(result.nodes) == 0

    # =========================================================================
    # Angular Router
    # =========================================================================

    def test_angular_router_routes(self):
        source = """\
import { RouterModule } from '@angular/router';

const routes: Routes = [
  { path: 'home', component: HomeComponent },
  { path: 'users', component: UsersComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
})
export class AppRoutingModule {}
"""
        result = self.detector.detect(_ctx(source, "app-routing.module.ts"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        paths = {n.properties["route_path"] for n in endpoints}
        assert paths == {"home", "users"}
        assert all(n.properties["framework"] == "angular" for n in endpoints)

        renders = [e for e in result.edges if e.kind == EdgeKind.RENDERS]
        assert len(renders) == 2
        targets = {e.target for e in renders}
        assert "HomeComponent" in targets
        assert "UsersComponent" in targets

    def test_angular_forChild(self):
        source = """\
const routes: Routes = [
  { path: 'settings', component: SettingsComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class SettingsModule {}
"""
        result = self.detector.detect(_ctx(source, "settings.module.ts"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["route_path"] == "settings"

    def test_angular_without_router_module_ignored(self):
        source = """\
const routes = [
  { path: 'admin', component: AdminComponent },
];
"""
        result = self.detector.detect(_ctx(source))
        # No RouterModule.forRoot/forChild -> no angular routes
        angular = [
            n for n in result.nodes
            if n.properties.get("framework") == "angular"
        ]
        assert len(angular) == 0

    # =========================================================================
    # Mixed / Edge cases
    # =========================================================================

    def test_empty_file(self):
        result = self.detector.detect(_ctx("", "empty.tsx"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_deterministic(self):
        source = '<Route path="/a" component={A}>\n<Route path="/b" component={B}>\n'
        ctx = _ctx(source, "routes.tsx")
        r1 = self.detector.detect(ctx)
        r2 = self.detector.detect(ctx)
        assert len(r1.nodes) == len(r2.nodes)
        for n1, n2 in zip(r1.nodes, r2.nodes):
            assert n1.id == n2.id

    def test_stateless(self):
        src_a = '<Route path="/x" component={X}>\n'
        src_b = '<Route path="/y" component={Y}>\n'
        ra = self.detector.detect(_ctx(src_a, "a.tsx"))
        rb = self.detector.detect(_ctx(src_b, "b.tsx"))
        assert len(ra.nodes) == 1
        assert len(rb.nodes) == 1
        assert ra.nodes[0].id != rb.nodes[0].id

    def test_location_is_set(self):
        source = '\n\n<Route path="/late" component={Late}>\n'
        result = self.detector.detect(_ctx(source, "late.tsx"))
        node = result.nodes[0]
        assert node.location is not None
        assert node.location.file_path == "late.tsx"
        assert node.location.line_start == 3
