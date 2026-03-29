package io.github.randomcodespace.iq.detector.generic;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class GenericImportsDetectorTest {
    private final GenericImportsDetector d = new GenericImportsDetector();
    @Test void detectsRubyClass() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("ruby", "require 'json'\nclass User < ActiveRecord::Base\ndef name; end\nend"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatchOnUnsupported() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("java", "class Foo {}")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("ruby", "require 'a'\nclass X\ndef y; end\nend")); }
}
