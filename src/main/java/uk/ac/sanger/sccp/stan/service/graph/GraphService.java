package uk.ac.sanger.sccp.stan.service.graph;

import uk.ac.sanger.sccp.stan.model.HistoryGraph;
import uk.ac.sanger.sccp.stan.request.History;

/**
 * Service to create history graphs.
 */
public interface GraphService {
    /**
     * Creates the graph of the given history
     * @param history the history to graph
     */
    HistoryGraph createGraph(History history);
}
