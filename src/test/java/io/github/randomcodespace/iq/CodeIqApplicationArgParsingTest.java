package io.github.randomcodespace.iq;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for CodeIqApplication argument parsing helper methods.
 * These are called in main() before the Spring context starts.
 */
class CodeIqApplicationArgParsingTest {

    private static String extractPositionalArg(String[] args, String command) throws Exception {
        Method m = CodeIqApplication.class.getDeclaredMethod("extractPositionalArg", String[].class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, args, command);
    }

    @Test
    void extractsPathAfterCommand() throws Exception {
        String result = extractPositionalArg(new String[]{"serve", "/my/repo"}, "serve");
        assertEquals("/my/repo", result);
    }

    @Test
    void pathNotSwallowedByBooleanNoUiFlag() throws Exception {
        // Regression: --no-ui is boolean; must not consume /repo as its value.
        String result = extractPositionalArg(new String[]{"serve", "--no-ui", "/my/repo"}, "serve");
        assertEquals("/my/repo", result);
    }

    @Test
    void pathStillExtractedWhenPortFlagPrecedes() throws Exception {
        String result = extractPositionalArg(new String[]{"serve", "--port", "9090", "/my/repo"}, "serve");
        assertEquals("/my/repo", result);
    }

    @Test
    void pathStillExtractedWithNoUiAndPort() throws Exception {
        String result = extractPositionalArg(new String[]{"serve", "--no-ui", "--port", "9090", "/my/repo"}, "serve");
        assertEquals("/my/repo", result);
    }

    @Test
    void returnsNullWhenNoPath() throws Exception {
        String result = extractPositionalArg(new String[]{"serve", "--no-ui"}, "serve");
        assertNull(result);
    }
}
