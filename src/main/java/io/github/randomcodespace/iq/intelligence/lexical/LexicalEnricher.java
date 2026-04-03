package io.github.randomcodespace.iq.intelligence.lexical;

import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Enriches {@link CodeNode} instances with lexical metadata before Neo4j bulk-load.
 *
 * <p>Populates two properties in the node's {@code properties} map:
 * <ul>
 *   <li>{@value #KEY_LEX_COMMENT} — extracted doc comment / docstring for the symbol.</li>
 *   <li>{@value #KEY_LEX_CONFIG_KEYS} — config key path for config-typed nodes.</li>
 * </ul>
 *
 * <p>These are stored as {@code prop_lex_comment} and {@code prop_lex_config_keys} in Neo4j
 * (via the {@code prop_*} round-trip convention) and indexed by {@code lexical_index}.
 */
@Component
public class LexicalEnricher {

    private static final Logger log = LoggerFactory.getLogger(LexicalEnricher.class);

    /** Property key for doc comment text stored in CodeNode.properties. */
    public static final String KEY_LEX_COMMENT = "lex_comment";

    /** Property key for config key path stored in CodeNode.properties. */
    public static final String KEY_LEX_CONFIG_KEYS = "lex_config_keys";

    /**
     * Enrich all nodes with lexical metadata extracted from source files.
     *
     * @param nodes    All enriched nodes (post-linker, post-classifier).
     * @param rootPath Absolute root path of the analysed repository.
     */
    public void enrich(List<CodeNode> nodes, Path rootPath) {
        int commented = 0;
        int configKeyed = 0;
        for (CodeNode node : nodes) {
            if (enrichDocComment(node, rootPath)) commented++;
            if (enrichConfigKeys(node)) configKeyed++;
        }
        log.info("Lexical enrichment: {} doc comments, {} config key entries indexed",
                commented, configKeyed);
    }

    /**
     * Extract and store the doc comment for the given node.
     *
     * @return true if a comment was found and stored.
     */
    private boolean enrichDocComment(CodeNode node, Path rootPath) {
        if (node.getFilePath() == null || node.getLineStart() == null) return false;
        if (!isDocCommentCandidate(node.getKind())) return false;

        String language = SnippetStore.inferLanguage(node.getFilePath());
        Path file = rootPath.resolve(node.getFilePath()).normalize();
        if (!file.startsWith(rootPath)) return false; // path traversal guard

        String comment = DocCommentExtractor.extract(file, language, node.getLineStart());
        if (comment != null && !comment.isBlank()) {
            node.getProperties().put(KEY_LEX_COMMENT, comment);
            return true;
        }
        return false;
    }

    /**
     * For config-typed nodes, store the label/fqn as the config key path.
     *
     * @return true if the node was a config node and the key was stored.
     */
    private static boolean enrichConfigKeys(CodeNode node) {
        if (node.getKind() != NodeKind.CONFIG_KEY
                && node.getKind() != NodeKind.CONFIG_FILE
                && node.getKind() != NodeKind.CONFIG_DEFINITION) {
            return false;
        }
        String keyPath = node.getFqn() != null ? node.getFqn() : node.getLabel();
        if (keyPath != null && !keyPath.isBlank()) {
            node.getProperties().put(KEY_LEX_CONFIG_KEYS, keyPath);
            return true;
        }
        return false;
    }

    /** True for node kinds that typically carry doc comments. */
    private static boolean isDocCommentCandidate(NodeKind kind) {
        return switch (kind) {
            case CLASS, ABSTRACT_CLASS, INTERFACE, ENUM, ANNOTATION_TYPE,
                 METHOD, ENDPOINT, ENTITY, SERVICE, REPOSITORY,
                 COMPONENT, GUARD, MIDDLEWARE -> true;
            default -> false;
        };
    }
}
