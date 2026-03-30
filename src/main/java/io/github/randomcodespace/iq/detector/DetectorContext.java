package io.github.randomcodespace.iq.detector;

public record DetectorContext(
    String filePath,
    String language,
    String content,
    Object parsedData,
    String moduleName
) {
    public DetectorContext(String filePath, String language, String content) {
        this(filePath, language, content, null, null);
    }
}
