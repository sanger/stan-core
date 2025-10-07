package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.stan.model.Species;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.register.filereader.BlockRegisterFileReader.Column;
import uk.ac.sanger.sccp.utils.Zip;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link BlockRegisterFileReaderImp}
 */
class TestBlockRegisterFileReader extends BaseTestFileReader {
    private static final int DATA_ROW = 3;

    private BlockRegisterFileReaderImp reader;

    @BeforeEach
    void testSetUp() {
        reader = spy(new BlockRegisterFileReaderImp());
    }

    // Check that the pattern for each column accepts that column's name
    @Test
    void testColumns() {
        final Column[] columns = Column.values();
        for (Column column : columns) {
            if (!column.name().startsWith("_")) {
                assertSame(column, IColumn.forHeading(columns, column.toString()));
            }
        }
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
        Map<Column, Integer> map = columnMapOf(Column.Donor_identifier, 3);
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
        Map<Column, Integer> columnIndex = columnMapOf(Column.Donor_identifier, 3);
        doReturn(columnIndex).when(reader).indexColumns(any(), any());
        final String problem = error ? "Problem with data" : null;
        List<Map<Column, Object>> rowMaps = IntStream.range(0, 3)
                .<Map<Column, Object>>mapToObj(i -> columnMapOf(Column.Donor_identifier, "Donor"+i))
                .collect(toList());
        for (int i = 0; i < rowMaps.size(); ++i) {
            String rowProblem = (i==1 ? problem : null);
            Matchers.mayAddProblem(rowProblem, rowMaps.get(i)).when(reader).readRow(any(), any(), same(rows.get(DATA_ROW+i)));
        }

        RegisterRequest request;
        if (error) {
            request = null;
        } else {
            request = new RegisterRequest();
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

    @Test
    void testIndexColumns() {
        Row row = mockRow("All information is needed",
                "SGP number", "donor identifier", "life stage", "if then date of collection of stuff",
                "species", "cellular classification", "biological risk assessment number", "humfre", "tissue type", "external id", "spatial location", "replicate number",
                "last known banana section custard", "labware type", "fixative", "medium", "information", "comment");
        List<String> problems = new ArrayList<>();
        var result = reader.indexColumns(problems, row);
        assertThat(problems).isEmpty();
        Column[] expectedOrder = Column.values();
        for (int i = 0; i < expectedOrder.length; ++i) {
            if (expectedOrder[i].getDataType()!=Void.class) {
                assertEquals(i, result.get(expectedOrder[i]));
            }
        }
    }

    @Test
    void testIndexColumnsProblems() {
        Row row = mockRow(
                "SGP number", "work number", "donor identifier", "life stage",
                "if then date of collection of stuff", "bananas",
                "species", "cell class", "bio risk", "humfre", "spatial location", "replicate number",
                "last known banana section custard", "labware type", "fixative", "medium", "information");
        List<String> problems = new ArrayList<>(3);
        reader.indexColumns(problems, row);
        assertThat(problems).containsExactlyInAnyOrder(
                "Repeated column: Work number",
                "Unexpected column heading: \"bananas\"",
                "Missing columns: [Tissue type, External identifier]");
    }

    @Test
    void testReadRow() {
        Map<Column, Integer> columnIndex = new EnumMap<>(Column.class);
        columnIndex.put(Column.Donor_identifier, 1);
        columnIndex.put(Column.External_identifier, 2);
        columnIndex.put(Column.Replicate_number, 3);
        Row row = mockRow("Bananas", "Custard", null, "77", "Crumble");
        doAnswer(invocation -> invocation.getArgument(1, Cell.class).getStringCellValue()).when(reader).cellValue(any(), any());
        final List<String> problems = new ArrayList<>();
        var map = reader.readRow(problems, columnIndex, row);
        assertThat(map).hasSize(2);
        assertEquals("Custard", map.get(Column.Donor_identifier));
        assertEquals("77", map.get(Column.Replicate_number));
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testReadCellValue(boolean numeric) {
        final List<String> problems = new ArrayList<>(0);
        Cell cell = mock(Cell.class);
        Column column = numeric ? Column.Spatial_location : Column.Donor_identifier;
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
        assertNull(reader.readCellValue(problems, Column.Donor_identifier, cell));
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

    @ParameterizedTest
    @MethodSource("cellDateArgs")
    public void testCellDateWithMocks(Cell cell, Object expected) {
        if (expected==null) {
            assertNull(reader.cellDate(cell));
        } else if (expected instanceof Class) {
            //noinspection unchecked
            assertThrows((Class<? extends Exception>) expected, () -> reader.cellDate(cell));
        } else {
            assertEquals(expected, reader.cellDate(cell));
        }
    }

    static Stream<Arguments> cellDateArgs() {
        LocalDate date = LocalDate.of(2023,1,2);
        Cell emptyCell = mockCell(CellType.BLANK);
        Cell dateCell = mockCell(CellType.NUMERIC);
        when(dateCell.getLocalDateTimeCellValue()).thenReturn(date.atStartOfDay());
        Cell stringCell = mockCell(CellType.STRING);
        when(stringCell.getStringCellValue()).thenReturn("02/01/2023");
        Cell emptyStringCell = mockCell(CellType.STRING);
        when(emptyStringCell.getStringCellValue()).thenReturn("");
        Cell nonDateCell = mockCell(CellType.BOOLEAN);
        when(nonDateCell.getLocalDateTimeCellValue()).thenThrow(IllegalStateException.class);
        Cell nonDateStringCell = mockCell(CellType.STRING);
        when(nonDateStringCell.getStringCellValue()).thenReturn("bananas");
        return Arrays.stream(new Object[][] {
                { emptyCell, null },
                { dateCell, date },
                { stringCell, date },
                { emptyStringCell, null },
                { nonDateCell, IllegalStateException.class },
                { nonDateStringCell, RuntimeException.class },
        }).map(Arguments::of);
    }

    @Test
    public void testCellDateWithRealCell() throws IOException {
        try (Workbook wb = new HSSFWorkbook()) {
            Row row = wb.createSheet().createRow(2);
            LocalDate date1 = LocalDate.of(2023,2,3);
            LocalDate date2 = LocalDate.of(2023,3,4);
            Cell cell1 = row.createCell(1, CellType.NUMERIC);
            cell1.setCellValue(date1);
            Cell cell2 = row.createCell(2, CellType.STRING);
            cell2.setCellValue("04/03/2023");
            assertEquals(date1, reader.cellDate(cell1));
            assertEquals(date2, reader.cellDate(cell2));
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

    @Test
    void testCreateRequest() {
        List<Map<Column, Object>> rows = List.of(
                rowMap("SGP1, SGP2 sgp3,sgp2", "X1"),
                rowMap("sgp1 sgp3 sgp2", "X2"),
                rowMap(null, null)
        );
        List<BlockRegisterRequest> brs = IntStream.rangeClosed(1, rows.size())
                .mapToObj(i -> makeBlockRegisterRequest("X"+i))
                .collect(toList());
        Zip.of(rows.stream(), brs.stream()).forEach((row, br) -> doReturn(br).when(reader).createBlockRequest(any(), same(row)));

        final List<String> problems = new ArrayList<>();
        RegisterRequest request = reader.createRequest(problems, rows);
        assertThat(request.getWorkNumbers()).containsExactlyInAnyOrder("SGP1", "SGP2", "SGP3");
        assertEquals(brs, request.getBlocks());
    }

    @Test
    void testCreateRequest_problems() {
        List<Map<Column, Object>> rows = List.of(
                rowMap("SGP1", "X1"),
                rowMap("sgp2", "X2")
        );
        List<BlockRegisterRequest> srls = IntStream.range(1, 3)
                .mapToObj(i -> makeBlockRegisterRequest("X"+i))
                .toList();

        doReturn(srls.get(0)).when(reader).createBlockRequest(any(), same(rows.get(0)));
        Matchers.mayAddProblem("Bad stuff.", srls.get(1)).when(reader).createBlockRequest(any(), same(rows.get(1)));

        assertValidationError(() -> reader.createRequest(new ArrayList<>(), rows),
                "All rows must list the same work numbers.",
                "Bad stuff.");
    }

    @Test
    void testCreateBlockRegisterRequest_basic() {
        List<String> problems = new ArrayList<>(0);
        Map<Column, Object> row = new EnumMap<>(Column.class);
        row.put(Column.Donor_identifier, "Donor1");
        row.put(Column.Replicate_number, "12A");
        row.put(Column.Spatial_location, 14);
        row.put(Column.Last_known_section, 18);
        BlockRegisterRequest src = reader.createBlockRequest(problems, row);
        assertEquals("Donor1", src.getDonorIdentifier());
        assertEquals("12A", src.getReplicateNumber());
        assertEquals(14, src.getSpatialLocation());
        assertNull(src.getLifeStage());
        assertThat(problems).isEmpty();
    }


    @Test
    void testCreateBlockRegisterRequest_full() {
        List<String> problems = new ArrayList<>(0);
        Map<Column, Object> row = new EnumMap<>(Column.class);
        final LocalDate date = LocalDate.of(2022, 5, 5);
        row.put(Column.Donor_identifier, "Donor1");
        row.put(Column.External_identifier, "X11");
        row.put(Column.HuMFre, "12/234");
        row.put(Column.Life_stage, "ADULT");
        row.put(Column.Replicate_number, "14");
        row.put(Column.Species, Species.HUMAN_NAME);
        row.put(Column.Tissue_type, "Arm");
        row.put(Column.Spatial_location, 2);
        row.put(Column.Embedding_medium, "brass");
        row.put(Column.Fixative, "Floop");
        row.put(Column.Collection_date, date);
        row.put(Column.Last_known_section, 17);
        row.put(Column.Labware_type, "Eggcup");
        BlockRegisterRequest br = reader.createBlockRequest(problems, row);
        assertEquals("Donor1", br.getDonorIdentifier());
        assertEquals("X11", br.getExternalIdentifier());
        assertEquals("12/234", br.getHmdmc());
        assertEquals(LifeStage.adult, br.getLifeStage());
        assertEquals("14", br.getReplicateNumber());
        assertEquals(Species.HUMAN_NAME, br.getSpecies());
        assertEquals("Arm", br.getTissueType());
        assertEquals(2, br.getSpatialLocation());
        assertEquals("brass", br.getMedium());
        assertEquals("Floop", br.getFixative());
        assertEquals(date, br.getSampleCollectionDate());
        assertEquals(17, br.getHighestSection());
        assertEquals("Eggcup", br.getLabwareType());
        assertThat(problems).isEmpty();
    }

    @Test
    void testCreateRequestContent_problems() {
        List<String> problems = new ArrayList<>(2);
        Map<Column, Object> row = new EnumMap<>(Column.class);
        row.put(Column.Donor_identifier, "Donor1");
        row.put(Column.Life_stage, "ascended");
        BlockRegisterRequest src = reader.createBlockRequest(problems, row);
        assertThat(problems).containsExactlyInAnyOrder(
                "Unknown life stage: \"ascended\"",
                "Last known section not specified.",
                "Spatial location not specified."
        );
        assertEquals("Donor1", src.getDonorIdentifier());
        assertNull(src.getLifeStage());
    }

    static <V> Map<Column, V> columnMapOf(Column k1, V v1) {
        EnumMap<Column,V> map = new EnumMap<>(Column.class);
        map.put(k1, v1);
        return map;
    }

    static Map<Column, Object> rowMap(Object workNumber, Object externalName) {
        Map<Column, Object> map = new EnumMap<>(Column.class);
        map.put(Column.Work_number, workNumber);
        map.put(Column.External_identifier, externalName);
        return map;
    }

    static BlockRegisterRequest makeBlockRegisterRequest(String externalId) {
        BlockRegisterRequest br = new BlockRegisterRequest();
        br.setExternalIdentifier(externalId);
        return br;
    }

    static void assertValidationError(Executable exec, String... expectedProblems) {
        Matchers.assertValidationException(exec, "The file contents are invalid.", expectedProblems);
    }
}