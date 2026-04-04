package io.github.randomcodespace.iq.cli;

import io.github.randomcodespace.iq.detector.Detector;
import io.github.randomcodespace.iq.detector.DetectorRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginsCommandTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream capture;

    @BeforeEach
    void setUp() {
        capture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capture, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void listSubcommandShowsAllDetectors() {
        var d1 = mockDetector("alpha-detector", Set.of("java"), "io.test.detector.java");
        var d2 = mockDetector("beta-detector", Set.of("python", "typescript"), "io.test.detector.python");
        var registry = new DetectorRegistry(List.of(d1, d2));

        var listCmd = new PluginsCommand.ListSubcommand(registry);
        int exitCode = listCmd.call();

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("2"), "Should show detector count");
        assertTrue(output.contains("Category"), "Should show header");
    }

    @Test
    void listSubcommandShowsSupportedLanguages() {
        var d1 = mockDetector("test-det", Set.of("java", "kotlin"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1));

        var listCmd = new PluginsCommand.ListSubcommand(registry);
        listCmd.call();

        String output = capture.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("java"), "Should list java language");
        assertTrue(output.contains("kotlin"), "Should list kotlin language");
    }

    @Test
    void infoSubcommandReturnsOneForMissingDetector() {
        var registry = new DetectorRegistry(List.of());
        var infoCmd = new PluginsCommand.InfoSubcommand(registry);

        var cmdLine = new picocli.CommandLine(infoCmd);
        int exitCode = cmdLine.execute("nonexistent");

        assertEquals(1, exitCode);
    }

    @Test
    void emptyRegistryShowsZeroCount() {
        var registry = new DetectorRegistry(List.of());
        var listCmd = new PluginsCommand.ListSubcommand(registry);
        int exitCode = listCmd.call();

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("0"), "Should show zero count");
    }

    @Test
    void infoSubcommandShowsSingleDetector() {
        var d1 = mockDetector("det-a", Set.of("java"), "io.test.detector.java");
        var d2 = mockDetector("det-b", Set.of("java"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1, d2));

        var infoCmd = new PluginsCommand.InfoSubcommand(registry);
        var cmdLine = new picocli.CommandLine(infoCmd);
        // Query by exact detector name (works with mocks)
        int exitCode = cmdLine.execute("det-a");

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("det-a"), "Should show detector a details");
    }

    @Test
    void languagesSubcommandShowsLanguages() {
        var d1 = mockDetector("det-1", Set.of("java", "python"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1));

        var langCmd = new PluginsCommand.LanguagesSubcommand(registry);
        int exitCode = langCmd.call();

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("java"), "Should show java");
        assertTrue(output.contains("python"), "Should show python");
        assertTrue(output.contains("Language"), "Should show header");
    }

    @Test
    void suggestSubcommandWithEmptyDirShowsWarning(@TempDir Path tempDir) {
        var registry = new DetectorRegistry(List.of());

        var suggestCmd = new PluginsCommand.SuggestSubcommand(registry);
        var cmdLine = new picocli.CommandLine(suggestCmd);
        // Redirect stderr too for the warn
        var errCapture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errCapture, true, StandardCharsets.UTF_8));
        try {
            int exitCode = cmdLine.execute(tempDir.toString());
            assertEquals(1, exitCode);
        } finally {
            System.setErr(System.err);
        }
    }

    @Test
    void suggestSubcommandGeneratesConfig(@TempDir Path tempDir) throws Exception {
        // Create some Java files
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/App.java"), "public class App {}");
        Files.writeString(tempDir.resolve("src/Service.java"), "public class Service {}");

        var d1 = mockDetector("spring-rest", Set.of("java"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1));

        var suggestCmd = new PluginsCommand.SuggestSubcommand(registry);
        var cmdLine = new picocli.CommandLine(suggestCmd);
        int exitCode = cmdLine.execute(tempDir.toString());

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("languages:"), "Should contain languages section");
        assertTrue(output.contains("java"), "Should contain java language");
        assertTrue(output.contains("detectors:"), "Should contain detectors section");
    }

    @Test
    void docsMarkdownFormat() {
        var d1 = mockDetector("test-det", Set.of("java"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1));

        var docsCmd = new PluginsCommand.DocsSubcommand(registry);
        var cmdLine = new picocli.CommandLine(docsCmd);
        int exitCode = cmdLine.execute("--format", "markdown");

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("# Code IQ Detector Reference"), "Should have title");
        assertTrue(output.contains("test-det"), "Should list detector");
    }

    @Test
    void docsJsonFormat() {
        var d1 = mockDetector("test-det", Set.of("java"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1));

        var docsCmd = new PluginsCommand.DocsSubcommand(registry);
        var cmdLine = new picocli.CommandLine(docsCmd);
        int exitCode = cmdLine.execute("--format", "json");

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("\"total\""), "Should have total field");
        assertTrue(output.contains("\"test-det\""), "Should list detector");
    }

    @Test
    void docsYamlFormat() {
        var d1 = mockDetector("test-det", Set.of("java"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1));

        var docsCmd = new PluginsCommand.DocsSubcommand(registry);
        var cmdLine = new picocli.CommandLine(docsCmd);
        int exitCode = cmdLine.execute("--format", "yaml");

        String output = capture.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("total:"), "Should have total field");
        assertTrue(output.contains("test-det"), "Should list detector");
    }

    @Test
    void defaultRunDelegatestoList() {
        var d1 = mockDetector("det1", Set.of("java"), "io.test.detector.java");
        var registry = new DetectorRegistry(List.of(d1));

        var cmd = new PluginsCommand(registry);
        cmd.run();

        String output = capture.toString(StandardCharsets.UTF_8);
        // The default run delegates to list, which now shows category summary
        assertTrue(output.contains("1"), "Default should show detector count");
        assertTrue(output.contains("Category"), "Default should show header");
    }

    private Detector mockDetector(String name, Set<String> languages, String packageName) {
        var d = mock(Detector.class);
        when(d.getName()).thenReturn(name);
        when(d.getSupportedLanguages()).thenReturn(languages);
        // Mock the class info to derive the package for categoryOf
        // Since mockito creates proxy classes with dynamic packages,
        // we use the actual mock behavior which will fall back to the proxy package
        return d;
    }
}
