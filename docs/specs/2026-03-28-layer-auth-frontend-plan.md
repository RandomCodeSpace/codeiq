# Layer Classification, Auth Detection & Frontend Components — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add layer classification (frontend/backend/infra/shared) to all graph nodes, detect authentication/authorization patterns across 6 languages, and detect frontend components (React, Vue, Angular, Svelte).

**Architecture:** Post-detection LayerClassifier sets a `layer` property on every node using ordered deterministic rules. 9 new auth detectors produce GUARD/MIDDLEWARE nodes with PROTECTS edges. 5 frontend detectors produce COMPONENT/HOOK nodes with RENDERS edges. All stateless, pure functions, 100% deterministic.

**Tech Stack:** Python 3.11+, regex-based detection, existing Detector protocol

---

## Task 1: Graph Model — Add New NodeKind and EdgeKind Values

**Files:**
- Modify: `src/code_intelligence/models/graph.py:11-41` (NodeKind enum)
- Modify: `src/code_intelligence/models/graph.py:43-70` (EdgeKind enum)

- [ ] **Step 1: Add 4 new NodeKind values**

Add after `INFRA_RESOURCE = "infra_resource"` (line 40):

```python
    COMPONENT = "component"
    GUARD = "guard"
    MIDDLEWARE = "middleware"
    HOOK = "hook"
```

- [ ] **Step 2: Add 2 new EdgeKind values**

Add after `RECEIVES_FROM = "receives_from"` (line 70):

```python
    PROTECTS = "protects"
    RENDERS = "renders"
```

- [ ] **Step 3: Verify import works**

Run: `python -c "from code_intelligence.models.graph import NodeKind, EdgeKind; print(NodeKind.COMPONENT, NodeKind.GUARD, NodeKind.MIDDLEWARE, NodeKind.HOOK, EdgeKind.PROTECTS, EdgeKind.RENDERS)"`
Expected: `component guard middleware hook protects renders`

- [ ] **Step 4: Run existing tests**

Run: `pytest tests/ -x -q`
Expected: All 113 tests pass (enum additions are backwards-compatible)

- [ ] **Step 5: Commit**

```bash
git add src/code_intelligence/models/graph.py
git commit -m "feat: add COMPONENT, GUARD, MIDDLEWARE, HOOK node kinds and PROTECTS, RENDERS edge kinds"
```

---

## Task 2: Layer Classifier

**Files:**
- Create: `src/code_intelligence/classifiers/__init__.py`
- Create: `src/code_intelligence/classifiers/layer_classifier.py`
- Modify: `src/code_intelligence/analyzer.py:376-380` (insert classifier call)
- Create: `tests/classifiers/test_layer_classifier.py`

- [ ] **Step 1: Create classifier directory**

```bash
mkdir -p src/code_intelligence/classifiers
touch src/code_intelligence/classifiers/__init__.py
mkdir -p tests/classifiers
```

- [ ] **Step 2: Write the failing test**

Create `tests/classifiers/test_layer_classifier.py`:

```python
"""Tests for LayerClassifier deterministic layer assignment."""

from code_intelligence.classifiers.layer_classifier import LayerClassifier
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation


def _node(id: str, kind: NodeKind, file_path: str, **props) -> GraphNode:
    return GraphNode(
        id=id,
        kind=kind,
        label=id,
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
    """Run classifier twice on same input, assert identical output."""
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `pytest tests/classifiers/test_layer_classifier.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'code_intelligence.classifiers.layer_classifier'`

- [ ] **Step 4: Implement LayerClassifier**

Create `src/code_intelligence/classifiers/layer_classifier.py`:

```python
"""Deterministic layer classifier for code intelligence graph nodes."""

from __future__ import annotations

import re
from typing import Sequence

from code_intelligence.models.graph import GraphNode, NodeKind

_FRONTEND_NODE_KINDS = {NodeKind.COMPONENT, NodeKind.HOOK}
_BACKEND_NODE_KINDS = {NodeKind.GUARD, NodeKind.MIDDLEWARE, NodeKind.ENDPOINT, NodeKind.REPOSITORY, NodeKind.DATABASE_CONNECTION, NodeKind.QUERY}
_INFRA_NODE_KINDS = {NodeKind.INFRA_RESOURCE, NodeKind.AZURE_RESOURCE, NodeKind.AZURE_FUNCTION}
_INFRA_LANGUAGES = {"terraform", "bicep", "dockerfile"}
_SHARED_NODE_KINDS = {NodeKind.CONFIG_FILE, NodeKind.CONFIG_KEY, NodeKind.CONFIG_DEFINITION}

_FRONTEND_PATH_RE = re.compile(
    r"(?:^|/)(?:src/)?(?:components|pages|views|app/ui|public)/",
)
_BACKEND_PATH_RE = re.compile(
    r"(?:^|/)(?:src/)?(?:server|api|controllers|services|routes|handlers)/",
)
_FRONTEND_EXT_RE = re.compile(r"\.(?:tsx|jsx)$")

_FRONTEND_FRAMEWORKS = {"react", "vue", "angular", "svelte", "nextjs"}
_BACKEND_FRAMEWORKS = {"express", "nestjs", "flask", "django", "fastapi", "spring"}


class LayerClassifier:
    """Assigns a deterministic 'layer' property to every graph node.

    Rules are evaluated in order; first match wins.
    """

    def classify(self, nodes: Sequence[GraphNode]) -> None:
        for node in nodes:
            node.properties["layer"] = self._classify_one(node)

    def _classify_one(self, node: GraphNode) -> str:
        # 1. Node kind — frontend
        if node.kind in _FRONTEND_NODE_KINDS:
            return "frontend"

        # 2. Node kind — backend (guard/middleware)
        if node.kind in _BACKEND_NODE_KINDS:
            return "backend"

        # 3. Node kind — infra
        if node.kind in _INFRA_NODE_KINDS:
            return "infra"

        # 4. Language — infra
        lang = node.properties.get("language", "")
        if lang in _INFRA_LANGUAGES:
            return "infra"

        # 5. File extension — .tsx/.jsx → frontend
        file_path = ""
        if node.location:
            file_path = node.location.file_path
        if _FRONTEND_EXT_RE.search(file_path):
            return "frontend"

        # 6. File path — frontend directories
        if _FRONTEND_PATH_RE.search(file_path):
            return "frontend"

        # 7. File path — backend directories
        if _BACKEND_PATH_RE.search(file_path):
            return "backend"

        # 8. Framework property
        fw = node.properties.get("framework", "")
        if fw in _FRONTEND_FRAMEWORKS:
            return "frontend"
        if fw in _BACKEND_FRAMEWORKS:
            return "backend"

        # 9. Shared config
        if node.kind in _SHARED_NODE_KINDS:
            return "shared"

        # 10. Fallback
        return "unknown"
```

- [ ] **Step 5: Run tests**

Run: `pytest tests/classifiers/test_layer_classifier.py -v`
Expected: All 9 tests pass

- [ ] **Step 6: Integrate into analyzer pipeline**

In `src/code_intelligence/analyzer.py`, add between the aggregation loop (line ~374) and the linkers call (line ~379):

After the line `# 6. Run cross-file linkers` comment block, insert before `_report("🔗 Linking cross-file relationships…")`:

```python
        # ----------------------------------------------------------
        # 5b. Classify layers
        # ----------------------------------------------------------
        from code_intelligence.classifiers.layer_classifier import LayerClassifier
        LayerClassifier().classify(list(builder._store.all_nodes()))
```

- [ ] **Step 7: Run full test suite**

Run: `pytest tests/ -x -q`
Expected: All tests pass

- [ ] **Step 8: Commit**

```bash
git add src/code_intelligence/classifiers/ tests/classifiers/ src/code_intelligence/analyzer.py
git commit -m "feat: add LayerClassifier — deterministic frontend/backend/infra/shared classification"
```

---

## Task 3: Spring Security Detector (Java Auth)

**Files:**
- Create: `src/code_intelligence/detectors/java/spring_security.py`
- Create: `tests/detectors/java/test_spring_security.py`

- [ ] **Step 1: Write the failing test**

Create `tests/detectors/java/test_spring_security.py`:

```python
"""Tests for Spring Security detector."""

from code_intelligence.detectors.java.spring_security import SpringSecurityDetector
from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "SecurityConfig.java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language="java",
        content=content.encode(), module_name="com.example",
    )


def test_secured_annotation():
    ctx = _ctx('@Secured("ROLE_ADMIN")\npublic void deleteUser() {}')
    result = SpringSecurityDetector().detect(ctx)
    assert any(n.kind == NodeKind.GUARD for n in result.nodes)
    assert any("ROLE_ADMIN" in str(n.properties.get("roles", [])) for n in result.nodes)


def test_pre_authorize():
    ctx = _ctx("@PreAuthorize(\"hasRole('USER')\")\npublic List<User> getUsers() {}")
    result = SpringSecurityDetector().detect(ctx)
    assert any(n.kind == NodeKind.GUARD for n in result.nodes)


def test_enable_web_security():
    ctx = _ctx("@EnableWebSecurity\npublic class SecurityConfig {}")
    result = SpringSecurityDetector().detect(ctx)
    assert any(n.kind == NodeKind.GUARD for n in result.nodes)


def test_security_filter_chain():
    ctx = _ctx('public SecurityFilterChain filterChain(HttpSecurity http) {\n    http.authorizeHttpRequests()\n}')
    result = SpringSecurityDetector().detect(ctx)
    assert any(n.kind == NodeKind.GUARD for n in result.nodes)


def test_no_security_returns_empty():
    ctx = _ctx("public class UserService { public void getUser() {} }")
    result = SpringSecurityDetector().detect(ctx)
    assert len(result.nodes) == 0


def test_determinism():
    ctx = _ctx('@Secured("ROLE_ADMIN")\n@RolesAllowed({"ROLE_USER"})\npublic class Api {}')
    r1 = SpringSecurityDetector().detect(ctx)
    r2 = SpringSecurityDetector().detect(ctx)
    assert len(r1.nodes) == len(r2.nodes)
    assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/detectors/java/test_spring_security.py -v`
Expected: FAIL — ModuleNotFoundError

- [ ] **Step 3: Implement SpringSecurityDetector**

Create `src/code_intelligence/detectors/java/spring_security.py`:

```python
"""Spring Security detector for auth annotations and filter chains."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_SECURED_RE = re.compile(r'@Secured\(\s*["\{]([^")}\]]+)["\}]\s*\)')
_PRE_AUTH_RE = re.compile(r"@PreAuthorize\(\s*\"([^\"]+)\"\s*\)")
_ROLES_ALLOWED_RE = re.compile(r'@RolesAllowed\(\s*\{?\s*"([^"]+)"')
_ENABLE_SECURITY_RE = re.compile(r"@Enable(?:Web|Method|Global\w*)Security")
_FILTER_CHAIN_RE = re.compile(r"SecurityFilterChain\s+\w+\(")
_HTTP_SECURITY_RE = re.compile(r"\.authorizeHttpRequests\(|\.authorizeRequests\(|\.oauth2Login\(|\.csrf\(")

_ROLE_EXTRACT_RE = re.compile(r"ROLE_\w+|hasRole\(['\"](\w+)['\"]\)|hasAuthority\(['\"](\w+)['\"]\)")


class SpringSecurityDetector:
    """Detects Spring Security annotations and configuration."""

    name: str = "spring_security"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")
        lines = text.split("\n")
        filepath = ctx.file_path

        for i, line in enumerate(lines):
            # @Secured
            m = _SECURED_RE.search(line)
            if m:
                roles = [r.strip().strip('"') for r in m.group(1).split(",")]
                result.nodes.append(GraphNode(
                    id=f"auth:{filepath}:secured:{i+1}",
                    kind=NodeKind.GUARD,
                    label=f"@Secured({', '.join(roles)})",
                    fqn=f"@Secured",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath, line_start=i + 1),
                    properties={"auth_type": "spring_security", "roles": roles, "auth_required": True},
                ))

            # @PreAuthorize
            m = _PRE_AUTH_RE.search(line)
            if m:
                expr = m.group(1)
                roles = [g1 or g2 for g1, g2 in _ROLE_EXTRACT_RE.findall(expr) if g1 or g2]
                result.nodes.append(GraphNode(
                    id=f"auth:{filepath}:preauth:{i+1}",
                    kind=NodeKind.GUARD,
                    label=f"@PreAuthorize({expr})",
                    fqn=f"@PreAuthorize",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath, line_start=i + 1),
                    properties={"auth_type": "spring_security", "expression": expr, "roles": roles, "auth_required": True},
                ))

            # @RolesAllowed
            m = _ROLES_ALLOWED_RE.search(line)
            if m:
                roles = [r.strip().strip('"') for r in re.findall(r'"([^"]+)"', line)]
                result.nodes.append(GraphNode(
                    id=f"auth:{filepath}:rolesallowed:{i+1}",
                    kind=NodeKind.GUARD,
                    label=f"@RolesAllowed({', '.join(roles)})",
                    fqn=f"@RolesAllowed",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath, line_start=i + 1),
                    properties={"auth_type": "spring_security", "roles": roles, "auth_required": True},
                ))

            # @EnableWebSecurity / @EnableMethodSecurity
            if _ENABLE_SECURITY_RE.search(line):
                result.nodes.append(GraphNode(
                    id=f"auth:{filepath}:security_config:{i+1}",
                    kind=NodeKind.GUARD,
                    label="SecurityConfig",
                    fqn=f"{filepath}:SecurityConfig",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath, line_start=i + 1),
                    properties={"auth_type": "spring_security", "config": True},
                ))

            # SecurityFilterChain
            if _FILTER_CHAIN_RE.search(line):
                result.nodes.append(GraphNode(
                    id=f"auth:{filepath}:filterchain:{i+1}",
                    kind=NodeKind.GUARD,
                    label="SecurityFilterChain",
                    fqn=f"{filepath}:SecurityFilterChain",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath, line_start=i + 1),
                    properties={"auth_type": "spring_security", "filter_chain": True, "auth_required": True},
                ))

        return result
```

- [ ] **Step 4: Run tests**

Run: `pytest tests/detectors/java/test_spring_security.py -v`
Expected: All 6 tests pass

- [ ] **Step 5: Commit**

```bash
git add src/code_intelligence/detectors/java/spring_security.py tests/detectors/java/test_spring_security.py
git commit -m "feat: add Spring Security detector — @Secured, @PreAuthorize, SecurityFilterChain"
```

---

## Task 4: Django Auth Detector

**Files:**
- Create: `src/code_intelligence/detectors/python/django_auth.py`
- Create: `tests/detectors/python/test_django_auth.py`

Implementation follows the same pattern as Task 3. Detect:
- `@login_required`, `@permission_required("perm")`, `@user_passes_test(fn)`
- `LoginRequiredMixin`, `PermissionRequiredMixin` in class inheritance
- Produce GUARD nodes with `auth_type: "django"`, `permissions`, `auth_required: True`

---

## Task 5: FastAPI Auth Detector

**Files:**
- Create: `src/code_intelligence/detectors/python/fastapi_auth.py`
- Create: `tests/detectors/python/test_fastapi_auth.py`

Detect:
- `Depends(get_current_user)`, `Security(oauth2_scheme)`, `HTTPBearer()`, `OAuth2PasswordBearer`
- Produce GUARD nodes with `auth_type: "fastapi"`, `auth_flow`

---

## Task 6: NestJS Guards Detector

**Files:**
- Create: `src/code_intelligence/detectors/typescript/nestjs_guards.py`
- Create: `tests/detectors/typescript/test_nestjs_guards.py`

Detect:
- `@UseGuards(JwtAuthGuard)`, `@Roles('admin')`, `canActivate()` implementations
- Produce GUARD nodes with `auth_type: "nestjs_guard"`, PROTECTS edges

---

## Task 7: Passport/JWT Detector

**Files:**
- Create: `src/code_intelligence/detectors/typescript/passport_jwt.py`
- Create: `tests/detectors/typescript/test_passport_jwt.py`

Detect:
- `passport.use(new JwtStrategy())`, `passport.authenticate('jwt')`, `jwt.verify()`, `express-jwt`
- Produce GUARD and MIDDLEWARE nodes with `auth_type: "passport"|"jwt"`

---

## Task 8: Kubernetes RBAC Detector

**Files:**
- Create: `src/code_intelligence/detectors/config/kubernetes_rbac.py`
- Create: `tests/detectors/config/test_kubernetes_rbac.py`

Detect K8s YAML with kind in {Role, ClusterRole, RoleBinding, ClusterRoleBinding, ServiceAccount}:
- Produce GUARD nodes with `auth_type: "k8s_rbac"`, `rules` property
- PROTECTS edges from role → service account via bindings

---

## Task 9: LDAP Auth Detector

**Files:**
- Create: `src/code_intelligence/detectors/auth/__init__.py`
- Create: `src/code_intelligence/detectors/auth/ldap_auth.py`
- Create: `tests/detectors/auth/test_ldap_auth.py`

Multi-language (java, python, typescript, csharp). Detect:
- Java: `LdapContextSource`, `ActiveDirectoryLdapAuthenticationProvider`
- Python: `ldap3.Connection`, `AUTH_LDAP_SERVER_URI`
- TypeScript: `ldapjs`, `passport-ldapauth`
- C#: `System.DirectoryServices`
- Produce GUARD nodes with `auth_type: "ldap"`, `server_uri`

---

## Task 10: TLS/Certificate/Azure AD Auth Detector

**Files:**
- Create: `src/code_intelligence/detectors/auth/certificate_auth.py`
- Create: `tests/detectors/auth/test_certificate_auth.py`

Multi-language + config. Detect mTLS, X.509, TLS config, Azure AD/MSAL patterns:
- Produce GUARD nodes with `auth_type: "mtls"|"x509"|"tls_config"|"azure_ad"`
- Properties: `cert_path`, `tenant_id`, `auth_flow`

---

## Task 11: Cookie/Session/Header Auth Detector

**Files:**
- Create: `src/code_intelligence/detectors/auth/session_header_auth.py`
- Create: `tests/detectors/auth/test_session_header_auth.py`

Multi-language. Detect session, header, API key, CSRF patterns:
- Produce GUARD/MIDDLEWARE nodes with `auth_type: "session"|"header"|"api_key"|"csrf"`

---

## Task 12: React Component Detector

**Files:**
- Create: `src/code_intelligence/detectors/frontend/__init__.py`
- Create: `src/code_intelligence/detectors/frontend/react_components.py`
- Create: `tests/detectors/frontend/test_react_components.py`

Detect:
- Function components: `export default function Comp()`, `export const Comp = () =>`
- Class components: `extends React.Component`
- Hooks: `useState`, `useEffect`, custom `use*`
- Produce COMPONENT nodes with `framework: "react"`, HOOK nodes, RENDERS edges

---

## Task 13: Vue Component Detector

**Files:**
- Create: `src/code_intelligence/detectors/frontend/vue_components.py`
- Create: `tests/detectors/frontend/test_vue_components.py`

Detect: `defineComponent`, `export default { name: }`, `<script setup>` patterns
- Produce COMPONENT nodes with `framework: "vue"`, HOOK nodes for composables

---

## Task 14: Angular Component Detector

**Files:**
- Create: `src/code_intelligence/detectors/frontend/angular_components.py`
- Create: `tests/detectors/frontend/test_angular_components.py`

Detect: `@Component({})`, `@Injectable()`, `@Directive()`, `@Pipe()`, `@NgModule()`
- Produce COMPONENT and MIDDLEWARE nodes with `framework: "angular"`

---

## Task 15: Svelte Component Detector

**Files:**
- Create: `src/code_intelligence/detectors/frontend/svelte_components.py`
- Create: `tests/detectors/frontend/test_svelte_components.py`

Detect: `export let`, `$:` reactive, event patterns in .svelte files
- Produce COMPONENT nodes with `framework: "svelte"`

---

## Task 16: Frontend Route Detector

**Files:**
- Create: `src/code_intelligence/detectors/frontend/frontend_routes.py`
- Create: `tests/detectors/frontend/test_frontend_routes.py`

Detect: React Router, Vue Router, Next.js pages, Angular routes
- Produce ENDPOINT nodes with `protocol: "frontend_route"`, RENDERS edges

---

## Task 17: Infrastructure — Registry, Analyzer, Config Updates

**Files:**
- Modify: `src/code_intelligence/detectors/registry.py:103` (add 15 new modules)
- Modify: `src/code_intelligence/analyzer.py:31-37` (add vue, svelte to _STRUCTURED_LANGUAGES)
- Modify: `src/code_intelligence/analyzer.py:54-74` (add vue/svelte parsing passthrough)
- Modify: `src/code_intelligence/discovery/file_discovery.py` (add .vue, .svelte to _EXTENSION_MAP)
- Modify: `src/code_intelligence/config.py` (add .vue, .svelte to include_extensions)

- [ ] **Step 1: Update registry.py**

Add after line 103 (after `"code_intelligence.detectors.proto.proto_structure"`):

```python
            # Auth detectors
            "code_intelligence.detectors.java.spring_security",
            "code_intelligence.detectors.python.django_auth",
            "code_intelligence.detectors.python.fastapi_auth",
            "code_intelligence.detectors.typescript.nestjs_guards",
            "code_intelligence.detectors.typescript.passport_jwt",
            "code_intelligence.detectors.config.kubernetes_rbac",
            "code_intelligence.detectors.auth.ldap_auth",
            "code_intelligence.detectors.auth.certificate_auth",
            "code_intelligence.detectors.auth.session_header_auth",
            # Frontend detectors
            "code_intelligence.detectors.frontend.react_components",
            "code_intelligence.detectors.frontend.vue_components",
            "code_intelligence.detectors.frontend.angular_components",
            "code_intelligence.detectors.frontend.svelte_components",
            "code_intelligence.detectors.frontend.frontend_routes",
```

- [ ] **Step 2: Update extension map and config**

Add to `_EXTENSION_MAP` in `file_discovery.py`:
```python
    ".vue": "vue",
    ".svelte": "svelte",
```

Add to `include_extensions` in `config.py`:
```python
    ".vue", ".svelte",
```

- [ ] **Step 3: Update analyzer structured languages and parser**

Add `"vue"` and `"svelte"` to `_STRUCTURED_LANGUAGES` set in `analyzer.py`.

Add parsing passthrough in `_parse_structured()`:
```python
    elif language == "vue":
        return {"type": "vue", "file": file_path, "data": content.decode("utf-8", errors="replace")}
    elif language == "svelte":
        return {"type": "svelte", "file": file_path, "data": content.decode("utf-8", errors="replace")}
```

- [ ] **Step 4: Verify all detectors load**

Run: `python -c "from code_intelligence.detectors.registry import DetectorRegistry; r = DetectorRegistry(); r.load_builtin_detectors(); print(f'{len(r.all_detectors())} detectors loaded')"`
Expected: `72 detectors loaded` (58 existing + 14 new, K8s RBAC shares yaml language with existing K8s detector)

- [ ] **Step 5: Run full test suite**

Run: `pytest tests/ -x -q`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/code_intelligence/detectors/registry.py src/code_intelligence/analyzer.py src/code_intelligence/discovery/file_discovery.py src/code_intelligence/config.py
git commit -m "feat: register 14 new detectors, add .vue/.svelte support, integrate layer classifier"
```

---

## Task 18: Benchmark & Consistency Verification

- [ ] **Step 1: Run benchmarks on 3 diverse projects, twice each**

```bash
# Clear caches
find ~/projects/testDir -name ".code_intelligence_cache*" -delete

# Round 1
time code-intelligence analyze ~/projects/testDir/contoso-real-estate --full -j 8
time code-intelligence analyze ~/projects/testDir/spring-boot --full -j 8
time code-intelligence analyze ~/projects/testDir/benchmark/kubernetes --full -j 8

# Clear and Round 2
find ~/projects/testDir -name ".code_intelligence_cache*" -delete
time code-intelligence analyze ~/projects/testDir/contoso-real-estate --full -j 8
time code-intelligence analyze ~/projects/testDir/spring-boot --full -j 8
time code-intelligence analyze ~/projects/testDir/benchmark/kubernetes --full -j 8
```

Expected: Identical node/edge counts between rounds for all 3 projects.

- [ ] **Step 2: Verify layer property on nodes**

```python
python3 -c "
from code_intelligence.analyzer import Analyzer
from pathlib import Path

result = Analyzer().run(Path('~/projects/testDir/contoso-real-estate').expanduser(), incremental=False)
layers = {}
for node in result.graph.all_nodes():
    layer = node.properties.get('layer', 'MISSING')
    layers[layer] = layers.get(layer, 0) + 1
for layer, count in sorted(layers.items()):
    print(f'  {layer}: {count}')
print(f'Total: {sum(layers.values())} nodes')
assert 'MISSING' not in layers, 'Some nodes missing layer property!'
"
```

- [ ] **Step 3: Verify new node types appear**

Check for GUARD, COMPONENT, MIDDLEWARE, HOOK nodes in spring-boot and contoso-real-estate results.

- [ ] **Step 4: Final commit and push**

```bash
git push
```
