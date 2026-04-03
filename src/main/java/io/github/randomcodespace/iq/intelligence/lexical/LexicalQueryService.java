package io.github.randomcodespace.iq.intelligence.lexical;

import io.github.randomcodespace.iq.config.CodeIqConfig;
import io.github.randomcodespace.iq.graph.GraphStore;
import io.github.randomcodespace.iq.intelligence.Provenance;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Lexical query service for identifier, doc comment, and config key retrieval.
 *
 * <p>Only active in the {@code serving} profile — lexical queries require a Neo4j
 * graph produced by the {@code enrich} command.
 */
@Service
@Profile("serving")
public class LexicalQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final GraphStore graphStore;
    private final SnippetStore snippetStore;
    private final CodeIqConfig config;

    public LexicalQueryService(GraphStore graphStore, SnippetStore snippetStore,
                               CodeIqConfig config) {
        this.graphStore = graphStore;
        this.snippetStore = snippetStore;
        this.config = config;
    }

    /**
     * Find nodes whose name (label or fully-qualified name) matches an identifier.
     * Results are ranked by fulltext relevance.
     *
     * @param identifier Identifier name to look up (e.g. "UserService", "handleLogin").
     * @param limit      Maximum number of results.
     * @return Ranked list of lexical results carrying provenance.
     */
    public List<LexicalResult> findByIdentifier(String identifier, int limit) {
        List<CodeNode> nodes = graphStore.search(identifier, Math.min(limit, MAX_LIMIT));
        return nodes.stream()
                .map(n -> LexicalResult.of(n, 0f, "identifier"))
                .toList();
    }

    /** Convenience overload using the default limit. */
    public List<LexicalResult> findByIdentifier(String identifier) {
        return findByIdentifier(identifier, DEFAULT_LIMIT);
    }

    /**
     * Find nodes whose doc comment / docstring text matches the given query.
     * Searches the {@code lexical_index} over {@code prop_lex_comment}.
     *
     * @param docQuery Natural-language or keyword query against doc comment text.
     * @param limit    Maximum number of results.
     * @return Ranked list of lexical results with optional bounded snippets.
     */
    public List<LexicalResult> findByDocComment(String docQuery, int limit) {
        Path rootPath = Path.of(config.getRootPath());
        List<CodeNode> nodes = graphStore.searchLexical(docQuery, Math.min(limit, MAX_LIMIT));
        return nodes.stream()
                .map(n -> {
                    Optional<CodeSnippet> snippet = snippetStore.extract(n, rootPath);
                    Provenance prov = Provenance.fromProperties(n.getProperties());
                    return new LexicalResult(n, 0f, LexicalEnricher.KEY_LEX_COMMENT,
                            snippet.orElse(null), prov);
                })
                .toList();
    }

    /** Convenience overload using the default limit. */
    public List<LexicalResult> findByDocComment(String docQuery) {
        return findByDocComment(docQuery, DEFAULT_LIMIT);
    }

    /**
     * Find config nodes whose key path matches the given query.
     * Results are filtered to config-typed NodeKinds only.
     *
     * @param keyQuery Partial or full config key path (e.g. "spring.datasource").
     * @param limit    Maximum number of results.
     * @return Ranked list of lexical results.
     */
    public List<LexicalResult> findByConfigKey(String keyQuery, int limit) {
        List<CodeNode> nodes = graphStore.searchLexical(keyQuery, Math.min(limit, MAX_LIMIT));
        return nodes.stream()
                .filter(n -> n.getKind() == NodeKind.CONFIG_KEY
                          || n.getKind() == NodeKind.CONFIG_FILE
                          || n.getKind() == NodeKind.CONFIG_DEFINITION)
                .map(n -> LexicalResult.of(n, 0f, LexicalEnricher.KEY_LEX_CONFIG_KEYS))
                .toList();
    }

    /** Convenience overload using the default limit. */
    public List<LexicalResult> findByConfigKey(String keyQuery) {
        return findByConfigKey(keyQuery, DEFAULT_LIMIT);
    }
}
