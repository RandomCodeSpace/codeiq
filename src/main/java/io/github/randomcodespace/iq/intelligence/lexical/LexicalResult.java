package io.github.randomcodespace.iq.intelligence.lexical;

import io.github.randomcodespace.iq.intelligence.Provenance;
import io.github.randomcodespace.iq.model.CodeNode;

/**
 * A single result from a lexical query, carrying relevance metadata and optional snippet.
 *
 * @param node         The matched CodeNode.
 * @param score        Lucene relevance score (higher = more relevant); 0.0 for non-scored queries.
 * @param matchedField The indexed field that matched: "identifier", "lex_comment", or "lex_config_keys".
 * @param snippet      Optional bounded code snippet for this node; null if not extracted.
 * @param provenance   Provenance extracted from the node's properties; may be null.
 */
public record LexicalResult(
        CodeNode node,
        float score,
        String matchedField,
        CodeSnippet snippet,
        Provenance provenance
) {
    /** Convenience factory — no snippet, provenance read from node properties. */
    public static LexicalResult of(CodeNode node, float score, String matchedField) {
        return new LexicalResult(node, score, matchedField, null,
                Provenance.fromProperties(node.getProperties()));
    }
}
