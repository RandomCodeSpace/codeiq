package io.github.randomcodespace.iq.intelligence.lexical;

import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetStoreTest {

    @TempDir
    Path root;

    private final SnippetStore snippetStore = new SnippetStore();

    @Test
    void extractsSnippetWithContext() throws Exception {
        Path file = root.resolve("MyClass.java");
        Files.writeString(file, """
                line1
                line2
                line3
                line4
                line5
                line6
                line7
                line8
                line9
                line10
                """);

        CodeNode node = new CodeNode("id1", NodeKind.CLASS, "MyClass");
        node.setFilePath("MyClass.java");
        node.setLineStart(5);
        node.setLineEnd(5);

        Optional<CodeSnippet> result = snippetStore.extract(node, root, 2);
        assertThat(result).isPresent();
        CodeSnippet snippet = result.get();
        assertThat(snippet.lineStart()).isEqualTo(3);
        assertThat(snippet.lineEnd()).isEqualTo(7);
        assertThat(snippet.sourceText()).contains("line3").contains("line5").contains("line7");
        assertThat(snippet.filePath()).isEqualTo("MyClass.java");
        assertThat(snippet.language()).isEqualTo("java");
    }

    @Test
    void returnsEmptyForMissingFilePath() {
        CodeNode node = new CodeNode("id2", NodeKind.CLASS, "NoPath");
        // no filePath set
        assertThat(snippetStore.extract(node, root)).isEmpty();
    }

    @Test
    void returnsEmptyForMissingLineStart() {
        CodeNode node = new CodeNode("id3", NodeKind.CLASS, "NoLine");
        node.setFilePath("SomeFile.java");
        // no lineStart set
        assertThat(snippetStore.extract(node, root)).isEmpty();
    }

    @Test
    void enforcesMaxLinesLimit() throws Exception {
        // Write a 200-line file
        var sb = new StringBuilder();
        for (int i = 1; i <= 200; i++) sb.append("line").append(i).append('\n');
        Path file = root.resolve("Big.java");
        Files.writeString(file, sb.toString());

        CodeNode node = new CodeNode("id4", NodeKind.CLASS, "Big");
        node.setFilePath("Big.java");
        node.setLineStart(100);
        node.setLineEnd(100);

        Optional<CodeSnippet> result = snippetStore.extract(node, root, 100);
        assertThat(result).isPresent();
        int lineCount = result.get().lineEnd() - result.get().lineStart() + 1;
        assertThat(lineCount).isLessThanOrEqualTo(SnippetStore.MAX_LINES);
    }

    @Test
    void preventsPathTraversal() {
        CodeNode node = new CodeNode("id5", NodeKind.CLASS, "Traversal");
        node.setFilePath("../../etc/passwd");
        node.setLineStart(1);
        assertThat(snippetStore.extract(node, root)).isEmpty();
    }

    @Test
    void inferredLanguageFromExtension() {
        assertThat(SnippetStore.inferLanguage("Foo.java")).isEqualTo("java");
        assertThat(SnippetStore.inferLanguage("bar.ts")).isEqualTo("typescript");
        assertThat(SnippetStore.inferLanguage("baz.py")).isEqualTo("python");
        assertThat(SnippetStore.inferLanguage("main.go")).isEqualTo("go");
        assertThat(SnippetStore.inferLanguage("lib.rs")).isEqualTo("rust");
        assertThat(SnippetStore.inferLanguage("noext")).isEqualTo("unknown");
    }

    @Test
    void extractionIsDeterministic() throws Exception {
        Path file = root.resolve("Det.java");
        Files.writeString(file, "class Det {\n    void go() {}\n}\n");

        CodeNode node = new CodeNode("id6", NodeKind.CLASS, "Det");
        node.setFilePath("Det.java");
        node.setLineStart(1);
        node.setLineEnd(3);

        Optional<CodeSnippet> r1 = snippetStore.extract(node, root);
        Optional<CodeSnippet> r2 = snippetStore.extract(node, root);
        assertThat(r1.map(CodeSnippet::sourceText)).isEqualTo(r2.map(CodeSnippet::sourceText));
    }
}
