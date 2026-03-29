package io.github.randomcodespace.iq.graph;

import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Facade service over the Neo4j graph backend.
 * All graph access goes through this service — never access GraphRepository directly
 * from controllers or other services.
 */
@Service
@ConditionalOnBean(GraphRepository.class)
public class GraphStore {

    private final GraphRepository repository;

    public GraphStore(GraphRepository repository) {
        this.repository = repository;
    }

    public CodeNode save(CodeNode node) {
        return repository.save(node);
    }

    public List<CodeNode> saveAll(Iterable<CodeNode> nodes) {
        return repository.saveAll(nodes);
    }

    public Optional<CodeNode> findById(String id) {
        return repository.findById(id);
    }

    public List<CodeNode> findAll() {
        return repository.findAll();
    }

    public List<CodeNode> findByKind(NodeKind kind) {
        return repository.findByKind(kind.getValue());
    }

    public List<CodeNode> findByLayer(String layer) {
        return repository.findByLayer(layer);
    }

    public List<CodeNode> findByModule(String module) {
        return repository.findByModule(module);
    }

    public List<CodeNode> findByFilePath(String filePath) {
        return repository.findByFilePath(filePath);
    }

    public List<CodeNode> search(String text) {
        return repository.search(text);
    }

    public List<CodeNode> findNeighbors(String nodeId) {
        return repository.findNeighbors(nodeId);
    }

    public long count() {
        return repository.count();
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    public void deleteById(String id) {
        repository.deleteById(id);
    }
}
