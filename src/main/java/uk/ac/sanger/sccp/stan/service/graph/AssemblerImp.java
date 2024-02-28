package uk.ac.sanger.sccp.stan.service.graph;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author dr6
 */
@Service
public class AssemblerImp implements Assembler {
    @Override
    public <N> void assemble(List<BuchheimNode<N>> roots, double padding) {
        Map<Integer, double[]> mergedBounds = new HashMap<>(); // bounds at each y-position
        int i = 0;
        for (BuchheimNode<?> root : roots) {
            if (i > 0) {
                double overlap = findOverlap(mergedBounds, root);
                if (overlap > 0) {
                    shift(root, overlap);
                }
            }
            if (i < roots.size() - 1) {
                addBounds(mergedBounds, root, padding);
            }
            ++i;
        }
    }

    /**
     * Finds the amount of overlap something something
     * @param leftBounds the bounds of the graphs to the left
     * @param root the root of the next graph
     * @return the amount of overlap between the new graph and the previous graphs (zero if none)
     */
    public double findOverlap(Map<Integer, double[]> leftBounds, BuchheimNode<?> root) {
        if (leftBounds.isEmpty()) {
            return 0;
        }
        double greatestOverlap = 0;
        for (BuchheimNode<?> node : root.tree()) {
            double[] bounds = leftBounds.get(node.y);
            if (bounds!=null && bounds[1] > node.x) {
                greatestOverlap = Math.max(greatestOverlap, bounds[1] - node.x);
            }
        }
        return greatestOverlap;
    }

    /**
     * Shifts a graph to the right
     * @param root the root of the graph
     * @param dx the amount to shift right
     */
    public void shift(BuchheimNode<?> root, double dx) {
        root.tree().forEach(node -> node.x += dx);
    }

    /**
     * Adds the bounds of a graph to the given map of bounds
     * @param bounds a map of the x-bounds at each y-position
     * @param root the root of the new graph
     * @param padding the amount of padding to allow between two adjacent nodes with the same y-position
     */
    public void addBounds(Map<Integer, double[]> bounds, BuchheimNode<?> root, double padding) {
        for (BuchheimNode<?> node : root.tree()) {
            double[] bds = bounds.get(node.y);
            if (bds==null) {
                bounds.put(node.y, new double[] { node.x, node.x + padding });
            } else {
                bds[0] = Math.min(bds[0], node.x);
                bds[1] = Math.max(bds[1], node.x + padding);
            }
        }
    }
}
