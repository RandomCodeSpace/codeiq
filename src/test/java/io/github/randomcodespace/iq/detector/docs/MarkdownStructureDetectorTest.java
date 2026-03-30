package io.github.randomcodespace.iq.detector.docs;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class MarkdownStructureDetectorTest {
    private final MarkdownStructureDetector d = new MarkdownStructureDetector();
    @Test void detectsHeadings() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("markdown", "# My Doc\n## Section 1\nSome text\n## Section 2\n[link](other.md)"));
        assertTrue(r.nodes().size() >= 3);
    }
    @Test void noMatch() { assertEquals(1, d.detect(DetectorTestUtils.contextFor("markdown", "plain text")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("markdown", "# Title\n## A\n## B")); }
}
