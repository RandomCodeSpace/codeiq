package io.github.randomcodespace.iq.intelligence.provenance;

import io.github.randomcodespace.iq.graph.GraphStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactMetadataProviderTest {

    @Test
    void currentReflectsLatestGraphCounts(@TempDir Path tempDir) {
        GraphStore graphStore = mock(GraphStore.class);
        when(graphStore.count()).thenReturn(0L, 12L);
        when(graphStore.countEdges()).thenReturn(0L, 34L);

        ArtifactMetadataProvider provider = new ArtifactMetadataProvider(tempDir, graphStore);

        ArtifactMetadata beforeBootstrap = provider.current();
        ArtifactMetadata afterBootstrap = provider.current();

        assertNotEquals(beforeBootstrap.integrityHash(), afterBootstrap.integrityHash());
        assertEquals(
                ArtifactMetadata.computeIntegrityHash(12L, 34L, afterBootstrap.commitSha()),
                afterBootstrap.integrityHash()
        );
    }

    @Test
    void currentFallsBackToZeroCountsWhenGraphUnavailable(@TempDir Path tempDir) {
        GraphStore graphStore = mock(GraphStore.class);
        when(graphStore.count()).thenThrow(new RuntimeException("graph not ready"));

        ArtifactMetadataProvider provider = new ArtifactMetadataProvider(tempDir, graphStore);
        ArtifactMetadata metadata = provider.current();

        assertEquals(
                ArtifactMetadata.computeIntegrityHash(0L, 0L, metadata.commitSha()),
                metadata.integrityHash()
        );
    }
}
