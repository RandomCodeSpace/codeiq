package io.github.randomcodespace.iq.health;

import io.github.randomcodespace.iq.graph.GraphStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphHealthIndicatorTest {

    @Mock
    private GraphStore graphStore;

    @InjectMocks
    private GraphHealthIndicator indicator;

    @Test
    void healthShouldBeUpWhenNodesExist() {
        when(graphStore.count()).thenReturn(42L);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(42L, health.getDetails().get("nodes"));
    }

    @Test
    void healthShouldBeDownWhenNoNodes() {
        when(graphStore.count()).thenReturn(0L);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("no_graph_data", health.getDetails().get("reason"));
        assertEquals(0, health.getDetails().get("nodes"));
    }

    @Test
    void healthShouldBeDownWhenStoreThrows() {
        when(graphStore.count()).thenThrow(new RuntimeException("DB connection failed"));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("graph_store_unavailable", health.getDetails().get("reason"));
        // CodeQL java/error-message-exposure — health endpoint is permitAll;
        // the indicator must NOT echo the underlying exception's message.
        // Only the exception class name (sanitized indicator) appears.
        assertEquals("RuntimeException", health.getDetails().get("error_class"));
        assertNull(health.getDetails().get("error"),
                "Exception message must not surface to permitAll /actuator/health");
    }

    @Test
    void healthCachesResultAcrossRapidCalls() {
        when(graphStore.count()).thenReturn(7L);

        Health first = indicator.health();
        Health second = indicator.health();
        Health third = indicator.health();

        // Only one underlying count() invocation despite three probes — the
        // 30s TTL cache absorbs the flood. Verifies readiness probes at 1Hz
        // don't hammer Cypher.
        org.mockito.Mockito.verify(graphStore, org.mockito.Mockito.times(1)).count();
        assertEquals(Status.UP, first.getStatus());
        assertEquals(Status.UP, second.getStatus());
        assertEquals(Status.UP, third.getStatus());
    }
}
