package uk.ac.sanger.sccp.utils.tsv;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.streamCaptor;

/**
 * Test {@link XlsxWriter}
 */
public class TestXlsxWriter {
    enum TestColumn {
        Alpha, Beta, Gamma
    }

    private OutputStream out;
    private XlsxWriter writer;

    @BeforeEach
    void setup() {
        out = mock(OutputStream.class);
        writer = spy(new XlsxWriter(out));
    }

    @SuppressWarnings("unchecked")
    TsvData<TestColumn, String> mockData() {
        TsvData<TestColumn, String> data = mock(TsvData.class);
        doReturn(Arrays.asList(TestColumn.values())).when(data).getColumns();
        when(data.getNumRows()).thenReturn(2);
        when(data.getValue(anyInt(), any())).then(invocation -> {
            int rowIndex = invocation.getArgument(0);
            TestColumn column = invocation.getArgument(1);
            if (column==TestColumn.Gamma) {
                return null;
            }
            return column.name() + rowIndex;
        });
        return data;
    }

    @Test
    void testWrite() throws IOException {
        TsvData<TestColumn, String> data = mockData();
        Workbook wb = mock(Workbook.class);
        doReturn(wb).when(writer).createWorkbook();
        Sheet sheet = mock(Sheet.class);
        doReturn(sheet).when(wb).createSheet();
        CellStyle style = mock(CellStyle.class);
        doReturn(style).when(writer).createHeadingsStyle(wb);
        doReturn(null).when(writer).createRow(any(), anyInt(), any(), any());

        writer.write(data);

        verify(writer).createWorkbook();
        verify(wb).createSheet();
        ArgumentCaptor<Stream<String>> headingsCaptor = streamCaptor();
        verify(writer).createRow(same(sheet), eq(0), headingsCaptor.capture(), same(style));
        assertThat(headingsCaptor.getValue()).containsExactly("Alpha", "Beta", "Gamma");

        for (int i = 1; i <= 2; ++i) {
            ArgumentCaptor<Stream<String>> rowCaptor = streamCaptor();
            verify(writer).createRow(same(sheet), eq(i), rowCaptor.capture(), isNull());
            if (i==1) {
                assertThat(rowCaptor.getValue()).containsExactly("Alpha0", "Beta0", null);
            } else {
                assertThat(rowCaptor.getValue()).containsExactly("Alpha1", "Beta1", null);
            }
        }

        verify(wb).write(out);
    }

    @Test
    void testCreateWorkbook() throws IOException {
        assertNotNull(writer.createWorkbook());
    }

    @Test
    void testCreateHeadingsStyle() {
        Workbook wb = mock(Workbook.class);
        CellStyle style = mock(CellStyle.class);
        Font font = mock(Font.class);
        when(wb.createCellStyle()).thenReturn(style);
        when(wb.createFont()).thenReturn(font);

        assertSame(style, writer.createHeadingsStyle(wb));
        verify(wb).createCellStyle();
        verify(wb).createFont();
        verify(font).setBold(true);
        verify(style).setFont(font);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCreateRow(boolean hasStyle) {
        Sheet sheet = mock(Sheet.class);
        final int rowIndex = 5;
        Stream<String> values = Stream.of("Alpha", "Beta", null);
        CellStyle style = (hasStyle ? mock(CellStyle.class) : null);
        Row row = mock(Row.class);
        when(sheet.createRow(anyInt())).thenReturn(row);
        final List<Cell> cells = new ArrayList<>(2);
        when(row.createCell(anyInt(), any())).then(invocation -> {
            Cell cell = mock(Cell.class);
            cells.add(cell);
            return cell;
        });

        assertSame(row, writer.createRow(sheet, rowIndex, values, style));
        verify(sheet).createRow(rowIndex);
        if (style==null) {
            verify(row, never()).setRowStyle(any());
        } else {
            verify(row).setRowStyle(style);
        }
        assertThat(cells).hasSize(3);
        verify(row).createCell(0, CellType.STRING);
        verify(row).createCell(1, CellType.STRING);
        verify(row).createCell(2, CellType.STRING);
        verifyNoMoreInteractions(row);
        cells.forEach(style==null ? (cell -> verify(cell, never()).setCellStyle(any())) : (cell -> verify(cell).setCellStyle(style)));
        verify(cells.get(0)).setCellValue("Alpha");
        verify(cells.get(1)).setCellValue("Beta");
        verify(cells.get(2), never()).setCellValue(any(String.class));
    }

    @Test
    void testValueToString() {
        assertEquals("1", XlsxWriter.valueToString(1));
        assertEquals("Alpha", XlsxWriter.valueToString(TestColumn.Alpha));
        assertNull(XlsxWriter.valueToString(null));
    }

    @Test
    void testClose() throws IOException {
        writer.close();
        verify(out).close();
    }
}
