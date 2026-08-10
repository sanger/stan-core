package uk.ac.sanger.sccp.utils.tsv;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.toLinkedHashSet;

/**
 * Column value generator that joins values from combined entries
 * @author dr6
 */
public class CombinerColumn<E> implements TsvColumn<List<E>> {
    public final TsvColumn<? super E> innerColumn;

    public CombinerColumn(TsvColumn<? super E> innerColumn) {
        this.innerColumn = innerColumn;
    }

    @Override
    public String get(List<E> entries) {
        if (nullOrEmpty(entries)) {
            return null;
        }
        if (entries.size() == 1) {
            return innerColumn.get(entries.getFirst());
        }
        Set<String> values = entries.stream()
                .map(innerColumn::get)
                .filter(Objects::nonNull)
                .collect(toLinkedHashSet());
        if (values.isEmpty()) {
            return null;
        }
        if (values.size()==1) {
            return values.iterator().next();
        }
        return String.join(", ", values);
    }

    @Override
    public String toString() {
        return this.innerColumn.toString();
    }
}
