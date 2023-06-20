package uk.ac.sanger.sccp.stan.service.register;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterContent;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterLabware;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.register.SectionRegisterFileReader.Column;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SectionRegisterFileReaderImp}
 */
class TestSectionRegisterFileReader {
    private static final int HEADING_ROW = 2, DATA_ROW = 4;

    private Sheet sheet;
    private Row headingRow;
    private List<Row> rows;

    private SectionRegisterFileReaderImp reader;

    @BeforeEach
    void testSetUp() {
        reader = spy(new SectionRegisterFileReaderImp(HEADING_ROW, DATA_ROW));
    }

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

    @Test
    void testRead_headingProblems() {
        final String problem = "Heading problem";
        Map<Column, Integer> map = Map.of();
        mockSheet();
        mockHeadingRow();
        doAnswer(Matchers.addProblem(problem, map)).when(reader).indexColumns(any(), any());
        assertValidationError(() -> reader.read(sheet), problem);
        verify(reader).indexColumns(any(), same(headingRow));
        verify(reader, never()).readRow(any(), any(), any());
        verify(reader, never()).createRequest(any(), any());
    }

    @Test
    void testRead_noData() {
        mockSheet();
        mockHeadingRow();
        mockRows(0,0);
        Map<Column, Integer> map = columnMapOf(Column.Donor_ID, 3);
        doReturn(map).when(reader).indexColumns(any(), any());
        assertValidationError(() -> reader.read(sheet), "No registrations requested.");
        verify(reader, never()).readRow(any(), any(), any());
        verify(reader, never()).createRequest(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testRead(boolean error) {
        mockSheet();
        mockHeadingRow();
        mockRows(0, DATA_ROW+3);
        Map<Column, Integer> columnIndex = columnMapOf(Column.Donor_ID, 3);
        doReturn(columnIndex).when(reader).indexColumns(any(), any());
        final String problem = error ? "Problem with data" : null;
        List<Map<Column, Object>> rowMaps = IntStream.range(0, 3)
                .<Map<Column, Object>>mapToObj(i -> columnMapOf(Column.Donor_ID, "Donor"+i))
                .collect(toList());
        for (int i = 0; i < rowMaps.size(); ++i) {
            String rowProblem = (i==1 ? problem : null);
            Matchers.mayAddProblem(rowProblem, rowMaps.get(i)).when(reader).readRow(any(), any(), same(rows.get(DATA_ROW+i)));
        }

        SectionRegisterRequest request;
        if (error) {
            request = null;
        } else {
            request = new SectionRegisterRequest();
            doReturn(request).when(reader).createRequest(any(), any());
        }
        if (error) {
            assertValidationError(() -> reader.read(sheet), problem);
        } else {
            assertSame(request, reader.read(sheet));
        }
        verify(reader).indexColumns(any(), same(headingRow));
        verify(reader, times(3)).readRow(any(), any(), any());
        for (int i = 0; i < 3; ++i) {
            verify(reader).readRow(any(), same(columnIndex), same(rows.get(DATA_ROW+i)));
        }
        if (request==null) {
            verify(reader, never()).createRequest(any(), any());
        } else {
            verify(reader).createRequest(any(), eq(rowMaps));
        }
    }
/*
Work_number(Pattern.compile("(work|sgp)\\s*number", Pattern.CASE_INSENSITIVE)),
        Slide_type,
        External_slide_ID,
        Section_address,
        Fixative,
        Embedding_medium,
        Donor_ID,
        Life_stage,
        Species,
        HuMFre,
        Tissue_type,
        Spatial_location(Integer.class),
        Replicate_number,
        Section_external_ID,
        Section_number(Integer.class),
        Section_thickness(Integer.class),
        Section_position(Pattern.compile("(if.+)?position", Pattern.CASE_INSENSITIVE)),
        ;
 */
    @Test
    void testIndexColumns() {
        Row row = mockRow("work number", "slide type", "external slide id",
                "section address", "fixative", "embedding medium", "donor id", "life stage",
                "species", "humfre", "tissue type", "spatial location", "replicate number",
                "section external id", "section number", "section thickness", "if bla bla bla position", null, "");
        List<String> problems = new ArrayList<>();
        var result = reader.indexColumns(problems, row);
        assertThat(problems).isEmpty();
        Column[] expectedOrder = Column.values();
        for (int i = 0; i < expectedOrder.length; ++i) {
            assertEquals(i, result.get(expectedOrder[i]));
        }
    }

    @Test
    void testIndexColumnsProblems() {
        Row row = mockRow("work number", "slide type", "external slide id",
                "section address", "fixative", "embedding medium", "donor id", "life stage",
                "species", "humfre", "tissue type", "spatial location", "replicate number",
                "section external id", "section number", "section NUMBER", "bananas");
        List<String> problems = new ArrayList<>(3);
        reader.indexColumns(problems, row);
        assertThat(problems).containsExactlyInAnyOrder(
                "Repeated column: "+Column.Section_number,
                "Unexpected column heading: \"bananas\"",
                "Missing columns: "+List.of(Column.Section_thickness, Column.Section_position));
    }

    @Test
    void testReadRow() {
        Map<Column, Integer> columnIndex = new EnumMap<>(Column.class);
        columnIndex.put(Column.Donor_ID, 1);
        columnIndex.put(Column.External_slide_ID, 2);
        columnIndex.put(Column.Replicate_number, 3);
        Row row = mockRow("Bananas", "Custard", null, "77", "Crumble");
        doAnswer(invocation -> invocation.getArgument(1, Cell.class).getStringCellValue()).when(reader).cellValue(any(), any());
        final List<String> problems = new ArrayList<>();
        var map = reader.readRow(problems, columnIndex, row);
        assertThat(map).hasSize(2);
        assertEquals("Custard", map.get(Column.Donor_ID));
        assertEquals("77", map.get(Column.Replicate_number));
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testReadCellValue(boolean numeric) {
        final List<String> problems = new ArrayList<>(0);
        Cell cell = mock(Cell.class);
        Column column = numeric ? Column.Spatial_location : Column.Donor_ID;
        Object value = numeric ? Integer.valueOf(16) : "Bananas";
        doReturn(value).when(reader).cellValue(any(), any());
        assertEquals(value, reader.readCellValue(problems, column, cell));
        assertThat(problems).isEmpty();

        verify(reader).cellValue((Class<?>)(numeric ? Integer.class : String.class), cell);
    }

    @Test
    void testReadCellValue_error() {
        final List<String> problems = new ArrayList<>(1);
        Cell cell = mock(Cell.class);
        when(cell.getAddress()).thenReturn(new CellAddress(3, 0));

        doThrow(new IllegalArgumentException("Bad stuff")).when(reader).cellValue(any(), any());
        assertNull(reader.readCellValue(problems, Column.Donor_ID, cell));
        Matchers.assertProblem(problems, "At cell A4: Bad stuff");
    }

    @ParameterizedTest
    @MethodSource("cellValueMocks")
    void testCellValue(Class<?> type, Cell cell, Object expectedResult, String expectedError) {
        if (expectedError==null) {
            assertEquals(expectedResult, reader.cellValue(type, cell));
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> reader.cellValue(type, cell)))
                    .hasMessage(expectedError);
        }
    }

    @Test
    void testCellValueWithRealNumericCell() throws IOException {
        try (Workbook wb = new HSSFWorkbook()) {
            Cell cell = wb.createSheet().createRow(2).createCell(3, CellType.NUMERIC);
            cell.setCellValue(5.0);
            testCellValue(Integer.class, cell, 5, null);
        }
    }

    @Test
    void testCellValueWithRealStringCell() throws IOException {
        try (Workbook wb = new HSSFWorkbook()) {
            Cell cell = wb.createSheet().createRow(2).createCell(3, CellType.STRING);
            cell.setCellValue("bananas");
            testCellValue(String.class, cell, "bananas", null);
        }
    }
    static Cell mockCell(CellType cellType) {
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(cellType);
        return cell;
    }

    static Stream<Arguments> cellValueMocks() {
        Cell numCell = mockCell(CellType.NUMERIC);
        when(numCell.getNumericCellValue()).thenReturn(5.0);
        Cell blankCell = mockCell(CellType.BLANK);
        Cell numStringCell = mockCell(CellType.STRING);
        when(numStringCell.getStringCellValue()).thenReturn("6");
        Cell stringCell = mockCell(CellType.STRING);
        when(stringCell.getStringCellValue()).thenReturn("Bananas");
        Cell formulaNumCell = mockCell(CellType.FORMULA);
        when(formulaNumCell.getNumericCellValue()).thenReturn(7.0);
        Cell formulaStringCell = mockCell(CellType.FORMULA);
        when(formulaStringCell.getStringCellValue()).thenReturn("Custard");
        Cell nonIntegerCell = mockCell(CellType.NUMERIC);
        when(nonIntegerCell.getNumericCellValue()).thenReturn(3.5);
        Cell emptyStringCell = mockCell(CellType.STRING);
        when(emptyStringCell.getStringCellValue()).thenReturn("");
        return Arrays.stream(new Object[][] {
                {Integer.class, null, null, null},
                {String.class, null, null, null},
                {String.class, blankCell, null, null},
                {Integer.class, numCell, 5, null},
                {Integer.class, numStringCell, 6, null},
                {Integer.class, formulaNumCell, 7, null},
                {String.class, formulaStringCell, "Custard", null},
                {Integer.class, nonIntegerCell, null, "Expected integer but got 3.5"},
                {String.class, nonIntegerCell, null, "Expected string but got number."},
                {String.class, numCell, "5", null},
                {Integer.class, stringCell, null, "Expected integer but got \"Bananas\""},
                {String.class, emptyStringCell, null, null},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("getUniqueStringArgs")
    void testGetUniqueString(List<String> strings, String expected, boolean expectMultiple) {
        Runnable runnable = mock(Runnable.class);
        assertEquals(expected, reader.getUniqueString(strings.stream(), runnable));
        if (expectMultiple) {
            verify(runnable).run();
        } else {
            verifyNoInteractions(runnable);
        }
    }

    static Stream<Arguments> getUniqueStringArgs() {
        return Arrays.stream(new Object[][] {
                {Arrays.asList("Alpha", null, "alpha", "ALPHA"), "Alpha", false},
                {List.of("Alpha"), "Alpha", false},
                {List.of(), null, false},
                {Arrays.asList(null, null), null, false},
                {List.of("Alpha", "BETA"), "Alpha", true},
        }).map(Arguments::of);
    }

    @Test
    void testCreateRequest() {
        List<Map<Column, Object>> rows = List.of(
                rowMap("SGP1", "X1", 1),
                rowMap(null, null, 2),
                rowMap("sgp1", "X2", 3)
        );
        List<SectionRegisterLabware> srls = IntStream.range(1, 3)
                .mapToObj(i -> new SectionRegisterLabware("X"+i, null, null))
                .collect(toList());
        doReturn(srls.get(0)).when(reader).createRequestLabware(any(), eq(rows.subList(0,2)));
        doReturn(srls.get(1)).when(reader).createRequestLabware(any(), eq(rows.subList(2,3)));

        final List<String> problems = new ArrayList<>();
        SectionRegisterRequest request = reader.createRequest(problems, rows);
        assertEquals("SGP1", request.getWorkNumber());
        assertEquals(srls, request.getLabware());
    }

    @Test
    void testCreateRequest_problems() {
        List<Map<Column, Object>> rows = List.of(
                rowMap("SGP1", null, 1),
                rowMap(null, "X1", 2),
                rowMap("sgp2", "X2", 3)
        );
        List<SectionRegisterLabware> srls = IntStream.range(1, 3)
                .mapToObj(i -> new SectionRegisterLabware("X"+i, null, null))
                .collect(toList());

        doReturn(srls.get(0)).when(reader).createRequestLabware(any(), eq(rows.subList(1,2)));
        Matchers.mayAddProblem("Bad stuff.", srls.get(1)).when(reader).createRequestLabware(any(), eq(rows.subList(2,3)));

        assertValidationError(() -> reader.createRequest(new ArrayList<>(), rows),
                "Multiple work numbers specified.",
                "Missing external barcode.",
                "Bad stuff.");
    }

    @Test
    void testCreateRequestLabware() {
        List<Map<Column, Object>> rows = List.of(
                rowMap("X1", "Bowl"),
                rowMap("X1", null),
                rowMap("X1", "bowl")
        );
        List<SectionRegisterContent> srcs = List.of(
                new SectionRegisterContent("d1", LifeStage.adult, "human"),
                new SectionRegisterContent("d2", LifeStage.adult, "human"),
                new SectionRegisterContent("d3", LifeStage.adult, "human")
        );
        var srcIter = srcs.iterator();
        for (var row : rows) {
            doReturn(srcIter.next()).when(reader).createRequestContent(any(), same(row));
        }

        final List<String> problems = new ArrayList<>(0);
        SectionRegisterLabware srl = reader.createRequestLabware(problems, rows);
        assertThat(problems).isEmpty();
        assertEquals(srl, new SectionRegisterLabware("X1", "Bowl", srcs));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCreateRequestLabware_problems(boolean anyLwType) {
        List<Map<Column, Object>> rows = List.of(
                rowMap("X1", anyLwType ? "Bowl" : null),
                rowMap("X1", null),
                rowMap("X1", anyLwType ? "pipe" : null)
        );
        List<SectionRegisterContent> srcs = List.of(
                new SectionRegisterContent("d1", LifeStage.adult, "human"),
                new SectionRegisterContent("d2", LifeStage.adult, "human"),
                new SectionRegisterContent("d3", LifeStage.adult, "human")
        );
        var srcIter = srcs.iterator();
        for (var row : rows) {
            Matchers.mayAddProblem(row==rows.get(1) ? "Bad stuff." : null, srcIter.next())
                    .when(reader).createRequestContent(any(), same(row));
        }
        final List<String> problems = new ArrayList<>(2);
        SectionRegisterLabware srl = reader.createRequestLabware(problems, rows);
        assertThat(problems).containsExactlyInAnyOrder("Bad stuff.",
                anyLwType ? "Multiple different labware types specified for external ID X1."
                        : "No labware type specified for external ID X1.");
        assertEquals(srl, new SectionRegisterLabware("X1", anyLwType ? "Bowl" : null, srcs));
    }

    @Test
    void testCreateRequestContent_basic() {
        List<String> problems = new ArrayList<>(0);
        Map<Column, Object> row = new EnumMap<>(Column.class);
        row.put(Column.Donor_ID, "Donor1");
        row.put(Column.Section_thickness, 41);
        SectionRegisterContent src = reader.createRequestContent(problems, row);
        assertEquals("Donor1", src.getDonorIdentifier());
        assertEquals(41, src.getSectionThickness());
        assertNull(src.getAddress());
        assertNull(src.getLifeStage());
        assertNull(src.getSpatialLocation());
        assertThat(problems).isEmpty();
    }


    @Test
    void testCreateRequestContent_full() {
        List<String> problems = new ArrayList<>(0);
        Map<Column, Object> row = new EnumMap<>(Column.class);
        row.put(Column.Donor_ID, "Donor1");
        row.put(Column.Section_thickness, 41);
        row.put(Column.Section_address, "A4");
        row.put(Column.Section_external_ID, "X1");
        row.put(Column.HuMFre, "12/234");
        row.put(Column.Life_stage, "ADULT");
        row.put(Column.Replicate_number, "14");
        row.put(Column.Species, "Human");
        row.put(Column.Tissue_type, "Arm");
        row.put(Column.Spatial_location, 2);
        row.put(Column.Embedding_medium, "brass");
        row.put(Column.Fixative, "Floop");
        row.put(Column.Section_number, 400);
        row.put(Column.Section_position, "Middle");
        SectionRegisterContent src = reader.createRequestContent(problems, row);
        assertEquals("Donor1", src.getDonorIdentifier());
        assertEquals(41, src.getSectionThickness());
        assertEquals(new Address(1,4), src.getAddress());
        assertEquals("X1", src.getExternalIdentifier());
        assertEquals("12/234", src.getHmdmc());
        assertEquals(LifeStage.adult, src.getLifeStage());
        assertEquals("14", src.getReplicateNumber());
        assertEquals("Human", src.getSpecies());
        assertEquals("Arm", src.getTissueType());
        assertEquals(2, src.getSpatialLocation());
        assertEquals("brass", src.getMedium());
        assertEquals("Floop", src.getFixative());
        assertEquals(400, src.getSectionNumber());
        assertEquals("Middle", src.getRegion());
        assertThat(problems).isEmpty();
    }

    @Test
    void testCreateRequestContent_problems() {
        List<String> problems = new ArrayList<>(2);
        Map<Column, Object> row = new EnumMap<>(Column.class);
        row.put(Column.Donor_ID, "Donor1");
        row.put(Column.Section_address, "xyz");
        row.put(Column.Life_stage, "ascended");
        SectionRegisterContent src = reader.createRequestContent(problems, row);
        assertThat(problems).containsExactlyInAnyOrder(
                "Unknown life stage: \"ascended\"",
                "Invalid address string: \"xyz\""

        );
        assertEquals("Donor1", src.getDonorIdentifier());
        assertNull(src.getLifeStage());
        assertNull(src.getAddress());
    }

    static <V> Map<Column, V> columnMapOf(Column k1, V v1) {
        EnumMap<Column,V> map = new EnumMap<>(Column.class);
        map.put(k1, v1);
        return map;
    }

    static Map<Column, Object> rowMap(Object workNumber, Object externalName, Integer sectionNumber) {
        Map<Column, Object> map = new EnumMap<>(Column.class);
        map.put(Column.Work_number, workNumber);
        map.put(Column.External_slide_ID, externalName);
        map.put(Column.Section_number, sectionNumber);
        return map;
    }

    static Map<Column, Object> rowMap(String externalName, String lwType) {
        Map<Column, Object> map = new EnumMap<>(Column.class);
        map.put(Column.External_slide_ID, externalName);
        map.put(Column.Slide_type, lwType);
        return map;
    }

    static void assertValidationError(Executable exec, String... expectedProblems) {
        Matchers.assertValidationException(exec, "The file contents are invalid.", expectedProblems);
    }
}