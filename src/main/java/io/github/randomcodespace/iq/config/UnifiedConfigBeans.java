package io.github.randomcodespace.iq.config;

import io.github.randomcodespace.iq.config.unified.CodeIqUnifiedConfig;
import io.github.randomcodespace.iq.config.unified.ConfigResolver;
import io.github.randomcodespace.iq.graph.GraphStore;
import io.github.randomcodespace.iq.intelligence.provenance.ArtifactMetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

/**
 * Spring wiring for the unified configuration.
 *
 * <p>Produces the {@link CodeIqUnifiedConfig} bean by running
 * {@link ConfigResolver} once at startup (defaults + user-global yml +
 * project yml + env vars). The legacy {@link CodeIqConfig} bean is derived
 * from the unified tree via {@link UnifiedConfigAdapter#toCodeIqConfig}, so
 * call sites that still depend on the legacy API continue to work unchanged.
 *
 * <p>Path layering (last wins):
 * <pre>
 *   BUILT_IN    (ConfigDefaults.builtIn())
 *   USER_GLOBAL (~/.codeiq/config.yml)
 *   PROJECT     (./codeiq.yml)
 *   ENV         (CODEIQ_* environment variables)
 *   CLI         (injected per-command; not applied here)
 * </pre>
 */
@Configuration
public class UnifiedConfigBeans {

    /**
     * Resolves codeiq.yml + env vars once at startup; the resulting
     * {@link CodeIqUnifiedConfig} is the single source of truth for
     * configuration.
     */
    @Bean
    public CodeIqUnifiedConfig codeIqUnifiedConfig() {
        Path userGlobal = Path.of(System.getProperty("user.home"), ".codeiq", "config.yml");
        Path project = Path.of("codeiq.yml");
        return new ConfigResolver()
                .userGlobalPath(userGlobal)
                .projectPath(project)
                .env(System.getenv())
                .resolve()
                .effective();
    }

    /**
     * Back-compat bean for the legacy {@link CodeIqConfig} API. Produced by
     * adapting the unified tree; preserves existing getter/setter surface
     * consumed by ~100 call sites across the codebase.
     */
    @Bean
    @Primary
    public CodeIqConfig codeIqConfig(CodeIqUnifiedConfig unified) {
        return UnifiedConfigAdapter.toCodeIqConfig(unified);
    }

    /**
     * Provides on-demand artifact metadata in the {@code serving} profile.
     *
     * <p>Moved here from {@link CodeIqConfig} when that class stopped being a
     * {@code @Configuration}. Graph-derived fields are resolved lazily so
     * H2-to-Neo4j bootstrap can complete before clients fetch manifest data.
     */
    @Bean
    @Profile("serving")
    public ArtifactMetadataProvider artifactMetadataProvider(
            CodeIqConfig config,
            @Autowired(required = false) GraphStore graphStore) {
        Path root = Path.of(config.getRootPath()).toAbsolutePath().normalize();
        return new ArtifactMetadataProvider(root, graphStore);
    }
}
