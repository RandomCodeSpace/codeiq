package io.github.randomcodespace.iq.graph;

import io.github.randomcodespace.iq.model.CodeNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Neo4j repository for CodeNode entities.
 */
@Repository
public interface GraphRepository extends Neo4jRepository<CodeNode, String> {

    List<CodeNode> findByKind(String kind);

    List<CodeNode> findByLayer(String layer);

    List<CodeNode> findByModule(String module);

    @Query("MATCH (n:CodeNode) WHERE n.filePath = $filePath RETURN n")
    List<CodeNode> findByFilePath(String filePath);

    @Query("MATCH (n:CodeNode) WHERE n.label CONTAINS $text OR n.fqn CONTAINS $text RETURN n")
    List<CodeNode> search(String text);

    @Query("MATCH (n:CodeNode)-[r]-(m:CodeNode) WHERE n.id = $nodeId RETURN m")
    List<CodeNode> findNeighbors(String nodeId);

    @Query("MATCH (n:CodeNode)-[r]->(m:CodeNode) WHERE n.id = $nodeId RETURN m")
    List<CodeNode> findOutgoingNeighbors(String nodeId);

    @Query("MATCH (n:CodeNode)<-[r]-(m:CodeNode) WHERE n.id = $nodeId RETURN m")
    List<CodeNode> findIncomingNeighbors(String nodeId);
}
