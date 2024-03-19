package uk.ac.sanger.sccp.stan.service.graph;

import java.util.List;

public interface Assembler {
    /**
     * Position several graphs in one space so they do not overlap.
     * @param roots the roots of the graphs
     * @param padding amount of space required between nodes with the same y-position
     * @param <N> the generic type of the nodes
     */
    <N> void assemble(List<BuchheimNode<N>> roots, double padding);
}
