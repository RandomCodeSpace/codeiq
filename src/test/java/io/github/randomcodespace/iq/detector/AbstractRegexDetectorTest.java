package io.github.randomcodespace.iq.detector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AbstractRegexDetectorTest {

    /** Concrete test subclass for testing abstract methods. */
    static class TestDetector extends AbstractRegexDetector {
        @Override
        public String getName() {
            return "test-detector";
        }

        @Override
        public Set<String> getSupportedLanguages() {
            return Set.of("java", "python");
        }

        @Override
        public DetectorResult detect(DetectorContext ctx) {
            return DetectorResult.empty();
        }
    }

    private final TestDetector detector = new TestDetector();

    @Test
    void iterLinesWithMultiLineContent() {
        String content = "line one\nline two\nline three";
        List<AbstractRegexDetector.IndexedLine> lines = detector.iterLines(content);

        assertEquals(3, lines.size());
        assertEquals(1, lines.get(0).lineNumber());
        assertEquals("line one", lines.get(0).text());
        assertEquals(2, lines.get(1).lineNumber());
        assertEquals("line two", lines.get(1).text());
        assertEquals(3, lines.get(2).lineNumber());
        assertEquals("line three", lines.get(2).text());
    }

    @Test
    void iterLinesWithEmptyContent() {
        assertTrue(detector.iterLines("").isEmpty());
        assertTrue(detector.iterLines(null).isEmpty());
    }

    @Test
    void iterLinesSingleLine() {
        List<AbstractRegexDetector.IndexedLine> lines = detector.iterLines("hello");
        assertEquals(1, lines.size());
        assertEquals(1, lines.getFirst().lineNumber());
        assertEquals("hello", lines.getFirst().text());
    }

    @Test
    void iterLinesTrailingNewline() {
        List<AbstractRegexDetector.IndexedLine> lines = detector.iterLines("a\nb\n");
        assertEquals(3, lines.size());
        assertEquals("", lines.get(2).text());
    }

    @Test
    void findLineNumberAtVariousOffsets() {
        String content = "abc\ndef\nghi";
        // offset 0 -> line 1 (char 'a')
        assertEquals(1, detector.findLineNumber(content, 0));
        // offset 3 -> line 1 (char '\n')
        assertEquals(1, detector.findLineNumber(content, 3));
        // offset 4 -> line 2 (char 'd', after first newline)
        assertEquals(2, detector.findLineNumber(content, 4));
        // offset 8 -> line 3 (char 'g', after second newline)
        assertEquals(3, detector.findLineNumber(content, 8));
    }

    @Test
    void findLineNumberWithNegativeOffset() {
        assertEquals(1, detector.findLineNumber("abc", -1));
    }

    @Test
    void findLineNumberBeyondContent() {
        assertEquals(2, detector.findLineNumber("a\nb", 100));
    }

    @Test
    void fileNameExtractsJustFilename() {
        var ctx = new DetectorContext("src/main/java/com/app/Foo.java", "java", "");
        assertEquals("Foo.java", detector.fileName(ctx));
    }

    @Test
    void fileNameWithNoDirectory() {
        var ctx = new DetectorContext("Foo.java", "java", "");
        assertEquals("Foo.java", detector.fileName(ctx));
    }

    @Test
    void fileNameWithBackslashes() {
        var ctx = new DetectorContext("src\\main\\Foo.java", "java", "");
        assertEquals("Foo.java", detector.fileName(ctx));
    }

    @Test
    void matchesFilenameWithGlobPatterns() {
        var ctx = new DetectorContext("src/controllers/UserController.java", "java", "");
        assertTrue(detector.matchesFilename(ctx, "*.java"));
        assertTrue(detector.matchesFilename(ctx, "*Controller.java"));
        assertFalse(detector.matchesFilename(ctx, "*.py"));
        assertFalse(detector.matchesFilename(ctx, "*.xml"));
    }

    @Test
    void matchesFilenameMultiplePatterns() {
        var ctx = new DetectorContext("config.yaml", "yaml", "");
        assertTrue(detector.matchesFilename(ctx, "*.yml", "*.yaml"));
        assertFalse(detector.matchesFilename(ctx, "*.json", "*.xml"));
    }
}
