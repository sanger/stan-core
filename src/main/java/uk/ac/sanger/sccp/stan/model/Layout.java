package uk.ac.sanger.sccp.stan.model;

/**
 * Layout helper.
 * Calculates address positions inside a particular rectangular layout.
 * @author dr6
 */
public class Layout {
    private final int numRows, numColumns;

    public Layout(int numRows, int numColumns) {
        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    public int numRows() {
        return this.numRows;
    }

    public int numColumns() {
        return this.numColumns;
    }

    public int indexOf(Address address) {
        final int row = address.getRow();
        final int column = address.getColumn();
        if (row < 1 || column < 1 || row > numRows || column > numColumns) {
            return -1;
        }
        return (row - 1) * numColumns + column - 1;
    }

    public Address addressAt(final int index) {
        if (index < 0) {
            return null;
        }
        int ri = index/numColumns;
        int ci = index%numColumns;
        if (ri >= numRows) {
            return null;
        }
        return new Address(ri + 1, ci + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Layout that = (Layout) o;
        return (this.numRows == that.numRows && this.numColumns == that.numColumns);
    }

    @Override
    public int hashCode() {
        return numRows + 63*numColumns;
    }

    @Override
    public String toString() {
        return String.format("Layout(%s,%s)", numRows, numColumns);
    }
}
