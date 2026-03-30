package io.github.randomcodespace.iq.graph;

import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphStoreTest {

    @Mock
    private GraphRepository repository;

    @Mock
    private org.neo4j.graphdb.GraphDatabaseService graphDb;

    private GraphStore store;

    @BeforeEach
    void setUp() {
        store = new GraphStore(repository, graphDb);
    }

    @Test
    void shouldSaveNode() {
        var node = new CodeNode("mod:app.py:module:app", NodeKind.MODULE, "app");
        when(repository.save(node)).thenReturn(node);

        var saved = store.save(node);

        assertEquals(node, saved);
        verify(repository).save(node);
    }

    @Test
    void shouldFindById() {
        var node = new CodeNode("mod:app.py:module:app", NodeKind.MODULE, "app");
        when(repository.findById("mod:app.py:module:app")).thenReturn(Optional.of(node));

        var result = store.findById("mod:app.py:module:app");

        assertTrue(result.isPresent());
        assertEquals(node, result.get());
    }

    @Test
    void shouldReturnEmptyForMissingId() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        var result = store.findById("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindByKind() {
        var node = new CodeNode("ep:routes.py:endpoint:get_users", NodeKind.ENDPOINT, "get_users");
        when(repository.findByKind("endpoint")).thenReturn(List.of(node));

        var results = store.findByKind(NodeKind.ENDPOINT);

        assertEquals(1, results.size());
        assertEquals(node, results.getFirst());
    }

    @Test
    void shouldCount() {
        when(repository.count()).thenReturn(42L);

        assertEquals(42L, store.count());
    }

    @Test
    void shouldDeleteAll() {
        store.deleteAll();

        verify(repository).deleteAll();
    }

    @Test
    void shouldSearch() {
        var node = new CodeNode("cls:models.py:class:User", NodeKind.CLASS, "User");
        when(repository.search("User")).thenReturn(List.of(node));

        var results = store.search("User");

        assertEquals(1, results.size());
        assertEquals("User", results.getFirst().getLabel());
    }
}
