package io.github.randomcodespace.iq.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeKindTest {

    @Test
    void shouldHave31Values() {
        assertEquals(32, NodeKind.values().length, "NodeKind must have exactly 32 types");
    }

    @Test
    void shouldReturnCorrectValue() {
        assertEquals("module", NodeKind.MODULE.getValue());
        assertEquals("rmi_interface", NodeKind.RMI_INTERFACE.getValue());
        assertEquals("websocket_endpoint", NodeKind.WEBSOCKET_ENDPOINT.getValue());
        assertEquals("abstract_class", NodeKind.ABSTRACT_CLASS.getValue());
        assertEquals("database_connection", NodeKind.DATABASE_CONNECTION.getValue());
    }

    @Test
    void shouldLookUpFromValue() {
        assertEquals(NodeKind.MODULE, NodeKind.fromValue("module"));
        assertEquals(NodeKind.HOOK, NodeKind.fromValue("hook"));
        assertEquals(NodeKind.AZURE_FUNCTION, NodeKind.fromValue("azure_function"));
    }

    @Test
    void shouldThrowOnUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> NodeKind.fromValue("nonexistent"));
    }

    @Test
    void shouldRoundTripAllValues() {
        for (NodeKind kind : NodeKind.values()) {
            assertEquals(kind, NodeKind.fromValue(kind.getValue()),
                    "Round-trip failed for " + kind.name());
        }
    }
}
