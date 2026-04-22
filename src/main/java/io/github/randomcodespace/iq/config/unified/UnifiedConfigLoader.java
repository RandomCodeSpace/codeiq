package io.github.randomcodespace.iq.config.unified;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reads a single codeiq.yml file into a CodeIqUnifiedConfig overlay.
 * Missing file => CodeIqUnifiedConfig.empty(). Malformed YAML or type
 * mismatches throw ConfigLoadException with the file path and failing
 * field name in the message.
 */
public final class UnifiedConfigLoader {
    private UnifiedConfigLoader() {}

    public static CodeIqUnifiedConfig load(Path path) {
        if (path == null || !Files.exists(path)) {
            return CodeIqUnifiedConfig.empty();
        }
        String yaml;
        try {
            yaml = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigLoadException("Cannot read config file " + path, e);
        }
        Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object raw;
        try {
            raw = parser.load(yaml);
        } catch (YAMLException e) {
            throw new ConfigLoadException(
                    "Malformed YAML in " + path + ": " + e.getMessage(), e);
        }
        if (raw == null) return CodeIqUnifiedConfig.empty();
        if (!(raw instanceof Map<?, ?> m)) {
            throw new ConfigLoadException(
                    "Top-level of " + path + " must be a mapping, got: " + raw.getClass().getSimpleName());
        }
        try {
            return fromMap(m, path);
        } catch (ClassCastException | IllegalArgumentException e) {
            throw new ConfigLoadException(
                    "Type mismatch in " + path + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static CodeIqUnifiedConfig fromMap(Map<?, ?> m, Path path) {
        return new CodeIqUnifiedConfig(
                projectFrom((Map<String, Object>) m.get("project"), path),
                indexingFrom((Map<String, Object>) m.get("indexing"), path),
                servingFrom((Map<String, Object>) m.get("serving"), path),
                mcpFrom((Map<String, Object>) m.get("mcp"), path),
                observabilityFrom((Map<String, Object>) m.get("observability")),
                detectorsFrom((Map<String, Object>) m.get("detectors"))
        );
    }

    @SuppressWarnings("unchecked")
    private static ProjectConfig projectFrom(Map<String, Object> m, Path path) {
        if (m == null) return ProjectConfig.empty();
        List<Map<String, Object>> modRaw = (List<Map<String, Object>>) m.get("modules");
        List<ModuleConfig> mods = modRaw == null ? List.of()
                : modRaw.stream().map(x -> new ModuleConfig(
                        (String) x.get("path"),
                        (String) x.get("type"),
                        (String) x.get("name"),
                        (String) x.get("kind"))).toList();
        return new ProjectConfig(
                (String) m.get("name"),
                (String) m.getOrDefault("root", "."),
                mods);
    }

    private static IndexingConfig indexingFrom(Map<String, Object> m, Path path) {
        if (m == null) return IndexingConfig.empty();
        return new IndexingConfig(
                asStringList(m.get("languages")),
                asStringList(m.get("include")),
                asStringList(m.get("exclude")),
                (Boolean) m.get("incremental"),
                (String) m.get("cacheDir"),
                m.get("parallelism") == null ? null : String.valueOf(m.get("parallelism")),
                requireIntOrNull(m.get("batchSize"), path, "indexing.batchSize"));
    }

    @SuppressWarnings("unchecked")
    private static ServingConfig servingFrom(Map<String, Object> m, Path path) {
        if (m == null) return ServingConfig.empty();
        Neo4jConfig n4j = neo4jFrom((Map<String, Object>) m.get("neo4j"), path);
        return new ServingConfig(
                requireIntOrNull(m.get("port"), path, "serving.port"),
                (String) m.get("bindAddress"),
                (Boolean) m.get("readOnly"),
                n4j);
    }

    private static Neo4jConfig neo4jFrom(Map<String, Object> m, Path path) {
        if (m == null) return Neo4jConfig.empty();
        return new Neo4jConfig(
                (String) m.get("dir"),
                requireIntOrNull(m.get("pageCacheMb"), path, "serving.neo4j.pageCacheMb"),
                requireIntOrNull(m.get("heapInitialMb"), path, "serving.neo4j.heapInitialMb"),
                requireIntOrNull(m.get("heapMaxMb"), path, "serving.neo4j.heapMaxMb"));
    }

    @SuppressWarnings("unchecked")
    private static McpConfig mcpFrom(Map<String, Object> m, Path path) {
        if (m == null) return McpConfig.empty();
        Map<String, Object> auth = (Map<String, Object>) m.get("auth");
        Map<String, Object> lim  = (Map<String, Object>) m.get("limits");
        Map<String, Object> tls  = (Map<String, Object>) m.get("tools");
        return new McpConfig(
                (Boolean) m.get("enabled"),
                (String) m.get("transport"),
                (String) m.get("basePath"),
                auth == null ? McpAuthConfig.empty() : new McpAuthConfig(
                        (String) auth.get("mode"),
                        (String) auth.get("tokenEnv")),
                lim == null ? McpLimitsConfig.empty() : new McpLimitsConfig(
                        requireIntOrNull(lim.get("perToolTimeoutMs"), path, "mcp.limits.perToolTimeoutMs"),
                        requireIntOrNull(lim.get("maxResults"), path, "mcp.limits.maxResults"),
                        requireLongOrNull(lim.get("maxPayloadBytes"), path, "mcp.limits.maxPayloadBytes"),
                        requireIntOrNull(lim.get("ratePerMinute"), path, "mcp.limits.ratePerMinute")),
                tls == null ? McpToolsConfig.empty() : new McpToolsConfig(
                        asStringList(tls.get("enabled")),
                        asStringList(tls.get("disabled"))));
    }

    private static ObservabilityConfig observabilityFrom(Map<String, Object> m) {
        if (m == null) return ObservabilityConfig.empty();
        return new ObservabilityConfig(
                (Boolean) m.get("metrics"),
                (Boolean) m.get("tracing"),
                (String) m.get("logFormat"),
                (String) m.get("logLevel"));
    }

    @SuppressWarnings("unchecked")
    private static DetectorsConfig detectorsFrom(Map<String, Object> m) {
        if (m == null) return DetectorsConfig.empty();
        Map<String, DetectorOverride> overrides = new java.util.LinkedHashMap<>();
        Map<String, Object> raw = (Map<String, Object>) m.getOrDefault("overrides", Map.of());
        for (var e : raw.entrySet()) {
            Map<String, Object> v = (Map<String, Object>) e.getValue();
            overrides.put(e.getKey(), new DetectorOverride(v == null ? null : (Boolean) v.get("enabled")));
        }
        return new DetectorsConfig(asStringList(m.get("profiles")), overrides);
    }

    private static List<String> asStringList(Object o) {
        if (o == null) return List.of();
        if (o instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        throw new IllegalArgumentException("expected list, got: " + o.getClass().getSimpleName());
    }

    private static Integer requireIntOrNull(Object o, Path path, String field) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        throw new IllegalArgumentException(field + " must be an integer; got " + o);
    }

    private static Long requireLongOrNull(Object o, Path path, String field) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        throw new IllegalArgumentException(field + " must be an integer; got " + o);
    }
}
