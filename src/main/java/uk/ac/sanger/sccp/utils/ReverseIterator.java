package uk.ac.sanger.sccp.utils;

import java.util.Iterator;
import java.util.ListIterator;


/** An iterator to traverse a list iterator backwards */
class ReverseIterator<T> implements Iterator<T> {
    private final ListIterator<T> iter;

    public ReverseIterator(ListIterator<T> iter) {
        this.iter = iter;
    }

    @Override
    public boolean hasNext() {
        return iter.hasPrevious();
    }

    @Override
    public T next() {
        return iter.previous();
    }

    @Override
    public void remove() {
        iter.remove();
    }
}