package io.github.randomcodespace.iq.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes MD5 hash of file content for change detection.
 * MD5 is used because it is fast and sufficient for content-change
 * detection (not for cryptographic purposes).
 */
public final class FileHasher {

    private FileHasher() {
    }

    /**
     * Compute the MD5 hex digest of a file's content.
     *
     * @param file path to the file
     * @return lowercase hex MD5 hash string
     * @throws IOException if the file cannot be read
     */
    public static String hash(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            try (InputStream is = Files.newInputStream(file)) {
                int n;
                while ((n = is.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * Compute the MD5 hex digest of a string's content (UTF-8 bytes).
     *
     * @param content the string to hash
     * @return lowercase hex MD5 hash string
     */
    public static String hashString(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
