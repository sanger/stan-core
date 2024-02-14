package uk.ac.sanger.sccp.utils;

import java.util.*;

/**
 * Implementation of an unstable topological sort
 * @author dr6
 */
public class TopoSorter<E> {
    private final Map<E, Set<E>> destToSrcs;
    private final Map<E, Set<E>> srcToDests;
    private final Set<E> unsourced;
    private final int size;

    public TopoSorter(Collection<E> items) {
        unsourced = new HashSet<>(items);
        destToSrcs = new HashMap<>();
        srcToDests = new HashMap<>();
        size = items.size();
    }

    public void addLink(E src, E dest) {
        destToSrcs.computeIfAbsent(dest, k -> new HashSet<>()).add(src);
        srcToDests.computeIfAbsent(src, k -> new HashSet<>()).add(dest);
        unsourced.remove(dest);
    }

    public void removeSrcs(Collection<E> srcs) {
        for (E src : srcs) {
            for (E dest : srcToDests.get(src)) {
                var destSrcs = destToSrcs.get(dest);
                destSrcs.remove(src);
                if (destSrcs.isEmpty()) {
                    unsourced.add(dest);
                }
            }
        }
        srcToDests.keySet().removeAll(srcs);
    }

    public List<E> sort() {
        List<E> sorted = new ArrayList<>(size);
        while (!unsourced.isEmpty()) {
            sorted.addAll(unsourced);
            removeSrcs(unsourced);
        }
        return sorted;
    }
}
