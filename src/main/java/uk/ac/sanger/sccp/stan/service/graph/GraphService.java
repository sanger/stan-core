package uk.ac.sanger.sccp.stan.service.graph;

import uk.ac.sanger.sccp.stan.request.GraphSVG;
import uk.ac.sanger.sccp.stan.request.history.History;
import uk.ac.sanger.sccp.stan.request.history.HistoryGraph;

/**
 * Service to create history graphs.
 */
public interface GraphService {
    /**
     * Creates the graph of the given history
     * @param history the history to graph
     */
    HistoryGraph createGraph(History history);

    /**
     * Renders history graph to SVG
     * @param fontSize optional font size
     */
    GraphSVG render(HistoryGraph graph, float zoom, Integer fontSize);
}
