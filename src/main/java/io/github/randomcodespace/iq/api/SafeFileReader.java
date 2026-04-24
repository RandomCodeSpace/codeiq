package io.github.randomcodespace.iq.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads files for the read-only serving layer with a hard byte cap.
 *
 * <p>The two entry points that surface repo content over HTTP — {@code GET /api/file}
 * and the {@code read_file} MCP tool — must never load unbounded content into the JVM
 * heap; a multi-GB file would OOM the serving process and become a trivial DoS vector.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Without a line range, the file's on-disk size is checked first and the read
 *       is rejected if it exceeds the cap.</li>
 *   <li>With a {@code startLine}/{@code endLine} range, the file is read line-by-line
 *       via a {@link BufferedReader}; only lines in range are retained and the
 *       accumulated UTF-8 byte count is capped the same way.</li>
 * </ul>
 */
public final class SafeFileReader {

    public static final class FileTooLargeException extends RuntimeException {
        private final long size;
        private final long max;

        public FileTooLargeException(long size, long max) {
            super("File exceeds max size: " + size + " bytes (max " + max + " bytes)");
            this.size = size;
            this.max = max;
        }

        public long size() { return size; }
        public long max()  { return max; }
    }

    private SafeFileReader() {}

    public static String read(Path path, Integer startLine, Integer endLine, long maxBytes)
            throws IOException {
        if (startLine == null && endLine == null) {
            long size = Files.size(path);
            if (size > maxBytes) {
                throw new FileTooLargeException(size, maxBytes);
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        int start = Math.max(1, startLine != null ? startLine : 1);
        int end = endLine != null ? Math.max(start, endLine) : Integer.MAX_VALUE;
        StringBuilder sb = new StringBuilder();
        long accumulated = 0;
        boolean first = true;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int idx = 0;
            while ((line = reader.readLine()) != null) {
                idx++;
                if (idx < start) continue;
                if (idx > end) break;
                long lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
                long add = lineBytes + (first ? 0L : 1L);
                if (accumulated + add > maxBytes) {
                    throw new FileTooLargeException(accumulated + add, maxBytes);
                }
                if (!first) sb.append('\n');
                sb.append(line);
                accumulated += add;
                first = false;
            }
        }
        return sb.toString();
    }
}
