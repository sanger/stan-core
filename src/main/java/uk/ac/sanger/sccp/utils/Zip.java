package uk.ac.sanger.sccp.utils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

/**
 * Zip functions
 * @author dr6
 */
public class Zip {
    private Zip() {}

    /**
     * Runs a consumer on corresponding elements in two streams
     * @param astream first stream
     * @param bstream second stream
     * @param consumer function to run on elements
     * @param <A> type of element in first stream
     * @param <B> type of element in second stream
     */
    public static <A,B> void forEach(Stream<A> astream, Stream<B> bstream, BiConsumer<? super A, ? super B> consumer) {
        Iterator<? extends A> aiterator = astream.iterator();
        Iterator<? extends B> biterator = bstream.iterator();
        while (aiterator.hasNext() && biterator.hasNext()) {
            A a = aiterator.next();
            B b = biterator.next();
            consumer.accept(a,b);
        }
    }

    /**
     * Runs a combined mapper over corresponding elements in two streams, and streams the results
     * @param astream first stream
     * @param bstream second stream
     * @param mapper function to run on elements
     * @param <A> type of element in first stream
     * @param <B> type of element in second stream
     * @param <C> type of element in returned stream
     */
    public static <A,B,C> Stream<C> map(Stream<A> astream, Stream<B> bstream,
                                           BiFunction<? super A, ? super B, ? extends C> mapper) {
        Spliterator<A> aIterator = astream.spliterator();
        Spliterator<B> bIterator = bstream.spliterator();

        long estimatedSize = Math.min(aIterator.estimateSize(), bIterator.estimateSize());

        final Iterator<A> iterA = Spliterators.iterator(aIterator);
        final Iterator<B> iterB = Spliterators.iterator(bIterator);
        Iterator<C> cIterator = new ZipIterator<>(iterA, iterB, mapper);

        int zipFlags = (
                aIterator.characteristics()
                        & bIterator.characteristics()
                        & ~(Spliterator.DISTINCT | Spliterator.SORTED)
        );

        Spliterator<C> siC = Spliterators.spliterator(cIterator, estimatedSize, zipFlags);
        boolean parallel = (astream.isParallel() || bstream.isParallel());
        return StreamSupport.stream(siC, parallel);
    }

    /**
     * Runs a consumer on items in the stream, with an index.
     * @param astream stream of items to iterate
     * @param consumer function to run on each index and item
     * @param <A> the type of object in the stream
     */
    public static <A> void enumerateForEach(Stream<A> astream, BiConsumer<Integer, ? super A> consumer) {
        int index = 0;
        Iterator<A> aIter = astream.iterator();
        while (aIter.hasNext()) {
            consumer.accept(index, aIter.next());
            ++index;
        }
    }

    /**
     * Creates a stream of items combined with an index
     * @param astream stream of items
     * @param mapper function to combine the index and the item to one object
     * @return a stream of combined objects
     * @param <A> the type of items in the stream argument
     * @param <C> the type of items in the return stream
     */
    public static <A, C> Stream<C> enumerateMap(Stream<A> astream, BiFunction<? super Integer, ? super A, ? extends C> mapper) {
        Spliterator<A> aIterator = astream.spliterator();

        long estimatedSize = aIterator.estimateSize();

        final Iterator<A> iterA = Spliterators.iterator(aIterator);
        Iterator<C> cIterator = new ZipIterator<>(new IntIterator(), iterA, mapper);

        int zipFlags = (
                aIterator.characteristics()
                        & ~(Spliterator.DISTINCT | Spliterator.SORTED)
        );

        Spliterator<C> siC = Spliterators.spliterator(cIterator, estimatedSize, zipFlags);
        boolean parallel = astream.isParallel();
        return StreamSupport.stream(siC, parallel);
    }

    /**
     * An iterator that iterates through two other iterators in parallel, and combines them
     * using a given function.
     */
    static class ZipIterator<A,B,C> implements Iterator<C> {

        private final Iterator<A> aIter;
        private final Iterator<B> bIter;
        private final BiFunction<? super A, ? super B, ? extends C> zipFunction;

        public ZipIterator(Iterator<A> a, Iterator<B> b, BiFunction<? super A, ? super B, ? extends C> zipFunction) {
            this.aIter = a;
            this.bIter = b;
            this.zipFunction = requireNonNull(zipFunction, "zipFunction is null");
        }

        @Override
        public boolean hasNext() {
            return (aIter.hasNext() && bIter.hasNext());
        }

        @Override
        public C next() {
            return zipFunction.apply(aIter.next(), bIter.next());
        }
    }
}
