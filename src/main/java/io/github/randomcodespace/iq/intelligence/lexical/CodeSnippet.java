package io.github.randomcodespace.iq.intelligence.lexical;

import io.github.randomcodespace.iq.intelligence.Provenance;

/**
 * A bounded code snippet extracted from a source file, produced by {@link SnippetStore}.
 *
 * @param sourceText  Raw source text, bounded to at most {@link SnippetStore#MAX_LINES} lines.
 * @param filePath    Repo-relative path of the source file.
 * @param lineStart   1-based start line of the extracted snippet.
 * @param lineEnd     1-based end line of the extracted snippet (inclusive).
 * @param language    Lowercase language identifier (e.g. "java", "typescript").
 * @param provenance  Provenance of the parent CodeNode; may be null.
 */
public record CodeSnippet(
        String sourceText,
        String filePath,
        int lineStart,
        int lineEnd,
        String language,
        Provenance provenance
) {}
