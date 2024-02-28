package uk.ac.sanger.sccp.stan.service.graph;

import java.util.*;

/**
 * Iterator to iterate through all nodes of a connected graph starting from the root
 * @author dr6
 */
public class TreeIterator<N> implements Iterator<BuchheimNode<N>> {
    private final Queue<BuchheimNode<N>> queue = new ArrayDeque<>();

    public TreeIterator(BuchheimNode<N> root) {
        this.queue.add(root);
    }

    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    @Override
    public BuchheimNode<N> next() {
        BuchheimNode<N> cur = queue.poll();
        if (cur == null) {
            throw new NoSuchElementException();
        }
        queue.addAll(cur.children);
        return cur;
    }
}
