"""Tests for the Svelte component detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.frontend.svelte_components import SvelteComponentDetector
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, file_path: str = "Counter.svelte") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="typescript",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestSvelteComponentDetector:
    def setup_method(self):
        self.detector = SvelteComponentDetector()

    # --- Protocol conformance ---

    def test_name(self):
        assert self.detector.name == "frontend.svelte_components"

    def test_supported_languages(self):
        assert "typescript" in self.detector.supported_languages
        assert "javascript" in self.detector.supported_languages

    # --- Component detection via export let (props) ---

    def test_detect_component_with_props(self):
        source = """\
<script>
  export let count = 0;
  export let label;
</script>

<button on:click>{label}: {count}</button>
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 1
        node = result.nodes[0]
        assert node.kind == NodeKind.COMPONENT
        assert node.label == "Counter"
        assert node.properties["framework"] == "svelte"
        assert "count" in node.properties["props"]
        assert "label" in node.properties["props"]
        assert node.id == "svelte:Counter.svelte:component:Counter"

    # --- Component detection via reactive statements ---

    def test_detect_component_with_reactive(self):
        source = """\
<script>
  let count = 0;
  $: doubled = count * 2;
  $: console.log(doubled);
</script>

<p>{doubled}</p>
"""
        result = self.detector.detect(_ctx(source, "Doubler.svelte"))
        assert len(result.nodes) == 1
        node = result.nodes[0]
        assert node.kind == NodeKind.COMPONENT
        assert node.label == "Doubler"
        assert node.properties["reactive_statements"] == 2

    # --- Component detection via script + HTML template ---

    def test_detect_component_with_script_and_template(self):
        source = """\
<script lang="ts">
  let name = 'world';
</script>

<h1>Hello {name}!</h1>
"""
        result = self.detector.detect(_ctx(source, "Greeting.svelte"))
        assert len(result.nodes) == 1
        node = result.nodes[0]
        assert node.kind == NodeKind.COMPONENT
        assert node.properties["framework"] == "svelte"

    # --- Negative cases ---

    def test_no_detection_for_plain_js(self):
        source = """\
const x = 42;
export default x;
"""
        result = self.detector.detect(_ctx(source, "util.js"))
        assert len(result.nodes) == 0

    def test_no_detection_for_script_only(self):
        """A <script> block alone (no HTML template) is not enough."""
        source = """\
<script>
  let x = 10;
</script>
"""
        result = self.detector.detect(_ctx(source, "notemplate.svelte"))
        assert len(result.nodes) == 0

    # --- ID format ---

    def test_node_id_format(self):
        source = """\
<script>
  export let value;
</script>
<div>{value}</div>
"""
        result = self.detector.detect(_ctx(source, "src/components/Card.svelte"))
        node = result.nodes[0]
        assert node.id == "svelte:src/components/Card.svelte:component:Card"

    # --- Location tracking ---

    def test_location_is_set(self):
        source = """\
<script>
  export let name;
</script>
<p>{name}</p>
"""
        result = self.detector.detect(_ctx(source, "Name.svelte"))
        node = result.nodes[0]
        assert node.location is not None
        assert node.location.file_path == "Name.svelte"
        assert node.location.line_start >= 1

    # --- Determinism ---

    def test_deterministic(self):
        source = """\
<script>
  export let a;
  export let b;
  $: sum = a + b;
</script>
<span>{sum}</span>
"""
        ctx = _ctx(source, "Sum.svelte")
        r1 = self.detector.detect(ctx)
        r2 = self.detector.detect(ctx)
        assert len(r1.nodes) == len(r2.nodes)
        assert r1.nodes[0].id == r2.nodes[0].id
        assert r1.nodes[0].properties == r2.nodes[0].properties

    # --- Combined patterns ---

    def test_component_with_all_patterns(self):
        source = """\
<script lang="ts">
  export let items = [];
  export let filter = '';
  $: filtered = items.filter(i => i.includes(filter));
</script>

<ul>
  {#each filtered as item}
    <li>{item}</li>
  {/each}
</ul>
"""
        result = self.detector.detect(_ctx(source, "FilterList.svelte"))
        assert len(result.nodes) == 1
        node = result.nodes[0]
        assert node.properties["framework"] == "svelte"
        assert set(node.properties["props"]) == {"items", "filter"}
        assert node.properties["reactive_statements"] == 1

    # --- Statelessness ---

    def test_stateless(self):
        """Running on different files does not carry over state."""
        src_a = "<script>\nexport let x;\n</script>\n<div>{x}</div>"
        src_b = "<script>\nexport let y;\n</script>\n<p>{y}</p>"
        ra = self.detector.detect(_ctx(src_a, "A.svelte"))
        rb = self.detector.detect(_ctx(src_b, "B.svelte"))
        assert len(ra.nodes) == 1
        assert len(rb.nodes) == 1
        assert ra.nodes[0].id != rb.nodes[0].id
        assert ra.nodes[0].properties["props"] == ["x"]
        assert rb.nodes[0].properties["props"] == ["y"]
