package io.github.randomcodespace.iq.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts {@code Map<String, Object>} to/from a JSON string for Neo4j storage.
 *
 * Neo4j does not natively support nested map properties. This converter serializes
 * the map as a JSON string on write and deserializes it back on read.
 */
public class MapToJsonConverter implements Neo4jPersistentPropertyConverter<Map<String, Object>> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Value write(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Values.value("{}");
        }
        try {
            return Values.value(mapper.writeValueAsString(source));
        } catch (JsonProcessingException e) {
            return Values.value("{}");
        }
    }

    @Override
    public Map<String, Object> read(Value source) {
        if (source == null || source.isNull()) {
            return new HashMap<>();
        }
        try {
            return mapper.readValue(source.asString(), MAP_TYPE);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
