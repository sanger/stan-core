package uk.ac.sanger.sccp.utils.tsv;

import java.io.Closeable;
import java.io.IOException;

public interface TableFileWriter extends Closeable {
    /**
     * Writes the table data to this writer's output stream
     * @param data data to write
     * @param <C> type representing columns in the table
     * @param <V> type representing values in the table
     * @exception IOException if a problem happened during writing
     */
    <C, V> void write(TsvData<C, V> data) throws IOException;
}
