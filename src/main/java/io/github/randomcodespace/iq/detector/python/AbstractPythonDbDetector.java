package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.analyzer.InfrastructureRegistry;
import io.github.randomcodespace.iq.detector.DetectorDbHelper;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;

import java.util.List;

/**
 * Abstract base for Python ORM detectors that emit DATABASE_CONNECTION nodes and CONNECTS_TO edges.
 * Extends {@link AbstractPythonAntlrDetector} with shared database node/edge helpers
 * used by Django and SQLAlchemy detectors.
 */
public abstract class AbstractPythonDbDetector extends AbstractPythonAntlrDetector {

    /**
     * Ensure a DATABASE_CONNECTION node exists in the result, creating it if needed.
     * Uses the first database from the InfrastructureRegistry if available,
     * otherwise creates a generic "database:unknown" node.
     *
     * @param registry infrastructure registry (may be null)
     * @param nodes    the nodes list to add the DB node to if missing
     * @return the database node ID
     */
    protected static String ensureDbNode(InfrastructureRegistry registry, List<CodeNode> nodes) {
        return DetectorDbHelper.ensureDbNode(registry, nodes);
    }

    /**
     * Add a CONNECTS_TO edge from the given source node to the database node.
     *
     * @param sourceId the source node ID
     * @param registry infrastructure registry (may be null)
     * @param nodes    the nodes list (used to find/create the DB node)
     * @param edges    the edges list to add the edge to
     */
    protected static void addDbEdge(String sourceId, InfrastructureRegistry registry,
            List<CodeNode> nodes, List<CodeEdge> edges) {
        DetectorDbHelper.addDbEdge(sourceId, registry, nodes, edges);
    }
}
