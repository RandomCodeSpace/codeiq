package io.github.randomcodespace.iq.detector.frontend;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class SvelteComponentDetectorTest {
    private final SvelteComponentDetector d = new SvelteComponentDetector();
    @Test void detectsSvelteWithProps() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("components/Counter.svelte", "svelte", "<script>\nexport let count = 0;\n</script>\n<p>{count}</p>"));
        assertEquals(1, r.nodes().size()); assertEquals("Counter", r.nodes().get(0).getLabel());
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("svelte", "const x = 1;")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("svelte", "export let x;\n$: doubled = x * 2;")); }
}
