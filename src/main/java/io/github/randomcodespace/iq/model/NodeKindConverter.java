package io.github.randomcodespace.iq.model;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter;

/**
 * Converts between NodeKind enum and its lowercase string value for Neo4j storage.
 * Neo4j stores "method", "class", etc. — not "METHOD", "CLASS".
 */
public class NodeKindConverter implements Neo4jPersistentPropertyConverter<NodeKind> {

    @Override
    public Value write(NodeKind kind) {
        return org.neo4j.driver.Values.value(kind != null ? kind.getValue() : null);
    }

    @Override
    public NodeKind read(Value source) {
        if (source == null || source.isNull()) return null;
        return NodeKind.fromValue(source.asString());
    }
}
