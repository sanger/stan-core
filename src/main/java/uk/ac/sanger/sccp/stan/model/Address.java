package uk.ac.sanger.sccp.stan.model;

import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * An address representing a row and a column (both positive ints).
 * @author dr6
 */
@SuppressWarnings("JpaDataSourceORMInspection")
@Embeddable
public class Address implements Comparable<Address> {
    public static final Comparator<Address> COLUMN_MAJOR = Comparator.comparing(Address::getColumn).thenComparing(Address::getRow);

    @Column(name="row_index")
    private int row;
    @Column(name="col_index")
    private int column;

    public Address() {}

    public Address(int row, int column) {
        setRow(row);
        setColumn(column);
    }

    public int getRow() {
        return this.row;
    }

    public int getColumn() {
        return this.column;
    }

    public void setRow(int row) {
        if (row < 1) {
            throw new IllegalArgumentException("Address row cannot be less than 1.");
        }
        this.row = row;
    }

    public void setColumn(int column) {
        if (column < 1) {
            throw new IllegalArgumentException("Address column cannot be less than 1.");
        }
        this.column = column;
    }

    /**
     * Returns a string representation of this address.
     * <p>Formats:
     * <ul>
     *     <li><tt>"B13"</tt> for row 2, column 13 (if {@code row <= 26})</li>
     *     <li><tt>"32,15"</tt> for row 32, column 15 (if {@code row > 26})</li>
     * </ul>
     * @return a string representation of this address
     */
    @Override
    public String toString() {
        if (row <= 26) {
            return String.format("%c%d", 'A'+row-1, column);
        }
        return String.format("%d,%d", row, column);
    }

    /**
     * Parses a string as an address.
     * Row and column must be positive ints.
     * <p>Supported formats:
     * <ul>
     *     <li><tt>"B13"</tt>: row 2, column 13</li>
     *     <li><tt>"32,15"</tt>: row 32, column 15</li>
     * </ul>
     * @param string the string to parse
     * @return the address parsed from the string
     * @exception NullPointerException if the string is null
     * @exception IllegalArgumentException if the string is not parsable as an address
     */
    @SuppressWarnings("DataFlowIssue")
    public static Address valueOf(String string) {
        // Adapted from CGAP LIMS
        Objects.requireNonNull(string, "Cannot convert null to an address.");
        int row = -1, column = -1;
        if (string.length() >= 2) {
            char ch = string.charAt(0);
            if (ch >= 'A' && ch <= 'Z') {
                row = ch - 'A' + 1;
                try {
                    column = Integer.parseInt(string.substring(1));
                } catch (NumberFormatException e) {
                    column = -1;
                }
            } else if (ch >= '0' && ch <= '9') {
                int n = string.indexOf(',');
                if (n > 0) {
                    try {
                        row = Integer.parseInt(string.substring(0, n));
                        column = Integer.parseInt(string.substring(n + 1));
                    } catch (NumberFormatException e) {
                        row = column = -1;
                    }
                }
            }
        }
        if (row < 0 || column < 0) {
            throw new IllegalArgumentException("Invalid address string: " + repr(string));
        }
        return new Address(row, column);
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

    public static Stream<Address> stream(final int numRows, final int numColumns) {
        if (numRows < 1 || numColumns < 1) {
            return Stream.empty();
        }
        return IntStream.range(0, numRows * numColumns)
                .mapToObj(n -> new Address(1 + n / numColumns, 1 + n % numColumns));
    }
}
