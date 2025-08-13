package uk.ac.sanger.sccp.utils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.*;

import static java.util.Objects.requireNonNull;

/**
 * Zip functions
 * @author dr6
 */
public interface Zip<A,B> {
    /**
     * Creates a zip of the two given streams
     * @param aStream first stream
     * @param bStream second stream
     * @return a zip that can go through the streams' corresponding elements
     * @param <A> type of elements in stream a
     * @param <B> type of elements in stream b
     */
    static <A, B> Zip<A, B> of(Stream<? extends A> aStream, Stream<? extends B> bStream) {
        requireNonNull(aStream);
        requireNonNull(bStream);
        return new ZipStream<>(aStream, bStream);
    }

    /**
     * Creates a zip of the given stream with an integer index
     * @param stream the stream of items
     * @return a stream whose items are (i,item) where i is the iteration index, starting at zero
     * @param <B> the type of item in the stream
     */
    static <B> Zip<Integer, B> enumerate(Stream<? extends B> stream) {
        requireNonNull(stream);
        return new ZipStream<>(IntStream.range(0, Integer.MAX_VALUE).boxed(), stream);
    }

    /**
     * Calls the given consumer on pairs of items from the pair of streams
     * @param action the consumer to call on each corresponding pair of items
     */
    void forEach(BiConsumer<? super A, ? super B> action);

    /**
     * Maps the pairs of items in the streams to items given by a combining function
     * @param mapper function to convert items in the input stream to items in the output stream
     * @return a stream of combined items
     * @param <C> type of items in the output stream
     */
    <C> Stream<C> map(BiFunction<? super A, ? super B, ? extends C> mapper);
}

/** Zip implementation */
class ZipStream<A, B> implements Zip<A, B> {
    private final Stream<? extends A> aStream;
    private final Stream<? extends B> bStream;

    ZipStream(Stream<? extends A> aStream, Stream<? extends B> bStream) {
        this.aStream = aStream;
        this.bStream = bStream;
    }

    @Override
    public void forEach(BiConsumer<? super A, ? super B> action) {
        Iterator<? extends A> aiterator = aStream.iterator();
        Iterator<? extends B> biterator = bStream.iterator();
        while (aiterator.hasNext() && biterator.hasNext()) {
            A a = aiterator.next();
            B b = biterator.next();
            action.accept(a, b);
        }
    }

    @Override
    public <C> Stream<C> map(BiFunction<? super A, ? super B, ? extends C> mapper) {
        Spliterator<? extends A> aIterator = aStream.spliterator();
        Spliterator<? extends B> bIterator = bStream.spliterator();

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
        boolean parallel = (aStream.isParallel() || bStream.isParallel());
        return StreamSupport.stream(siC, parallel);
    }
}

/**
 * An iterator that iterates through two other iterators in parallel, and combines them
 * using a given function.
 */
class ZipIterator<A, B, C> implements Iterator<C> {

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
