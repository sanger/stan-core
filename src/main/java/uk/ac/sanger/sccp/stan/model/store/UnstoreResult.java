package uk.ac.sanger.sccp.stan.model.store;

import com.google.common.base.MoreObjects;

import java.util.*;

/**
 * The result of unstoring some items
 * @author dr6
 */
public class UnstoreResult {
    private List<UnstoredItem> unstored;

    public UnstoreResult() {
        this(null);
    }

    public UnstoreResult(Collection<UnstoredItem> unstored) {
        setUnstored(unstored);
    }

    public List<UnstoredItem> getUnstored() {
        return this.unstored;
    }

    public void setUnstored(Collection<UnstoredItem> unstored) {
        this.unstored = (unstored==null ? new ArrayList<>() : new ArrayList<>(unstored));
    }

    public int getNumUnstored() {
        return this.unstored.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnstoreResult that = (UnstoreResult) o;
        return (Objects.equals(this.unstored, that.unstored));
    }

    @Override
    public int hashCode() {
        return Objects.hash(unstored);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("unstored", unstored)
                .toString();
    }
}
