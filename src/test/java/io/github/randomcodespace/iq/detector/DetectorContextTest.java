package io.github.randomcodespace.iq.detector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DetectorContextTest {

    @Test
    void fullConstructorSetsAllFields() {
        Object parsed = new Object();
        var ctx = new DetectorContext("src/Foo.java", "java", "class Foo {}", parsed, "com.app");

        assertEquals("src/Foo.java", ctx.filePath());
        assertEquals("java", ctx.language());
        assertEquals("class Foo {}", ctx.content());
        assertSame(parsed, ctx.parsedData());
        assertEquals("com.app", ctx.moduleName());
    }

    @Test
    void convenienceConstructorSetsNullOptionalFields() {
        var ctx = new DetectorContext("test.py", "python", "print('hi')");

        assertEquals("test.py", ctx.filePath());
        assertEquals("python", ctx.language());
        assertEquals("print('hi')", ctx.content());
        assertNull(ctx.parsedData());
        assertNull(ctx.moduleName());
    }

    @Test
    void nullParsedDataAndModuleNameAreAllowed() {
        var ctx = new DetectorContext("f.js", "javascript", "var x;", null, null);

        assertNull(ctx.parsedData());
        assertNull(ctx.moduleName());
    }
}
