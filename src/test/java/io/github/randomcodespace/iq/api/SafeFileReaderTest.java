package io.github.randomcodespace.iq.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFileReaderTest {

    @Test
    void readsWholeFileUnderCap(@TempDir Path tempDir) throws IOException {
        Path f = tempDir.resolve("small.txt");
        Files.writeString(f, "hello", StandardCharsets.UTF_8);
        assertEquals("hello", SafeFileReader.read(f, null, null, 1024L));
    }

    @Test
    void rejectsWholeFileExceedingCap(@TempDir Path tempDir) throws IOException {
        Path f = tempDir.resolve("big.txt");
        byte[] payload = new byte[2048];
        java.util.Arrays.fill(payload, (byte) 'x');
        Files.write(f, payload);
        SafeFileReader.FileTooLargeException e = assertThrows(
                SafeFileReader.FileTooLargeException.class,
                () -> SafeFileReader.read(f, null, null, 1024L));
        assertEquals(2048L, e.size());
        assertEquals(1024L, e.max());
    }

    @Test
    void streamsLineRangeWithoutLoadingWholeFile(@TempDir Path tempDir) throws IOException {
        Path f = tempDir.resolve("ranged.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) sb.append("line").append(i).append('\n');
        Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);

        // Whole-file readString with cap=64 would fail, but the 3-line range fits.
        assertEquals("line2\nline3\nline4",
                SafeFileReader.read(f, 2, 4, 64L));
    }

    @Test
    void rejectsLineRangeExceedingCap(@TempDir Path tempDir) throws IOException {
        Path f = tempDir.resolve("ranged.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) sb.append("line").append(i).append('\n');
        Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);

        SafeFileReader.FileTooLargeException e = assertThrows(
                SafeFileReader.FileTooLargeException.class,
                () -> SafeFileReader.read(f, 1, 100, 32L));
        assertTrue(e.max() == 32L);
    }

    @Test
    void clampsStartLineToOneWhenNegative(@TempDir Path tempDir) throws IOException {
        Path f = tempDir.resolve("clamp.txt");
        Files.writeString(f, "a\nb\nc\n", StandardCharsets.UTF_8);
        assertEquals("a\nb", SafeFileReader.read(f, -3, 2, 1024L));
    }
}
