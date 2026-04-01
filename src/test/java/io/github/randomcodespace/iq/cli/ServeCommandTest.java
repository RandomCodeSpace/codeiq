package io.github.randomcodespace.iq.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServeCommandTest {

    @Test
    void commandNameIsServe() {
        var cmd = new ServeCommand();
        var cmdLine = new CommandLine(cmd);
        assertEquals("serve", cmdLine.getCommandName());
    }

    @Test
    void defaultPortIs8080() {
        var cmd = new ServeCommand();
        // After picocli parsing with defaults
        var cmdLine = new CommandLine(cmd);
        cmdLine.parseArgs();  // Use defaults
        assertEquals(8080, cmd.getPort());
    }

    @Test
    void defaultHostIsAllInterfaces() {
        var cmd = new ServeCommand();
        var cmdLine = new CommandLine(cmd);
        cmdLine.parseArgs();
        assertEquals("0.0.0.0", cmd.getHost());
    }

    @Test
    void pathDefaultsToCurrentDir() {
        var cmd = new ServeCommand();
        var cmdLine = new CommandLine(cmd);
        cmdLine.parseArgs();
        assertNotNull(cmd.getPath());
        assertEquals(".", cmd.getPath().toString());
    }

    @Test
    void customPortIsParsed() {
        var cmd = new ServeCommand();
        var cmdLine = new CommandLine(cmd);
        cmdLine.parseArgs("--port", "9090");
        assertEquals(9090, cmd.getPort());
    }

    @Test
    void noUiDefaultsToFalse() {
        var cmd = new ServeCommand();
        var cmdLine = new CommandLine(cmd);
        cmdLine.parseArgs();
        assertEquals(false, cmd.isNoUi());
    }

    @Test
    void noUiFlagIsRecognized() {
        var cmd = new ServeCommand();
        var cmdLine = new CommandLine(cmd);
        cmdLine.parseArgs("--no-ui");
        assertEquals(true, cmd.isNoUi());
    }

    @Test
    void pathNotSwallowedWhenNoUiPrecedesPath() {
        // Regression: --no-ui is boolean and must not consume the next positional arg.
        var cmd = new ServeCommand();
        var cmdLine = new CommandLine(cmd);
        cmdLine.parseArgs("--no-ui", "/some/repo");
        assertEquals(true, cmd.isNoUi());
        assertEquals("/some/repo", cmd.getPath().toString());
    }
}
