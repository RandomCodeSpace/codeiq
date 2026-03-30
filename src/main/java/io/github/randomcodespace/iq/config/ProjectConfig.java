package io.github.randomcodespace.iq.config;

import java.util.List;
import java.util.Map;

/**
 * Parsed project-level configuration from .osscodeiq.yml.
 * Immutable value object carrying optional filter/override settings.
 */
public final class ProjectConfig {

    private final List<String> languages;
    private final List<String> detectorCategories;
    private final List<String> detectorInclude;
    private final List<String> exclude;
    private final Map<String, String> parsers;
    private final Integer pipelineParallelism;
    private final Integer pipelineBatchSize;

    public ProjectConfig(
            List<String> languages,
            List<String> detectorCategories,
            List<String> detectorInclude,
            List<String> exclude,
            Map<String, String> parsers,
            Integer pipelineParallelism,
            Integer pipelineBatchSize
    ) {
        this.languages = languages;
        this.detectorCategories = detectorCategories;
        this.detectorInclude = detectorInclude;
        this.exclude = exclude;
        this.parsers = parsers;
        this.pipelineParallelism = pipelineParallelism;
        this.pipelineBatchSize = pipelineBatchSize;
    }

    /** Empty config with no overrides. */
    public static ProjectConfig empty() {
        return new ProjectConfig(null, null, null, null, null, null, null);
    }

    /** Languages to include in file discovery (null = all). */
    public List<String> getLanguages() {
        return languages;
    }

    /** Detector categories to run (null = all). */
    public List<String> getDetectorCategories() {
        return detectorCategories;
    }

    /** Specific detector names to include (null = all). */
    public List<String> getDetectorInclude() {
        return detectorInclude;
    }

    /** Additional exclude patterns for file discovery (null = none). */
    public List<String> getExclude() {
        return exclude;
    }

    /** Parser overrides by language (null = default). */
    public Map<String, String> getParsers() {
        return parsers;
    }

    /** Pipeline thread count override (null = adaptive). */
    public Integer getPipelineParallelism() {
        return pipelineParallelism;
    }

    /** Pipeline batch size override (null = default). */
    public Integer getPipelineBatchSize() {
        return pipelineBatchSize;
    }

    /** True if this config has any language filter. */
    public boolean hasLanguageFilter() {
        return languages != null && !languages.isEmpty();
    }

    /** True if this config has any detector category filter. */
    public boolean hasDetectorCategoryFilter() {
        return detectorCategories != null && !detectorCategories.isEmpty();
    }

    /** True if this config has any detector include filter. */
    public boolean hasDetectorIncludeFilter() {
        return detectorInclude != null && !detectorInclude.isEmpty();
    }

    /** True if this config has any exclude patterns. */
    public boolean hasExcludePatterns() {
        return exclude != null && !exclude.isEmpty();
    }
}
