package uk.ac.sanger.sccp.stan.model.store;

/**
 * A grid size, number of rows and columns
 * @author dr6
 */
public class Size {
    private int numRows;
    private int numColumns;

    public Size() {
        this(1,1);
    }

    public Size(int numRows, int numColumns) {
        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public Size numRows(int numRows) {
        this.numRows = numRows;
        return this;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    public Size numColumns(int numColumns) {
        this.numColumns = numColumns;
        return this;
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
