package io.github.randomcodespace.iq.model;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An edge (relationship) in the OSSCodeIQ knowledge graph.
 */
@RelationshipProperties
public class CodeEdge {

    @Id
    @GeneratedValue
    private Long internalId;

    private String id;

    private EdgeKind kind;

    private String sourceId;

    @TargetNode
    private CodeNode target;

    private Map<String, Object> properties = new HashMap<>();

    public CodeEdge() {
    }

    public CodeEdge(String id, EdgeKind kind, String sourceId, CodeNode target) {
        this.id = id;
        this.kind = kind;
        this.sourceId = sourceId;
        this.target = target;
    }

    // --- Getters and Setters ---

    public Long getInternalId() {
        return internalId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public EdgeKind getKind() {
        return kind;
    }

    public void setKind(EdgeKind kind) {
        this.kind = kind;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public CodeNode getTarget() {
        return target;
    }

    public void setTarget(CodeNode target) {
        this.target = target;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeEdge codeEdge = (CodeEdge) o;
        return Objects.equals(id, codeEdge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CodeEdge{id='%s', kind=%s, source='%s'}".formatted(id, kind, sourceId);
    }
}
