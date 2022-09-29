package uk.ac.sanger.sccp.stan.model.reagentplate;

import uk.ac.sanger.sccp.stan.model.Address;

import java.util.Objects;

/**
 * A specification for a layout of {@link ReagentPlate}
 * @author dr6
 */
public class ReagentPlateLayout {
    private final String name;
    private final int numRows, numColumns;

    public ReagentPlateLayout(String name, int numRows, int numColumns) {
        this.name = name;
        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    public String getName() {
        return this.name;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    /**
     * Returns the index of the specified address in this plate type's valid addresses.
     * Returns -1 if the address is not valid for this labware type
     * @param address the address to find
     * @return the index found, or -1 if the address is not valid for this labware type
     */
    public int indexOf(Address address) {
        if (address.getRow() < 1 || address.getColumn() < 1
                || address.getRow() > numRows || address.getColumn() > numColumns) {
            return -1;
        }
        return (address.getRow()-1) * numColumns + address.getColumn()-1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentPlateLayout that = (ReagentPlateLayout) o;
        return (Objects.equals(this.name, that.name) &&
                this.numRows == that.numRows && this.numColumns == that.numColumns);
    }

    @Override
    public int hashCode() {
        return 31*numRows + numColumns;
    }

    @Override
    public String toString() {
        return String.format("(name=%s, numRows=%s, numColumns=%s)", name, numRows, numColumns);
    }
}
