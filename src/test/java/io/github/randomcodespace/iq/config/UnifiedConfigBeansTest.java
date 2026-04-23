package io.github.randomcodespace.iq.config;

import io.github.randomcodespace.iq.config.unified.CodeIqUnifiedConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies Task 11 wiring: the Spring context exposes a {@link CodeIqUnifiedConfig}
 * bean that is the single source of truth, and the legacy {@link CodeIqConfig} bean
 * is produced by adapting the unified bean.
 *
 * <p>The "defaults path" assertions here run with no {@code codeiq.yml} in cwd,
 * so values must match {@link io.github.randomcodespace.iq.config.unified.ConfigDefaults}
 * — which in turn matches the values that were historically in {@code application.yml}.
 */
@SpringBootTest
@ActiveProfiles("test")
class UnifiedConfigBeansTest {

    @Autowired
    CodeIqUnifiedConfig unified;

    @Autowired
    CodeIqConfig legacy;

    @Test
    void contextExposesUnifiedAndLegacyBeansBothBackedBySameSource() {
        assertNotNull(unified, "unified config bean must be present");
        assertNotNull(legacy, "legacy config bean must be present");
        // Same cacheDir — proves the legacy bean is adapted from unified.
        assertEquals(unified.indexing().cacheDir(), legacy.getCacheDir());
    }

    @Test
    void defaultsMatchHistoricalApplicationYmlValues() {
        // These values came from application.yml pre-Task-11; they must still
        // be what CodeIqConfig exposes now that wiring goes through ConfigDefaults.
        assertEquals(".code-iq/cache", legacy.getCacheDir());
        assertEquals(".code-iq/graph/graph.db", legacy.getGraph().getPath());
        assertEquals(10, legacy.getMaxDepth());
        assertEquals(10, legacy.getMaxRadius());
        assertEquals(500, legacy.getBatchSize());
    }
}
