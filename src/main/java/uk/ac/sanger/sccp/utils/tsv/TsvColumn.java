package uk.ac.sanger.sccp.utils.tsv;

/**
 * A column, that is, something that can extract a single string value from a row.
 * @param <R>
 */
public interface TsvColumn<R> {
    /**
     * The value for this column from the given row
     * @param entry the row
     * @return the value for this column and the given row
     */
    String get(R entry);

    /**
     * The string for this column should be usable as the title of the column.
     * @return the title of the column
     */
    String toString();
}
