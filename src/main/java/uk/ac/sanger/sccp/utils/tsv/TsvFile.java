package uk.ac.sanger.sccp.utils.tsv;

import java.util.List;

/**
 * The data associated with a tsv file (a filename and some contents)
 * @author dr6
 */
public class TsvFile<E> implements TsvData<TsvColumn<E>, String> {
    private final String filename;
    private final List<E> entries;
    private final List<TsvColumn<E>> columns;

    public TsvFile(String filename, List<E> entries, List<TsvColumn<E>> columns) {
        this.filename = filename;
        this.entries = entries;
        this.columns = columns;
    }

    @Override
    public List<TsvColumn<E>> getColumns() {
        return this.columns;
    }

    @Override
    public int getNumRows() {
        return this.entries.size();
    }

    @Override
    public String getValue(int row, TsvColumn<E> column) {
        return column.get(entries.get(row));
    }

    public String getFilename() {
        return this.filename;
    }
}
