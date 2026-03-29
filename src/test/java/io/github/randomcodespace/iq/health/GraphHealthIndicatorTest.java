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
        assertEquals("No graph data", health.getDetails().get("reason"));
        assertEquals(0, health.getDetails().get("nodes"));
    }

    @Test
    void healthShouldBeDownWhenStoreThrows() {
        when(graphStore.count()).thenThrow(new RuntimeException("DB connection failed"));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Graph store unavailable", health.getDetails().get("reason"));
        assertEquals("DB connection failed", health.getDetails().get("error"));
    }
}
