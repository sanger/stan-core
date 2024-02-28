package uk.ac.sanger.sccp.utils;

import javax.annotation.CheckReturnValue;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Implementation of an unstable topological sort
 * @author dr6
 */
public class TopoSorter<E> {
    private final Map<E, Set<E>> destToSrcs;
    private final Map<E, Set<E>> srcToDests;
    private final Collection<E> items;

    /**
     * Constructs a new sorter to sort the given items.
     * @param items the items that will be sorted
     */
    public TopoSorter(Collection<E> items) {
        this.items = items;
        this.destToSrcs = new HashMap<>();
        this.srcToDests = new HashMap<>();
    }

    /**
     * Adds a link between two items. This is a requirement that <tt>src</tt>
     * should come somewhere before <tt>dest</tt> in the sorted output.
     * @param src a preceding item
     * @param dest a subsequent item
     */
    public void addLink(E src, E dest) {
        destToSrcs.computeIfAbsent(dest, k -> new HashSet<>()).add(src);
        srcToDests.computeIfAbsent(src, k -> new HashSet<>()).add(dest);
    }

    /**
     * Removes the given items as sources from the links.
     * @param srcs the sources to remove
     * @return the items that now have no source, and can be added to the sorted output
     */
    public Set<E> removeSrcs(Collection<E> srcs) {
        Set<E> newUnsourced = new HashSet<>();
        for (E src : srcs) {
            for (E dest : srcToDests.getOrDefault(src, Set.of())) {
                var destSrcs = destToSrcs.get(dest);
                destSrcs.remove(src);
                if (destSrcs.isEmpty()) {
                    newUnsourced.add(dest);
                }
            }
        }
        srcToDests.keySet().removeAll(srcs);
        return newUnsourced;
    }

    /**
     * Finds items that are not the source of any link
     * @return set of items that are not a source
     */
    public Set<E> findUnsourced() {
        Set<E> unsourced = new HashSet<>(items);
        for (Map.Entry<E,Set<E>> entry : destToSrcs.entrySet()) {
            if (!nullOrEmpty(entry.getValue())) {
                unsourced.remove(entry.getKey());
            }
        }
        return unsourced;
    }

    /**
     * Creates sorted output of this sorter's items according to this sorter's links.
     * @return the sorted list
     */
    @CheckReturnValue
    public List<E> sort() {
        List<E> sorted = new ArrayList<>(this.items.size());
        Set<E> nextLinks = findUnsourced();
        while (!nextLinks.isEmpty()) {
            sorted.addAll(nextLinks);
            nextLinks = removeSrcs(nextLinks);
        }
        if (sorted.size() != this.items.size()) {
            throw new IllegalArgumentException("The given items cannot be topologically sorted.");
        }
        return sorted;
    }
}
