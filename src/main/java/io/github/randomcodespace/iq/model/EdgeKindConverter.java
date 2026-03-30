package io.github.randomcodespace.iq.model;

import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter;

/**
 * Converts between EdgeKind enum and its lowercase string value for Neo4j storage.
 * Neo4j stores "depends_on", "calls", etc. — not "DEPENDS_ON", "CALLS".
 */
public class EdgeKindConverter implements Neo4jPersistentPropertyConverter<EdgeKind> {

    @Override
    public Value write(EdgeKind kind) {
        return org.neo4j.driver.Values.value(kind != null ? kind.getValue() : null);
    }

    @Override
    public EdgeKind read(Value source) {
        if (source == null || source.isNull()) return null;
        return EdgeKind.fromValue(source.asString());
    }
}
