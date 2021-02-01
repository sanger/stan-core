package uk.ac.sanger.sccp.utils.tsv;

import java.util.List;

/**
 * The data required for a tsv file
 * @author dr6
 */
public interface TsvData<C, E> {
    /**
     * The columns in this tsv file. These should be converted to strings and written at the top of the tsv file,
     * but they are specified as a generic type to allow for them to be (for instance) an enum.
     * @return the columns of this tsv data
     */
    List<C> getColumns();

    /**
     * The number of rows (excluding the header row) of tsv data
     * @return the number of data rows
     */
    int getNumRows();

    /**
     * Gets the value at the specified row and column
     * @param row the row number, from 0 to {@link #getNumRows()}{@code -1}
     * @param column the column, as specified by {@link #getColumns}
     * @return the value at the given point in the tsv file
     */
    E getValue(int row, C column);
}
