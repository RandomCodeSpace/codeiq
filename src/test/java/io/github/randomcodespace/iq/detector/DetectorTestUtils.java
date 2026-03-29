package io.github.randomcodespace.iq.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared test utilities for detector tests.
 */
public final class DetectorTestUtils {

    private DetectorTestUtils() {
        // utility class
    }

    public static DetectorContext contextFor(String language, String content) {
        return new DetectorContext("test." + extensionFor(language), language, content);
    }

    public static DetectorContext contextFor(String filePath, String language, String content) {
        return new DetectorContext(filePath, language, content);
    }

    public static void assertDeterministic(Detector detector, DetectorContext ctx) {
        DetectorResult r1 = detector.detect(ctx);
        DetectorResult r2 = detector.detect(ctx);
        assertEquals(r1.nodes().size(), r2.nodes().size(),
                "Detector %s produced different node counts on repeated invocation".formatted(detector.getName()));
        assertEquals(r1.edges().size(), r2.edges().size(),
                "Detector %s produced different edge counts on repeated invocation".formatted(detector.getName()));
    }

    private static String extensionFor(String language) {
        return switch (language) {
            case "java" -> "java";
            case "python" -> "py";
            case "typescript" -> "ts";
            case "javascript" -> "js";
            case "yaml" -> "yaml";
            case "json" -> "json";
            case "go" -> "go";
            case "rust" -> "rs";
            case "kotlin" -> "kt";
            case "csharp" -> "cs";
            default -> "txt";
        };
    }
}
