# ANTLR Full AST Migration Design

**Date:** 2026-03-29
**Status:** Approved
**Scope:** Replace all regex-based detectors with ANTLR AST parsing for 8 non-JVM languages

## Overview

Migrate all 91 regex-based detectors to ANTLR AST-based parsing. Full replacement, not hybrid. Every detector walks a parse tree instead of matching regex patterns. Fallback to regex with warning log when ANTLR parsing fails on malformed code.

## Why

- Maximum detection quality — proper scoping, type info, decorator resolution
- Less boilerplate — AST walking is cleaner than regex pattern engineering
- Maintainable — adding a new detector means walking a tree, not crafting fragile patterns
- Future-proof — ANTLR has 200+ language grammars, adding new languages is trivial
- Consistent — same pattern across all languages (JavaParser for Java, ANTLR for everything else)

## Tech Stack

- ANTLR 4.13.2 runtime + maven plugin
- `.g4` grammar files from official grammars-v4 repository
- Generated Java lexers/parsers/visitors at build time
- ThreadLocal parser instances for virtual thread safety

## Languages & Grammars

| Language | Grammar Source | Detectors to Migrate |
|---|---|---|
| TypeScript/JS | grammars-v4/typescript + javascript | 14 |
| Python | grammars-v4/python (Python3) | 12 |
| Go | grammars-v4/golang | 4 |
| C# | grammars-v4/csharp | 4 |
| Rust | grammars-v4/rust | 3 |
| Kotlin | grammars-v4/kotlin | 3 |
| Scala | grammars-v4/scala | 2 |
| C++ | grammars-v4/cpp (CPP14) | 2 |
| Shell/Bash | grammars-v4/bash | 3 |
| **Total** | | **47 detectors** |

Note: Config/infra detectors (YAML, JSON, XML, etc.) stay as AbstractStructuredDetector — they parse data structures, not programming languages. Auth detectors that scan multiple languages stay regex (they match patterns across any language). Generic imports detector stays regex.

## Directory Structure

```
src/main/antlr4/io/github/randomcodespace/iq/grammar/
    typescript/TypeScriptLexer.g4, TypeScriptParser.g4
    python/Python3Lexer.g4, Python3Parser.g4
    golang/GoLexer.g4, GoParser.g4
    csharp/CSharpLexer.g4, CSharpParser.g4
    rust/RustLexer.g4, RustParser.g4
    kotlin/KotlinLexer.g4, KotlinParser.g4
    scala/ScalaLexer.g4, ScalaParser.g4
    cpp/CPP14Lexer.g4, CPP14Parser.g4
    bash/BashLexer.g4, BashParser.g4
```

Generated output: `target/generated-sources/antlr4/io/github/randomcodespace/iq/grammar/`

## Base Class

```java
public abstract class AbstractAntlrDetector extends AbstractRegexDetector {

    // Template method: try ANTLR, fall back to regex with warning
    @Override
    public DetectorResult detect(DetectorContext ctx) {
        try {
            ParseTree tree = parse(ctx);
            if (tree != null) {
                return detectWithAst(tree, ctx);
            }
        } catch (Exception e) {
            log.warn("ANTLR parse failed for {}, falling back to regex: {}",
                ctx.filePath(), e.getMessage());
        }
        return detectWithRegex(ctx);
    }

    protected abstract ParseTree parse(DetectorContext ctx);
    protected abstract DetectorResult detectWithAst(ParseTree tree, DetectorContext ctx);
    protected DetectorResult detectWithRegex(DetectorContext ctx) {
        return DetectorResult.empty(); // Override if regex fallback needed
    }
}
```

### Language-specific parser helpers

```java
// Per-language helper classes for common AST operations
public class TypeScriptAstHelper {
    private static final ThreadLocal<TypeScriptParser> PARSER = ...;

    public static ParseTree parse(String content) { ... }
    public static List<ClassDecl> findClasses(ParseTree tree) { ... }
    public static List<FunctionDecl> findFunctions(ParseTree tree) { ... }
    public static List<ImportDecl> findImports(ParseTree tree) { ... }
    public static List<Decorator> findDecorators(ParseTree tree) { ... }
}
```

One helper per language. Detectors for that language share the helper.

## Detector Rewrite Pattern

Each detector:
1. Extends `AbstractAntlrDetector`
2. Implements `parse()` — delegates to language helper
3. Implements `detectWithAst()` — walks the AST for framework-specific patterns
4. Optionally overrides `detectWithRegex()` — existing regex logic as fallback

## Fallback Behavior

When ANTLR parse fails:
1. Log warning: `"ANTLR parse failed for {file}, falling back to regex: {error}"`
2. Execute regex fallback (existing logic, unchanged)
3. Result from regex is returned — never lose coverage

This ensures we never produce fewer results than the current regex-only approach.

## Thread Safety

ANTLR parsers are NOT thread-safe. Use ThreadLocal per language:

```java
private static final ThreadLocal<TypeScriptLexer> LEXER =
    ThreadLocal.withInitial(() -> new TypeScriptLexer(null));
private static final ThreadLocal<TypeScriptParser> PARSER =
    ThreadLocal.withInitial(() -> new TypeScriptParser(null));
```

Reset input stream per parse call. Same pattern as JavaParser ThreadLocal.

## Maven Configuration

```xml
<dependency>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-runtime</artifactId>
    <version>4.13.2</version>
</dependency>

<plugin>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-maven-plugin</artifactId>
    <version>4.13.2</version>
    <executions>
        <execution>
            <goals><goal>antlr4</goal></goals>
        </execution>
    </executions>
    <configuration>
        <visitor>true</visitor>
        <listener>true</listener>
    </configuration>
</plugin>
```

## Testing

Each migrated detector keeps existing tests plus:
- **AST-specific test** — verify AST path produces correct nodes/edges
- **Fallback test** — malformed code triggers regex fallback + warning logged
- **Parity test** — AST results >= regex results on same input

## Performance

- ANTLR parsing: ~2-5x slower than regex per file
- Total pipeline impact: ~10-20% slower analysis time
- Offset by: higher quality detection, fewer false negatives
- Virtual threads absorb some overhead via parallelism

## What Stays Unchanged

- Java detectors — already use JavaParser (better than ANTLR for Java)
- Config detectors — use AbstractStructuredDetector (YAML/JSON/XML parsing)
- Auth detectors — scan multiple languages with cross-language regex patterns
- Generic imports detector — simple cross-language regex
- IaC detectors — Terraform/Bicep/Dockerfile use regex (no ANTLR grammar needed)
