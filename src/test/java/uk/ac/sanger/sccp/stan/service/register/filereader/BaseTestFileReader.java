package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author dr6
 */
abstract class BaseTestFileReader {

    int HEADING_ROW = 1;

    Sheet sheet;
    Row headingRow;
    List<Row> rows;

    void mockSheet() {
        sheet = mock(Sheet.class);
    }
    void mockHeadingRow() {
        headingRow = mock(Row.class);
        when(headingRow.getRowNum()).thenReturn(HEADING_ROW);
        if (sheet != null) {
            when(sheet.getRow(HEADING_ROW)).thenReturn(headingRow);
        }
    }
    void mockRows(int startRange, int endRange) {
        rows = IntStream.range(startRange, endRange)
                .mapToObj(i -> {
                    if (headingRow!=null && i==HEADING_ROW) {
                        return headingRow;
                    }
                    Row row = mock(Row.class);
                    when(row.getRowNum()).thenReturn(i);
                    when(sheet.getRow(i)).thenReturn(row);
                    return row;
                })
                .collect(toList());
        when(sheet.spliterator()).thenReturn(rows.spliterator());
        when(sheet.iterator()).thenReturn(rows.iterator());
    }
    Row mockRow(String... values) {
        Row row = mock(Row.class);
        List<Cell> cells = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; ++i) {
            String value = values[i];
            Cell cell = mock(Cell.class);
            when(cell.getStringCellValue()).thenReturn(value);
            when(cell.getColumnIndex()).thenReturn(i);
            when(row.getCell(i)).thenReturn(cell);
            cells.add(cell);
        }
        when(row.spliterator()).thenReturn(cells.spliterator());
        when(row.iterator()).thenReturn(cells.iterator());
        return row;
    }

    static Cell mockCell(CellType cellType) {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(cellType);
        return cell;
    }

}
