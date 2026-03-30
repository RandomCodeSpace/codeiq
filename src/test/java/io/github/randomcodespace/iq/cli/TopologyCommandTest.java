package io.github.randomcodespace.iq.cli;

import io.github.randomcodespace.iq.config.CodeIqConfig;
import io.github.randomcodespace.iq.query.TopologyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopologyCommandTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream captureOut;
    private ByteArrayOutputStream captureErr;

    @BeforeEach
    void setUp() {
        captureOut = new ByteArrayOutputStream();
        captureErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captureOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(captureErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void returnsErrorWhenNoCacheExists() {
        var config = new CodeIqConfig();
        var topoService = new TopologyService();
        var cmd = new TopologyCommand(config, topoService);

        var cmdLine = new picocli.CommandLine(cmd);
        int exitCode = cmdLine.execute("/tmp/nonexistent-path-" + System.nanoTime());

        assertEquals(1, exitCode);
        String err = captureErr.toString(StandardCharsets.UTF_8);
        // Should report missing cache
        org.junit.jupiter.api.Assertions.assertTrue(
                err.contains("No analysis cache") || err.contains("Failed"),
                "Should report missing cache, got: " + err);
    }

    @Test
    void helpShowsDescription() {
        var config = new CodeIqConfig();
        var topoService = new TopologyService();
        var cmd = new TopologyCommand(config, topoService);

        var cmdLine = new picocli.CommandLine(cmd);
        int exitCode = cmdLine.execute("--help");

        assertEquals(0, exitCode);
        String output = captureOut.toString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(output.contains("topology"),
                "Help should mention 'topology'");
    }
}
