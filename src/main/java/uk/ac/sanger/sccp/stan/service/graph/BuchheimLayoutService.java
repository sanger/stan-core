package uk.ac.sanger.sccp.stan.service.graph;

import uk.ac.sanger.sccp.stan.request.history.HistoryGraph.Link;
import uk.ac.sanger.sccp.stan.request.history.HistoryGraph.Node;

import java.util.List;

/**
 * Service to lay out graph nodes using Buchheim layout.
 * The graph nodes' y-position is set according to chronological sequence.
 * Their x-positions are set based on parent-child relationships.
 * @author dr6
 */
public interface BuchheimLayoutService {
    /**
     * Sets the positions of the supplied nodes
     * @param nodes the nodes to position
     * @param links the links between nodes
     */
    void layout(List<Node> nodes, List<Link> links);
}
