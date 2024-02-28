package uk.ac.sanger.sccp.stan.service.graph;

import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.utils.TopoSorter;

import java.util.Collection;

/**
 * Factory for a topological sorter
 * @author dr6
 */
@Component
public class TopoSorterFactory {
    /**
     * Creates and returns a topological sorter for the given items.
     * Links should be added to the sorter before its sort method is used.
     * @param items the items to sort
     * @return a topological sorter to sort the given items
     * @param <E> the type of the items
     */
    public <E> TopoSorter<E> createSorter(Collection<E> items) {
        return new TopoSorter<>(items);
    }
}
