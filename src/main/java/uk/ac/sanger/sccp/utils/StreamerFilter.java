package uk.ac.sanger.sccp.utils;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

/**
 * Utility for helping with streaming and filtering objects from different sources.
 * @author dr6
 */
public class StreamerFilter<E> {
    private final List<Predicate<E>> filters = new ArrayList<>();
    private Supplier<? extends Iterable<E>> source;

    /**
     * Adds a collections of items as either a source or a filter.
     * @param items the items specified
     * @param getter a way to get the item value from the entity e
     * @param sourceFunction a way to get a source of entities e from the given items
     * @param <T> the type of item
     */
    public <T> void addFilter(Collection<T> items, Function<E, T> getter,
                              Function<? super Collection<T>, ? extends Iterable<E>> sourceFunction) {
        if (items!=null) {
            if (!hasSource()) {
                setSource(new ItemSource<>(items, sourceFunction));
            } else {
                this.filters.add(new ItemFilter<>(items, getter));
            }
        }
    }

    /**
     * Creates a predicate matching the filters specified in this.
     * @return a predicate for filtering the {@code E} entities
     */
    public Predicate<E> filter() {
        if (filters.isEmpty()) {
            return E -> true;
        }
        return filters.stream()
                .reduce(Predicate::and)
                .orElseThrow();
    }

    /**
     * Sets the source for this streamerfilter.
     * @param source a function to return an iterable sequence of entities
     */
    public void setSource(Supplier<? extends Iterable<E>> source) {
        this.source = source;
    }

    /**
     * Does this streamerfilter have its source set?
     * @return true if this streamerfilter has its source set; false otherwise
     */
    public boolean hasSource() {
        return (this.source != null);
    }

    /**
     * Gets the stream from this streamerfilters's source, filtered through its {@link #filter}.
     * @return a stream of entities
     */
    public Stream<E> filterStream() {
        Stream<E> stream = BasicUtils.stream(source.get());
        if (filters.isEmpty()) {
            return stream;
        }
        return stream.filter(filter());
    }


    static class ItemFilter<T,E> implements Predicate<E> {
        private final Collection<T> items;
        private final Function<E, T> getter;

        ItemFilter(Collection<T> items, Function<E, T> getter) {
            this.items = items;
            this.getter = getter;
        }

        @Override
        public boolean test(E e) {
            return (this.items==null || items.contains(getter.apply(e)));
        }
    }

    static class ItemSource<T,E> implements Supplier<Iterable<E>> {
        Collection<T> input;
        Function<? super Collection<T>, ? extends Iterable<E>> function;

        public ItemSource(Collection<T> input, Function<? super Collection<T>, ? extends Iterable<E>> function) {
            this.input = input;
            this.function = function;
        }

        @Override
        public Iterable<E> get() {
            return function.apply(input);
        }
    }
}
