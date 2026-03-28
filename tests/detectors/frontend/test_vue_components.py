"""Tests for Vue component and composable detector."""

from code_intelligence.detectors.base import DetectorContext
from code_intelligence.detectors.frontend.vue_components import VueComponentDetector
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, file_path: str = "src/components/App.vue") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="typescript",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestDefineComponent:
    def test_define_component_with_name(self):
        source = """\
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'UserProfile',
  props: {
    userId: String,
  },
  setup(props) {
    return {};
  },
});
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "UserProfile"
        assert components[0].properties["framework"] == "vue"
        assert components[0].properties["api_style"] == "composition"
        assert components[0].id == "vue:src/components/App.vue:component:UserProfile"

    def test_define_component_multiline(self):
        source = """\
export default defineComponent({
  name: 'Dashboard',
  components: { Header, Footer },
  setup() {
    return {};
  },
});
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "Dashboard"


class TestOptionsAPI:
    def test_options_api_with_name(self):
        source = """\
export default {
  name: 'TodoList',
  data() {
    return { items: [] };
  },
  methods: {
    addItem(item) { this.items.push(item); },
  },
};
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "TodoList"
        assert components[0].properties["api_style"] == "options"

    def test_options_api_double_quotes(self):
        source = """\
export default {
  name: "SideNav",
  props: ['items'],
};
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "SideNav"


class TestScriptSetup:
    def test_script_setup_basic(self):
        source = """\
<template>
  <div>{{ message }}</div>
</template>

<script setup>
import { ref } from 'vue';

const message = ref('Hello');
</script>
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source, "src/components/HelloWorld.vue"))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "HelloWorld"
        assert components[0].properties["api_style"] == "script_setup"

    def test_script_setup_lang_ts(self):
        source = """\
<template>
  <div>{{ count }}</div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const count = ref<number>(0);
</script>
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source, "src/components/Counter.vue"))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 1
        assert components[0].label == "Counter"

    def test_script_setup_non_vue_file_no_name(self):
        """If file is not .vue, cannot derive name from script setup."""
        source = """\
<script setup>
const x = 1;
</script>
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source, "src/components/something.ts"))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        assert len(components) == 0


class TestComposables:
    def test_export_function_composable(self):
        source = """\
import { ref } from 'vue';

export function useFetch(url: string) {
  const data = ref(null);
  return { data };
}
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source, "src/composables/useFetch.ts"))
        hooks = [n for n in result.nodes if n.kind == NodeKind.HOOK]
        assert len(hooks) == 1
        assert hooks[0].label == "useFetch"
        assert hooks[0].properties["framework"] == "vue"
        assert hooks[0].id == "vue:src/composables/useFetch.ts:hook:useFetch"

    def test_export_const_composable(self):
        source = """\
export const useAuth = () => {
  return { user: null };
};
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source, "src/composables/useAuth.ts"))
        hooks = [n for n in result.nodes if n.kind == NodeKind.HOOK]
        assert len(hooks) == 1
        assert hooks[0].label == "useAuth"

    def test_multiple_composables(self):
        source = """\
export function useCounter() { return {}; }
export function useToggle() { return {}; }
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source, "src/composables/index.ts"))
        hooks = [n for n in result.nodes if n.kind == NodeKind.HOOK]
        assert len(hooks) == 2
        labels = {h.label for h in hooks}
        assert labels == {"useCounter", "useToggle"}


class TestMixedContent:
    def test_component_and_composable_in_same_file(self):
        source = """\
import { defineComponent, ref } from 'vue';

export function useLocalState() {
  return ref(0);
}

export default defineComponent({
  name: 'MixedWidget',
  setup() {
    const state = useLocalState();
    return { state };
  },
});
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source))
        components = [n for n in result.nodes if n.kind == NodeKind.COMPONENT]
        hooks = [n for n in result.nodes if n.kind == NodeKind.HOOK]
        assert len(components) == 1
        assert components[0].label == "MixedWidget"
        assert len(hooks) == 1
        assert hooks[0].label == "useLocalState"


class TestStatelessAndDeterministic:
    def test_deterministic(self):
        source = """\
export default defineComponent({
  name: 'TestComp',
});
"""
        detector = VueComponentDetector()
        r1 = detector.detect(_ctx(source))
        r2 = detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        for n1, n2 in zip(r1.nodes, r2.nodes):
            assert n1.id == n2.id
            assert n1.kind == n2.kind

    def test_stateless(self):
        source1 = "export default defineComponent({ name: 'Foo' });\n"
        source2 = "export default defineComponent({ name: 'Bar' });\n"
        detector = VueComponentDetector()
        r1 = detector.detect(_ctx(source1))
        r2 = detector.detect(_ctx(source2))
        assert r1.nodes[0].label == "Foo"
        assert r2.nodes[0].label == "Bar"


class TestEdgeCases:
    def test_empty_file(self):
        detector = VueComponentDetector()
        result = detector.detect(_ctx(""))
        assert result.nodes == []

    def test_no_components(self):
        source = "const x = 42;\nexport default x;\n"
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source))
        assert result.nodes == []

    def test_line_numbers_accurate(self):
        source = """\
// line 1
// line 2
// line 3
export default defineComponent({
  name: 'LineTest',
});
"""
        detector = VueComponentDetector()
        result = detector.detect(_ctx(source))
        assert result.nodes[0].location.line_start == 4
