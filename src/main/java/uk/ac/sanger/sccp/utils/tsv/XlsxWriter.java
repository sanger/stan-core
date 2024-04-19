package uk.ac.sanger.sccp.utils.tsv;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility to write data as an xlsx file to an OutputStream.
 * @author dr6
 */
public class XlsxWriter implements TableFileWriter {
    private final OutputStream out;

    public XlsxWriter(OutputStream out) {
        this.out = out;
    }

    @Override
    public <C, V> void write(TsvData<C, V> data) throws IOException {
        final List<? extends C> columns = data.getColumns();
        final int numRows = data.getNumRows();
        try (Workbook wb = createWorkbook()) {
            Sheet sheet = wb.createSheet();
            createRow(sheet, 0, columns.stream().map(Object::toString), createHeadingsStyle(wb));
            for (int fileRow = 1; fileRow <= numRows; fileRow++) {
                final int dataRow = fileRow - 1;
                createRow(sheet, fileRow, columns.stream()
                                .map(column -> valueToString(data.getValue(dataRow, column))),
                        null);
            }
            wb.write(out);
        }
    }

    /** Creates a new Xssf workbook from the POI factory */
    public Workbook createWorkbook() throws IOException {
        return WorkbookFactory.create(true);
    }

    /** Creates a style suitable for headings in the given workbook */
    protected CellStyle createHeadingsStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font headingsFont = wb.createFont();
        headingsFont.setBold(true);
        style.setFont(headingsFont);
        return style;
    }

    /**
     * Adds a row to the given sheet
     * @param sheet the sheet to modify
     * @param rowIndex the index of the row to add
     * @param values the values to put in the row
     * @param style the style to use for the row and its cells (may be null)
     * @return the new row
     */
    protected Row createRow(Sheet sheet, int rowIndex, Stream<String> values, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        if (style!=null) {
            row.setRowStyle(style);
        }
        Iterator<String> iter = values.iterator();
        int columnIndex = 0;
        while (iter.hasNext()) {
            String value = iter.next();
            Cell cell = row.createCell(columnIndex, CellType.STRING);
            if (style!=null) {
                cell.setCellStyle(style);
            }
            if (value != null) {
                cell.setCellValue(value);
            }
            ++columnIndex;
        }
        return row;
    }

    /**
     * Null-ignoring toString method
     * @param value object to convert
     * @return null if the object is null, otherwise the result of calling {@code toString()} on it
     */
    protected static String valueToString(Object value) {
        return (value==null ? null : value.toString());
    }

    /** Closes the underlying output stream */
    @Override
    public void close() throws IOException {
        out.close();
    }
}
