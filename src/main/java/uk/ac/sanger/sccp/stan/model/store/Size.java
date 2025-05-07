package uk.ac.sanger.sccp.stan.model.store;

import uk.ac.sanger.sccp.stan.model.Address;

/**
 * A grid size, number of rows and columns
 * @author dr6
 */
public class Size {
    private int numRows;
    private int numColumns;

    /** Creates a size with one row and one column */
    public Size() {
        this(1,1);
    }

    /** Creates a size with the given number of rows and columns. */
    public Size(int numRows, int numColumns) {
        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    public int getNumRows() {
        return this.numRows;
    }

    /**
     * Sets the number of rows in this size
     * @param numRows the new number of rows
     * @return this size
     */
    public Size numRows(int numRows) {
        this.numRows = numRows;
        return this;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    /**
     * Sets the number of columns in this size
     * @param numColumns the new number of columns
     * @return this size
     */
    public Size numColumns(int numColumns) {
        this.numColumns = numColumns;
        return this;
    }

    /** Does the given address fit inside this size? */
    public boolean contains(Address address) {
        return (address != null && address.getRow() >= 1 && address.getRow() <= this.numRows
                && address.getColumn() >= 1 && address.getColumn() <= this.numColumns);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Size that = (Size) o;
        return (this.numRows == that.numRows
                && this.numColumns == that.numColumns);
    }

    @Override
    public int hashCode() {
        return numRows + 63*numColumns;
    }

    @Override
    public String toString() {
        return String.format("(numRows=%s, numColumns=%s)", numRows, numColumns);
    }
}
