package io.github.randomcodespace.iq.intelligence.lexical;

import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalEnricherTest {

    @TempDir
    Path root;

    private final LexicalEnricher enricher = new LexicalEnricher();

    @Test
    void enrichesDocCommentForClass() throws Exception {
        Path file = root.resolve("MyService.java");
        Files.writeString(file, """
                /**
                 * Handles user authentication.
                 */
                public class MyService {}
                """);

        CodeNode node = new CodeNode("id1", NodeKind.CLASS, "MyService");
        node.setFilePath("MyService.java");
        node.setLineStart(4);

        enricher.enrich(List.of(node), root);

        assertThat(node.getProperties()).containsKey(LexicalEnricher.KEY_LEX_COMMENT);
        assertThat(node.getProperties().get(LexicalEnricher.KEY_LEX_COMMENT).toString())
                .contains("user authentication");
    }

    @Test
    void enrichesConfigKeyForConfigNode() {
        CodeNode node = new CodeNode("id2", NodeKind.CONFIG_KEY, "spring.datasource.url");
        node.setFqn("spring.datasource.url");

        enricher.enrich(List.of(node), root);

        assertThat(node.getProperties()).containsKey(LexicalEnricher.KEY_LEX_CONFIG_KEYS);
        assertThat(node.getProperties().get(LexicalEnricher.KEY_LEX_CONFIG_KEYS).toString())
                .isEqualTo("spring.datasource.url");
    }

    @Test
    void enrichesConfigFileNode() {
        CodeNode node = new CodeNode("id3", NodeKind.CONFIG_FILE, "application.yml");
        node.setLabel("application.yml");

        enricher.enrich(List.of(node), root);

        assertThat(node.getProperties()).containsKey(LexicalEnricher.KEY_LEX_CONFIG_KEYS);
    }

    @Test
    void skipsNodesWithoutFilePath() {
        CodeNode node = new CodeNode("id4", NodeKind.CLASS, "Bare");
        // no filePath, no lineStart

        enricher.enrich(List.of(node), root);

        assertThat(node.getProperties()).doesNotContainKey(LexicalEnricher.KEY_LEX_COMMENT);
    }

    @Test
    void doesNotEnrichModuleOrTopicNodes() throws Exception {
        Path file = root.resolve("module.java");
        Files.writeString(file, "/** doc */\nmodule foo {}");

        CodeNode node = new CodeNode("id5", NodeKind.MODULE, "foo");
        node.setFilePath("module.java");
        node.setLineStart(2);

        enricher.enrich(List.of(node), root);

        assertThat(node.getProperties()).doesNotContainKey(LexicalEnricher.KEY_LEX_COMMENT);
    }

    @Test
    void enrichmentIsDeterministic() throws Exception {
        Path file = root.resolve("Svc.java");
        Files.writeString(file, """
                /** Service docs. */
                public class Svc {}
                """);

        CodeNode n1 = new CodeNode("id6", NodeKind.CLASS, "Svc");
        n1.setFilePath("Svc.java");
        n1.setLineStart(2);

        CodeNode n2 = new CodeNode("id7", NodeKind.CLASS, "Svc");
        n2.setFilePath("Svc.java");
        n2.setLineStart(2);

        enricher.enrich(List.of(n1), root);
        enricher.enrich(List.of(n2), root);

        assertThat(n1.getProperties().get(LexicalEnricher.KEY_LEX_COMMENT))
                .isEqualTo(n2.getProperties().get(LexicalEnricher.KEY_LEX_COMMENT));
    }
}
