package io.github.randomcodespace.iq.detector;

import io.github.randomcodespace.iq.analyzer.InfrastructureRegistry;

public record DetectorContext(
    String filePath,
    String language,
    String content,
    Object parsedData,
    String moduleName,
    InfrastructureRegistry registry
) {
    /** Minimal constructor — no parsed data, module name, or registry. */
    public DetectorContext(String filePath, String language, String content) {
        this(filePath, language, content, null, null, null);
    }

    /** Full constructor without registry — backward compat for existing callers. */
    public DetectorContext(String filePath, String language, String content,
                           Object parsedData, String moduleName) {
        this(filePath, language, content, parsedData, moduleName, null);
    }
}
