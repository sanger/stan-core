package uk.ac.sanger.sccp.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator to issue ints sequentially
 * @author dr6
 */
public class IntIterator implements Iterator<Integer> {
    private final int stopValue;
    private int nextValue;

    public IntIterator(int startValue, int stopValue) {
        this.nextValue = startValue;
        this.stopValue = stopValue;
    }

    public IntIterator(int startValue) {
        this(startValue, Integer.MAX_VALUE);
    }

    public IntIterator() {
        this(0);
    }

    @Override
    public boolean hasNext() {
        return (nextValue < stopValue);
    }

    public int nextInt() {
        int n = nextValue;
        if (n >= stopValue) {
            throw new NoSuchElementException();
        }
        ++nextValue;
        return n;
    }

    @Override
    public Integer next() {
        return nextInt();
    }
}
