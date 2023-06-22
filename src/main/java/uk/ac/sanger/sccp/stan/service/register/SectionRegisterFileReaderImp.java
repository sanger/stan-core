package uk.ac.sanger.sccp.stan.service.register;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterContent;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterLabware;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class SectionRegisterFileReaderImp implements SectionRegisterFileReader {
    final int headingRowIndex;
    final int dataRowIndex;

    public SectionRegisterFileReaderImp() {
        this(1, 3);
    }

    public SectionRegisterFileReaderImp(int headingRowIndex, int dataRowIndex) {
        this.headingRowIndex = headingRowIndex;
        this.dataRowIndex = dataRowIndex;
    }

    @Override
    public SectionRegisterRequest read(Sheet sheet) {
        final Collection<String> problems = new LinkedHashSet<>();
        Map<Column, Integer> columnIndex = indexColumns(problems, sheet.getRow(headingRowIndex));
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        List<Map<Column, Object>> rows = stream(sheet)
                .filter(row -> row.getRowNum() >= dataRowIndex)
                .map(row -> readRow(problems, columnIndex, row))
                .filter(map -> !map.isEmpty())
                .collect(toList());
        if (problems.isEmpty() && rows.isEmpty()) {
            problems.add("No registrations requested.");
        }
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        return createRequest(problems, rows);
    }

    /**
     * Finds the indexes of {@link Column} headings in the given row.
     * Empty cells are skipped. Each column should be matched once, and no unrecognised headings should be found.
     * @param problems receptacle for problems found
     * @param row the headings row
     * @return a map of column to index
     */
    public Map<Column, Integer> indexColumns(Collection<String> problems, Row row) {
        EnumMap<Column, Integer> map = new EnumMap<>(Column.class);
        for (Cell cell : row) {
            String heading = (cell==null ? null : cell.getStringCellValue());
            if (heading==null) {
                continue;
            }
            heading = heading.trim();
            if (heading.isEmpty()) {
                continue;
            }
            Column column = Column.forHeading(heading);
            if (column==null) {
                problems.add("Unexpected column heading: "+repr(heading));
            } else if (map.get(column)!=null) {
                problems.add("Repeated column: "+column);
            } else {
                map.put(column, cell.getColumnIndex());
            }
        }
        List<String> missingColumns = Arrays.stream(Column.values())
                .filter(c -> map.get(c)==null)
                .map(Object::toString)
                .collect(toList());
        if (!missingColumns.isEmpty()) {
            problems.add("Missing columns: "+missingColumns);
        }
        return map;
    }

    /**
     * Reads values from the given row as linked to the given map of columns and integers.
     * @param problems receptacle for problems found
     * @param columnIndex map of {@link Column} to index in the row
     * @param row the row to read
     * @return a map of {@link Column} to value from the row
     */
    public Map<Column, Object> readRow(final Collection<String> problems, Map<Column, Integer> columnIndex, Row row) {
        return columnIndex.entrySet().stream()
                .map(e -> simpleEntry(e.getKey(), readCellValue(problems, e.getKey(), row.getCell(e.getValue()))))
                .filter(e -> e.getValue()!=null)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Reads the value for a particular column from the given cell.
     * Problems found will indicate the address of the cell where the problem occurred.
     * @param problems receptacle for problems found
     * @param column the column we want a value for
     * @param cell the cell we want to read a value from
     * @return a value from the given cell
     */
    public Object readCellValue(Collection<String> problems, Column column, Cell cell) {
        try {
            return cellValue(column.getDataType(), cell);
        } catch (RuntimeException e) {
            problems.add("At cell "+cell.getAddress()+": "+e.getMessage());
        }
        return null;
    }

    /**
     * Gets a cell value, trying to make it appropriate for the indicated type.
     * Some conversions are possible between cell types and data types.
     * For instance, a cell containing a number may be converted to a string;
     * a cell containing a numeric (floating-point) value may be interpreted as an integer.
     * @param type a type ({@link String} or {@link Integer})
     * @param cell the cell to read
     * @return the value read
     * @param <T> the type of value read
     * @exception IllegalArgumentException if the specified type cannot be read from the given cell
     * @exception IllegalStateException may be thrown from the POI cells if they are of the wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T cellValue(Class<T> type, Cell cell) {
        CellType cellType = (cell==null ? null : cell.getCellType());
        if (cellType==null || cellType==CellType.BLANK) {
            return null;
        }
        if (type==Integer.class) {
            if (cellType==CellType.NUMERIC || cellType==CellType.FORMULA) {
                double value = cell.getNumericCellValue();
                if (value%1!=0.0) {
                    throw new IllegalArgumentException("Expected integer but got "+value);
                }
                return (T) (Integer) (int) value;
            }
            String value = cell.getStringCellValue();
            try {
                return (T) Integer.valueOf(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected integer but got "+repr(value));
            }
        }
        if (cellType==CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value%1==0.0) {
                return (T) String.valueOf((int) value);
            }
            throw new IllegalArgumentException("Expected string but got number.");
        }
        String string = cell.getStringCellValue();
        if (string!=null) {
            string = string.trim();
            if (string.isEmpty()) {
                return null;
            }
        }
        return (T) string;
    }

    /**
     * Gets the (case-insensitively) unique string from the given stream. Null values are ignored.
     * If multiple different strings are found, the <tt>multipleValues</tt> function is called,
     * and the first string found is returned
     * @param values the stream of strings
     * @param multipleValues callback to run if multiple different strings are found
     * @return the string found, or null if no string is found
     */
    public String getUniqueString(Stream<String> values, Runnable multipleValues) {
        Iterable<String> iter = values::iterator;
        String found = null;
        for (String value : iter) {
            if (found==null) {
                found = value;
            } else if (value != null && !found.equalsIgnoreCase(value)) {
                multipleValues.run();
                break;
            }
        }
        return found;
    }

    /**
     * Creates a registration request from the given list of column/value data.
     * @param problems receptacle for problems
     * @param rows a list of row data, where each row's data is a map from a conceptual column to the value
     * read for that column
     * @return a request created from the given data
     */
    public SectionRegisterRequest createRequest(Collection<String> problems, List<Map<Column, Object>> rows) {
        List<List<Map<Column, Object>>> groups = new ArrayList<>();
        List<Map<Column, Object>> currentGroup = null;
        String groupx = null;
        String workNumber = getUniqueString(rows.stream().map(row -> (String) row.get(Column.Work_number)),
                () -> problems.add("Multiple work numbers specified."));

        for (var row : rows) {
            String rowx = (String) row.get(Column.External_slide_ID);
            if (groupx==null && rowx==null) {
                problems.add("Missing external barcode.");
            } else if (rowx!=null && !rowx.equalsIgnoreCase(groupx)) {
                currentGroup = new ArrayList<>();
                groups.add(currentGroup);
                currentGroup.add(row);
                groupx = rowx;
            } else {
                currentGroup.add(row);
            }
        }
        List<SectionRegisterLabware> srls = groups.stream()
                .map(group -> createRequestLabware(problems, group))
                .collect(toList());
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        return new SectionRegisterRequest(srls, workNumber);
    }

    /**
     * Creates the part of a request linked to a particular item of labware (slide)
     * @param problems receptacle for problems
     * @param group the rows all linked to the same labware
     * @return the part of the request linked to the particular item of labware
     */
    public SectionRegisterLabware createRequestLabware(Collection<String> problems, List<Map<Column, Object>> group) {
        String externalName = (String) group.get(0).get(Column.External_slide_ID);
        String lwType = getUniqueString(group.stream().map(row -> (String) row.get(Column.Slide_type)),
                () -> problems.add("Multiple different labware types specified for external ID "+externalName+"."));
        if (lwType==null) {
            problems.add("No labware type specified for external ID "+externalName+".");
        }

        List<SectionRegisterContent> srcs = group.stream()
                .map(row -> createRequestContent(problems, row))
                .filter(Objects::nonNull)
                .collect(toList());
        return new SectionRegisterLabware(externalName, lwType, srcs);
    }

    /**
     * Creates the part of the request linked to one section (sample)
     * @param problems receptacle for problems
     * @param row the row describing a particular section in a piece of labware
     * @return the request content for that section
     */
    public SectionRegisterContent createRequestContent(Collection<String> problems, Map<Column, Object> row) {
        SectionRegisterContent content = new SectionRegisterContent();
        String addressString = (String) row.get(Column.Section_address);
        if (!nullOrEmpty(addressString)) {
            try {
                content.setAddress(Address.valueOf(addressString));
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        content.setExternalIdentifier((String) row.get(Column.Section_external_ID));
        content.setFixative((String) row.get(Column.Fixative));
        content.setHmdmc((String) row.get(Column.HuMFre));
        content.setSectionNumber((Integer) row.get(Column.Section_number));
        content.setMedium((String) row.get(Column.Embedding_medium));
        content.setDonorIdentifier((String) row.get(Column.Donor_ID));
        String lifeStageString = (String) row.get(Column.Life_stage);
        if (!nullOrEmpty(lifeStageString)) {
            try {
                content.setLifeStage(LifeStage.valueOf(lifeStageString.toLowerCase()));
            } catch (IllegalArgumentException e) {
                problems.add("Unknown life stage: "+repr(lifeStageString));
            }
        }
        content.setReplicateNumber((String) row.get(Column.Replicate_number));
        content.setSectionThickness((Integer) row.get(Column.Section_thickness));
        content.setSpecies((String) row.get(Column.Species));
        content.setTissueType((String) row.get(Column.Tissue_type));
        content.setSpatialLocation((Integer) row.get(Column.Spatial_location));
        content.setRegion((String) row.get(Column.Section_position));
        return content;
    }

    /**
     * Test function to read an Excel file
     */
    public static void main(String[] args) throws IOException {
        SectionRegisterFileReader r = new SectionRegisterFileReaderImp();
        final Path path = Paths.get("/Users/dr6/Desktop/regtest.xlsx");
        SectionRegisterRequest request;
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(path))) {
            request = r.read(wb.getSheetAt(3));
        } catch (ValidationException e) {
            System.err.println("\n****\nException: "+e);
            System.err.println("Problems:");
            for (var problem : e.getProblems()) {
                System.err.println(" "+problem);
            }
            System.err.println("****\n");
            throw e;
        }
        System.out.println(request);
    }
}
