package uk.ac.sanger.sccp.utils.tsv;

import java.io.*;
import java.util.Iterator;
import java.util.List;

/**
 * Utility to write a tsv file to an OutputStream.
 * With alternative fields for {@code separator} etc., you could also write a csv or other format.
 * Any value containing the separator (e.g. {@code \t}) will be quoted using the specified
 * quote-character (e.g. {@code "}), and quotes will be escaped with a quote escape character
 * (by default {@code "} is escaped to {@code ""}).
 * @author dr6
 */
public class TsvWriter implements Closeable {
    private final char separator;
    private final char quote;
    private final char quoteEscape;
    private final char newline;

    private final OutputStream out;

    public TsvWriter(OutputStream out) {
        this(out, '\t', '"', '"', '\n');
    }

    public TsvWriter(OutputStream out, char separator, char quote, char quoteEscape, char newline) {
        this.out = out;
        this.separator = separator;
        this.quote = quote;
        this.quoteEscape = quoteEscape;
        this.newline = newline;
    }

    public <C, V> void write(TsvData<C, V> data) throws IOException {
        final List<? extends C> columns = data.getColumns();
        writeLn(columns.stream().map(Object::toString).iterator());
        final int numRows = data.getNumRows();
        for (int i = 0; i < numRows; ++i) {
            final int row = i;
            writeLn(columns.stream()
                    .map(column -> data.getValue(row, column))
                    .map(this::valueToString).iterator());
        }
    }

    protected String valueToString(Object value) {
        return (value==null ? null : value.toString());
    }

    private void writeLn(Iterator<String> iter) throws IOException {
        if (!iter.hasNext()) {
            return;
        }
        write(iter.next());
        while (iter.hasNext()) {
            if (separator!=0) {
                write(separator);
            }
            write(iter.next());
        }
        out.write(newline);
    }

    private void write(String value) throws IOException {
        if (value==null) {
            return; // omit null
        }
        boolean addQuotes = (quote!=0 && (separator!=0 && value.indexOf(separator) >= 0)
                            || (quoteEscape!=0 && value.indexOf(quote) >= 0));
        if (addQuotes) {
            out.write(quote);
        }
        int len = value.length();
        for (int i = 0; i < len; ++i) {
            write(value.charAt(i));
        }
        if (addQuotes) {
            out.write(quote);
        }
    }

    private void write(char ch) throws IOException {
        if (ch==quote && quoteEscape!=0) {
            out.write(quoteEscape);
        }
        out.write(ch);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
