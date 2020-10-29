package uk.ac.sanger.sccp.stan.model;

import org.jetbrains.annotations.NotNull;

import javax.persistence.Embeddable;

/**
 * @author dr6
 */
@Embeddable
public class Address implements Comparable<Address> {
    private int row, column;

    public Address() {}

    public Address(int row, int column) {
        if (row < 1) {
            throw new IllegalArgumentException("Row cannot be less than 1.");
        }
        if (column < 1) {
            throw new IllegalArgumentException("Column cannot be less than 1.");
        }
        this.row = row;
        this.column = column;
    }

    public int getRow() {
        return this.row;
    }

    public int getColumn() {
        return this.column;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    @Override
    public String toString() {
        if (row <= 26) {
            return String.format("%c%d", 'A'+row-1, column);
        }
        return String.format("%d,%d", row, column);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address that = (Address) o;
        return (this.row == that.row && this.column == that.column);
    }

    @Override
    public int hashCode() {
        return row + 63*column;
    }

    @Override
    public int compareTo(@NotNull Address that) {
        if (this.row!=that.row) {
            return (this.row < that.row ? -1 : 1);
        }
        if (this.column!=that.column) {
            return (this.column < that.column ? -1 : 1);
        }
        return 0;
    }
}
