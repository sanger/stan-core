package uk.ac.sanger.sccp.utils;

import java.util.*;
import java.util.function.*;
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

    /**
     * Maps pairs of items in the streams to a new pair of items given by two combining functions.
     * @param mapper1 function to map pairs of the original streams to the first item in the combined stream
     * @param mapper2 function to map pairs of the original streams to the second item in the combined stream
     * @return a zip to stream combined items
     * @param <C> the first type of element in the combined stream
     * @param <D> the second type of element in the combined stream
     */
    <C,D> Zip<C,D> map(BiFunction<? super A, ? super B, ? extends C> mapper1,
                       BiFunction<? super A, ? super B, ? extends D> mapper2);

    /**
     * Maps each input stream
     * @param mapper1 mapper for first input stream
     * @param mapper2 mapper for second input stream
     * @return zip of combined items
     * @param <C> first type in combined stream
     * @param <D> second type in combined stream
     */
    <C,D> Zip<C,D> map(Function<? super A, ? extends C> mapper1, Function<? super B, ? extends D> mapper2);

    /**
     * Filters the pairs in this stream
     * @param predicate function to determine which pairs to include
     * @return a zip of included pairs from this zip
     */
    Zip<A,B> filter(BiPredicate<? super A,? super B> predicate);
}

/** Zip implementation from two streams */
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
        Spliterator<? extends A> spliterA = aStream.spliterator();
        Spliterator<? extends B> spliterB = bStream.spliterator();

        long estimatedSize = Math.min(spliterA.estimateSize(), spliterB.estimateSize());

        final Iterator<A> iterA = Spliterators.iterator(spliterA);
        final Iterator<B> iterB = Spliterators.iterator(spliterB);
        Iterator<C> cIterator = new ZipIterator<>(iterA, iterB, mapper);

        int zipFlags = (
                spliterA.characteristics()
                        & spliterB.characteristics()
                        & ~(Spliterator.DISTINCT | Spliterator.SORTED)
        );

        Spliterator<C> spliterC = Spliterators.spliterator(cIterator, estimatedSize, zipFlags);
        boolean parallel = (aStream.isParallel() || bStream.isParallel());
        return StreamSupport.stream(spliterC, parallel);
    }

    @Override
    public <C,D> Zip<C,D> map(BiFunction<? super A, ? super B, ? extends C> mapper1,
                              BiFunction<? super A, ? super B, ? extends D> mapper2) {
        requireNonNull(mapper1);
        requireNonNull(mapper2);
        return new EntryZip<>(map(EntryZip.Entry::new)).map(mapper1, mapper2);
    }

    @Override
    public <C, D> Zip<C, D> map(Function<? super A, ? extends C> mapper1, Function<? super B, ? extends D> mapper2) {
        requireNonNull(mapper1);
        requireNonNull(mapper2);
        return new ZipStream<>(aStream.map(mapper1), bStream.map(mapper2));
    }

    @Override
    public Zip<A, B> filter(BiPredicate<? super A, ? super B> predicate) {
        requireNonNull(predicate);
        return new EntryZip<>(map(EntryZip.Entry::new)).filter(predicate);
    }
}

/** Zip implementation from an entry stream */
class EntryZip<A,B> implements Zip<A, B> {
    record Entry<A,B>(A a, B b) {}

    private final Stream<Entry<? extends A, ? extends B>> stream;

    EntryZip(Stream<Entry<? extends A, ? extends B>> stream) {
        this.stream = stream;
    }

    @Override
    public void forEach(BiConsumer<? super A, ? super B> action) {
        requireNonNull(action);
        stream.forEach(e -> action.accept(e.a(), e.b()));
    }

    @Override
    public <C> Stream<C> map(BiFunction<? super A, ? super B, ? extends C> mapper) {
        requireNonNull(mapper);
        return stream.map(e -> mapper.apply(e.a(), e.b()));
    }

    @Override
    public <C,D> Zip<C,D> map(BiFunction<? super A, ? super B, ? extends C> mapper1,
                              BiFunction<? super A, ? super B, ? extends D> mapper2) {
        requireNonNull(mapper1);
        requireNonNull(mapper2);
        Stream<Entry<? extends C, ? extends D>> newStream = stream.map(e -> new Entry<>(mapper1.apply(e.a(), e.b()),
                mapper2.apply(e.a(), e.b())));
        return new EntryZip<>(newStream);
    }

    @Override
    public <C, D> Zip<C, D> map(Function<? super A, ? extends C> mapper1, Function<? super B, ? extends D> mapper2) {
        requireNonNull(mapper1);
        requireNonNull(mapper2);
        Stream<Entry<? extends C, ? extends D>> newStream = stream.map(e -> new Entry<>(mapper1.apply(e.a()), mapper2.apply(e.b())));
        return new EntryZip<>(newStream);
    }

    @Override
    public Zip<A, B> filter(BiPredicate<? super A, ? super B> predicate) {
        requireNonNull(predicate);
        return new EntryZip<>(stream.filter(e -> predicate.test(e.a(), e.b())));
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
