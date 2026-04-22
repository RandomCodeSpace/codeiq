package io.github.randomcodespace.iq.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for {@link ConfigExplainSubcommand}.
 *
 * <p>Covers the contract promised in Task 9 of the Phase B unified-config plan:
 *
 * <ul>
 *   <li>each leaf field in the merged config is emitted on stdout with its value, source layer, and
 *       source label;
 *   <li>output is deterministic (sorted by field path);
 *   <li>ENV-layer overrides are reflected as {@code ENV};
 *   <li>missing {@code --path} file surfaces a load error on stderr with a non-zero exit, mirroring
 *       {@link ConfigValidateSubcommand}.
 * </ul>
 */
class ConfigExplainSubcommandTest {

    @Test
    void printsProvenanceForEachLeaf(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("codeiq.yml");
        Files.writeString(cfg, "serving:\n  port: 9000\n");

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        ConfigExplainSubcommand cmd =
                new ConfigExplainSubcommand(
                        new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                        new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        cmd.setPath(cfg);
        cmd.setEnv(Map.of("CODEIQ_MCP_LIMITS_PERTOOLTIMEOUTMS", "30000"));

        int rc = cmd.call();

        assertEquals(0, rc, "explain should succeed; stderr=" + errBuf);
        String s = outBuf.toString(StandardCharsets.UTF_8);
        assertTrue(s.contains("serving.port"), "must list serving.port, got: " + s);
        assertTrue(s.contains("9000"), "must show effective value 9000, got: " + s);
        assertTrue(s.contains("PROJECT"), "must show source layer PROJECT, got: " + s);
        assertTrue(
                s.contains("mcp.limits.perToolTimeoutMs"),
                "must list mcp timeout field, got: " + s);
        assertTrue(s.contains("30000"), "must show env-overridden 30000, got: " + s);
        assertTrue(s.contains("ENV"), "must show source layer ENV, got: " + s);
        assertTrue(s.contains("BUILT_IN"), "must show at least one BUILT_IN leaf, got: " + s);
    }

    @Test
    void outputIsDeterministicAndSortedByFieldPath(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("codeiq.yml");
        Files.writeString(cfg, "serving:\n  port: 9000\n");

        String first = runCapture(cfg, Map.of());
        String second = runCapture(cfg, Map.of());

        assertEquals(first, second, "explain must be byte-for-byte deterministic");

        // Verify sort order: scan lines, extract the first column (field path), confirm
        // strictly non-decreasing. We skip the header and divider lines.
        String prev = "";
        for (String line : first.split("\n")) {
            if (line.isBlank() || line.startsWith("FIELD") || line.startsWith("-")) {
                continue;
            }
            String field = line.split("\\s+", 2)[0];
            assertTrue(
                    field.compareTo(prev) >= 0,
                    "fields must be sorted ascending; '" + prev + "' then '" + field + "'");
            prev = field;
        }
    }

    @Test
    void missingExplicitPathFailsWithStderrLoadError(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("does-not-exist.yml");

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        ConfigExplainSubcommand cmd =
                new ConfigExplainSubcommand(
                        new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                        new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        cmd.setPath(cfg);

        int rc = cmd.call();

        assertEquals(1, rc, "missing explicit --path must be a failure, not a silent pass");
        String err = errBuf.toString(StandardCharsets.UTF_8);
        assertTrue(err.contains("Load error"), "stderr should carry a load error, got: " + err);
        assertTrue(
                err.contains(cfg.toString()),
                "stderr should mention the missing path, got: " + err);
        assertFalse(
                outBuf.toString(StandardCharsets.UTF_8).contains("FIELD"),
                "stdout must not carry the explain table when loading failed");
    }

    private static String runCapture(Path path, Map<String, String> env) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        ConfigExplainSubcommand cmd =
                new ConfigExplainSubcommand(
                        new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                        new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        cmd.setPath(path);
        cmd.setEnv(env);
        int rc = cmd.call();
        if (rc != 0) {
            throw new AssertionError("explain failed: stderr=" + errBuf);
        }
        return outBuf.toString(StandardCharsets.UTF_8);
    }
}
