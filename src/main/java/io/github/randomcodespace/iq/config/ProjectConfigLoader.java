package io.github.randomcodespace.iq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads project-level configuration from .osscodeiq.yml or .osscodeiq.yaml
 * found in the target directory, and applies overrides to {@link CodeIqConfig}.
 */
public final class ProjectConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ProjectConfigLoader.class);
    private static final String[] CONFIG_FILE_NAMES = {".osscodeiq.yml", ".osscodeiq.yaml"};

    private ProjectConfigLoader() {
        // utility class
    }

    /**
     * Look for .osscodeiq.yml or .osscodeiq.yaml in the given directory.
     * If found, parse it and apply matching properties to the config.
     *
     * @param directory the project root directory to search
     * @param config    the config to apply overrides to
     * @return true if a config file was found and applied
     */
    @SuppressWarnings("unchecked")
    public static boolean loadIfPresent(Path directory, CodeIqConfig config) {
        for (String name : CONFIG_FILE_NAMES) {
            Path configFile = directory.resolve(name);
            if (Files.isRegularFile(configFile)) {
                try {
                    String content = Files.readString(configFile, StandardCharsets.UTF_8);
                    Yaml yaml = new Yaml();
                    Map<String, Object> data = yaml.load(content);
                    if (data != null) {
                        applyOverrides(data, config);
                        log.info("Loaded project config from {}", configFile);
                        return true;
                    }
                } catch (IOException e) {
                    log.warn("Failed to read config file {}: {}", configFile, e.getMessage());
                } catch (Exception e) {
                    log.warn("Failed to parse config file {}: {}", configFile, e.getMessage());
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void applyOverrides(Map<String, Object> data, CodeIqConfig config) {
        if (data.containsKey("cache_dir")) {
            config.setCacheDir(String.valueOf(data.get("cache_dir")));
        }
        if (data.containsKey("max_depth")) {
            config.setMaxDepth(toInt(data.get("max_depth"), config.getMaxDepth()));
        }
        if (data.containsKey("max_radius")) {
            config.setMaxRadius(toInt(data.get("max_radius"), config.getMaxRadius()));
        }
        // Nested analysis section (matches Python config structure)
        if (data.get("analysis") instanceof Map<?, ?> analysis) {
            if (analysis.containsKey("parallelism")) {
                // Stored for CLI to pick up; not directly in CodeIqConfig
            }
            if (analysis.containsKey("incremental")) {
                // Available for future use
            }
        }
        // Nested output section
        if (data.get("output") instanceof Map<?, ?> output) {
            if (output.containsKey("max_nodes")) {
                // Available for future use
            }
        }
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
