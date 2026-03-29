package io.github.randomcodespace.iq.model;

/**
 * Types of nodes in the OSSCodeIQ graph.
 * Mirrors the 31 node kinds from the Python implementation.
 */
public enum NodeKind {

    MODULE("module"),
    PACKAGE("package"),
    CLASS("class"),
    METHOD("method"),
    ENDPOINT("endpoint"),
    ENTITY("entity"),
    REPOSITORY("repository"),
    QUERY("query"),
    MIGRATION("migration"),
    TOPIC("topic"),
    QUEUE("queue"),
    EVENT("event"),
    RMI_INTERFACE("rmi_interface"),
    CONFIG_FILE("config_file"),
    CONFIG_KEY("config_key"),
    WEBSOCKET_ENDPOINT("websocket_endpoint"),
    INTERFACE("interface"),
    ABSTRACT_CLASS("abstract_class"),
    ENUM("enum"),
    ANNOTATION_TYPE("annotation_type"),
    PROTOCOL_MESSAGE("protocol_message"),
    CONFIG_DEFINITION("config_definition"),
    DATABASE_CONNECTION("database_connection"),
    AZURE_RESOURCE("azure_resource"),
    AZURE_FUNCTION("azure_function"),
    MESSAGE_QUEUE("message_queue"),
    INFRA_RESOURCE("infra_resource"),
    COMPONENT("component"),
    GUARD("guard"),
    MIDDLEWARE("middleware"),
    HOOK("hook"),
    SERVICE("service");

    private final String value;

    NodeKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Look up a NodeKind by its string value.
     *
     * @param value the lowercase string value (e.g. "module", "rmi_interface")
     * @return the matching NodeKind
     * @throws IllegalArgumentException if no match found
     */
    public static NodeKind fromValue(String value) {
        for (NodeKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown NodeKind value: " + value);
    }
}
