package uk.ac.sanger.sccp.stan.service.graph.render;

public interface GraphRenderer {
    /**
     * Gets the bounds of the exported graph in render space.
     * @param fontSize the font size used in the graph
     * @return the bounds of the rendered graph
     */
    Bounds getExportBounds(int fontSize);

    /** Renders the graph */
    void render();
}
