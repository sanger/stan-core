package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.register.filereader.BlockRegisterFileReader.Column;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

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
        assertValidationException(() -> reader.read(sheet), List.of(problem));
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
        assertValidationException(() -> reader.read(sheet), List.of("No registrations requested."));
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

        BlockRegisterRequest request;
        if (error) {
            request = null;
        } else {
            request = new BlockRegisterRequest();
            doReturn(request).when(reader).createRequest(any(), any());
        }
        if (error) {
            assertValidationException(() -> reader.read(sheet), List.of(problem));
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
                "species", "cellular classification", "biological risk assessment number", "humfre", "slot address of sample",
                "tissue type", "external id", "spatial location", "replicate number",
                "last known banana section custard", "labware type", "fixative", "medium", "external barcode",
                "information", "comment");
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
                "Missing columns: [Slot address, Tissue type, External identifier, External barcode]");
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

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCreateRequest(boolean ok) {
        List<Map<Column, Object>> rows;
        if (ok) {
            rows = List.of(rowMap("SGP1, SGP2", "EXT1"), rowMap("sgp2, sgp1", "EXT2"));
        } else {
            rows = List.of(rowMap("SGP1", "EXT1"), rowMap("SGP2", "EXT2"));
        }
        List<BlockRegisterLabware> brlw = List.of(new BlockRegisterLabware());
        String problem = ok ? null : "Bad request.";
        mayAddProblem(problem, brlw).when(reader).createLabwareRequests(any(), any());
        if (ok) {
            List<String> problems = new ArrayList<>();
            BlockRegisterRequest request = reader.createRequest(problems, rows);
            assertSame(brlw, request.getLabware());
            assertThat(request.getWorkNumbers()).containsExactlyInAnyOrder("SGP1", "SGP2");
        } else {
            List<String> problems = new ArrayList<>(2);
            List<String> expectedProblems = List.of(problem, "All rows must list the same work numbers.");
            assertValidationException(() -> reader.createRequest(problems, rows), expectedProblems);
        }
        verify(reader).createLabwareRequests(any(), same(rows));
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";",
            "sgp1;SGP1",
            "sgp1, SGP2;SGP1,SGP2"
    }, delimiter=';')
    void testWorkNumberSet(String input, String expected) {
        Set<String> workNumbers = BlockRegisterFileReaderImp.workNumberSet(input);
        if (expected==null) {
            assertThat(workNumbers).isNullOrEmpty();
        } else {
            assertThat(workNumbers).containsExactlyInAnyOrder(expected.split(","));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testCreateLabwareRequests(boolean xbMissing) {
        List<Map<Column, Object>> rows = List.of(rowWithExternalBarcode("EXT1", "A"),
                rowWithExternalBarcode(xbMissing ? null : "ext1", "B"),
                rowWithExternalBarcode("EXT2", "C"));

        List<BlockRegisterLabware> brls = List.of(new BlockRegisterLabware(), new BlockRegisterLabware());
        brls.getFirst().setExternalBarcode("EXT1");
        brls.getLast().setExternalBarcode("EXT2");
        doReturnFrom(brls.iterator()).when(reader).toLabwareRequest(any(), any());
        List<String> problems = new ArrayList<>(xbMissing ? 1 : 0);
        assertThat(reader.createLabwareRequests(problems, rows)).containsExactlyElementsOf(brls);
        assertProblem(problems, xbMissing ? "Cannot process blocks without an external barcode." : null);

        verify(reader, times(2)).toLabwareRequest(any(), any());
        verify(reader).toLabwareRequest(same(problems), eq(rows.subList(0, xbMissing ? 1 : 2)));
        verify(reader).toLabwareRequest(same(problems), eq(rows.subList(2, 3)));
    }

    @ParameterizedTest
    @CsvSource({
            ",false,",
            "EXT1 ext1,false,EXT1",
            "ext1 ext2,true,ext1",
    })
    void testUniqueRowValue(String values, boolean error, String expectedValue) {
        String[] splitValues = (values==null ? new String[0] : values.split("\\s+"));
        final Column column = Column.External_barcode;
        List<Map<Column, Object>> rows = Arrays.stream(splitValues).map(v -> {
            Map<Column, Object> map = new EnumMap<>(Column.class);
            map.put(column, v);
            return map;
        }).toList();
        List<String> problems = new ArrayList<>(error ? 1 : 0);
        Supplier<String> errorSupplier = () -> "Bad thing.";
        assertEquals(expectedValue, reader.uniqueRowValue(problems, rows, column, errorSupplier));
        assertProblem(problems, error ? "Bad thing." : null);
    }

    @Test
    void testToLabwareRequest() {
        List<Map<Column, Object>> rows = List.of(rowWithExternalBarcode("BC", "A"), rowWithExternalBarcode("BC", "B"));
        rows.forEach(row -> {
            row.put(Column.Labware_type, "lt");
            row.put(Column.Fixative, "fix");
            row.put(Column.Embedding_medium, "med");
        });
        BlockRegisterSample brs1 = new BlockRegisterSample();
        BlockRegisterSample brs2 = new BlockRegisterSample();
        brs1.setExternalIdentifier("xn1");
        brs2.setExternalIdentifier("xn2");
        doReturn(brs1, brs2).when(reader).toSample(any(), any());
        List<String> problems = new ArrayList<>(0);
        BlockRegisterLabware brl = reader.toLabwareRequest(problems, rows);
        verify(reader, times(3)).uniqueRowValue(any(), any(), any(), any());
        rows.forEach(row -> verify(reader).toSample(same(problems), same(row)));
        assertThat(problems).isEmpty();
        assertEquals("lt", brl.getLabwareType());
        assertEquals("fix", brl.getFixative());
        assertEquals("med", brl.getMedium());
        assertEquals("BC", brl.getExternalBarcode());
        assertThat(brl.getSamples()).containsExactly(brs1, brs2);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testToSample(boolean complete) {
        Map<Column, Object> row = new EnumMap<>(Column.class);
        row.put(Column.Bio_risk, "risk1");
        row.put(Column.Cell_class, "cclass1");
        row.put(Column.Donor_identifier, "donor1");
        row.put(Column.HuMFre, "hum1");
        row.put(Column.Life_stage, "fetal");
        row.put(Column.Replicate_number, "17");
        row.put(Column.Species, "mermaid");
        row.put(Column.Tissue_type, "leg");
        LocalDate date;
        if (complete) {
            row.put(Column.Spatial_location, 3);
            row.put(Column.Last_known_section, 5);
            date = LocalDate.of(2023, 1, 2);
            row.put(Column.Collection_date, date);
            row.put(Column.Slot_address, "A1, A2");
        } else {
            date = null;
        }
        row.put(Column.External_identifier, "ext1");
        List<String> problems = new ArrayList<>(complete ? 0 : 2);
        BlockRegisterSample brs = reader.toSample(problems, row);
        if (complete) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactlyInAnyOrder("Spatial location not specified.", "Last known section not specified.");
        }
        assertEquals("risk1", brs.getBioRiskCode());
        assertEquals("cclass1", brs.getCellClass());
        assertEquals("donor1", brs.getDonorIdentifier());
        assertEquals("hum1", brs.getHmdmc());
        assertEquals(LifeStage.fetal, brs.getLifeStage());
        assertEquals("17", brs.getReplicateNumber());
        assertEquals("mermaid", brs.getSpecies());
        assertEquals("leg", brs.getTissueType());
        if (complete) {
            assertEquals(3, brs.getSpatialLocation());
            assertEquals(5, brs.getHighestSection());
            assertEquals(date, brs.getSampleCollectionDate());
            assertThat(brs.getAddresses()).containsExactlyInAnyOrder(new Address(1,1), new Address(1,2));
        } else {
            assertNull(brs.getSpatialLocation());
            assertNull(brs.getHighestSection());
            assertNull(brs.getSampleCollectionDate());
            assertThat(brs.getAddresses()).isEmpty();
        }
        assertEquals("ext1", brs.getExternalIdentifier());
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

    static Map<Column, Object> rowWithExternalBarcode(String externalBarcode, String externalName) {
        Map<Column, Object> map = new EnumMap<>(Column.class);
        map.put(Column.External_identifier, externalName);
        map.put(Column.External_barcode, externalBarcode);
        return map;
    }

    @ParameterizedTest
    @MethodSource("parseAddressesArgs")
    void testParseAddresses(String input, List<Address> expectedAddresses, String expectedError) {
        List<String> problems = new ArrayList<>(expectedError==null ? 0 : 1);
        assertEquals(expectedAddresses, BlockRegisterFileReaderImp.parseAddresses(problems, input));
        assertProblem(problems, expectedError);
    }

    static Stream<Arguments> parseAddressesArgs() {
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        Address AA50 = new Address(27,50);
        return Arrays.stream(new Object[][] {
                {null, List.of(), null},
                {"A1", List.of(A1), null},
                {"a1, A2", List.of(A1, A2), null},
                {"A1 A2", List.of(A1, A2), null},
                {"A2 27,50 a1", List.of(A2, AA50, A1), null},
                {"A,X,W?", List.of(), "Couldn't parse slot addresses: \"A,X,W?\""}
        }).map(Arguments::of);
    }
}