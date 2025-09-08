package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @param <RequestType> the type of request being generated
 * @param <ColumnType> the type representing columns in the file
 * @author dr6
 */
public abstract class BaseRegisterFileReader<RequestType, ColumnType extends Enum<ColumnType>& IColumn> {

    protected static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    protected int headingRowIndex, dataRowIndex;

    protected Class<ColumnType> columnClass;

    protected BaseRegisterFileReader(Class<ColumnType> columnClass, int headingRowIndex, int dataRowIndex) {
        this.columnClass = columnClass;
        this.headingRowIndex = headingRowIndex;
        this.dataRowIndex = dataRowIndex;
    }

    protected abstract RequestType createRequest(Collection<String> problems, List<Map<ColumnType, Object>> rows);

    protected Stream<ColumnType> getColumns() {
        return Arrays.stream(columnClass.getEnumConstants());
    }

    protected <V> Map<ColumnType, V> makeColumnMap() {
        return new EnumMap<>(columnClass);
    }

    protected ColumnType columnForHeading(String heading) {
        return IColumn.forHeading(columnClass.getEnumConstants(), heading);
    }

    public RequestType read(Sheet sheet) {
        final Collection<String> problems = new LinkedHashSet<>();
        Map<ColumnType, Integer> columnIndex = indexColumns(problems, sheet.getRow(headingRowIndex));
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        List<Map<ColumnType, Object>> rows = stream(sheet)
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
     * Finds the indexes of {@link IColumn} headings in the given row.
     * Empty cells are skipped. Each column should be matched once, and no unrecognised headings should be found.
     * @param problems receptacle for problems found
     * @param row the headings row
     * @return a map of column to index
     */
    public Map<ColumnType, Integer> indexColumns(Collection<String> problems, Row row) {
        Map<ColumnType, Integer> map = makeColumnMap();
        for (Cell cell : row) {
            String heading = (cell==null ? null : cell.getStringCellValue());
            if (StringUtils.isBlank(heading)) {
                continue;
            }
            heading = heading.trim();
            ColumnType column = columnForHeading(heading);
            if (column==null) {
                problems.add("Unexpected column heading: "+repr(heading));
            } else if (map.get(column)!=null) {
                problems.add("Repeated column: "+column);
            } else if (column.isRequired() || column.getDataType()!=Void.class) {
                map.put(column, cell.getColumnIndex());
            }
        }
        List<String> missingColumns = getColumns()
                .filter(c -> c.isRequired() && map.get(c)==null)
                .map(Object::toString)
                .toList();
        if (!missingColumns.isEmpty()) {
            problems.add("Missing columns: "+missingColumns);
        }
        return map;
    }

    /**
     * Reads values from the given row as linked to the given map of columns and integers.
     * @param problems receptacle for problems found
     * @param columnIndex map of {@link IColumn} to index in the row
     * @param row the row to read
     * @return a map of {@link IColumn} to value from the row
     */
    public Map<ColumnType, Object> readRow(final Collection<String> problems, Map<ColumnType, Integer> columnIndex, Row row) {
        final Map<ColumnType, Object> map = makeColumnMap();
        for (var entry : columnIndex.entrySet()) {
            ColumnType key = entry.getKey();
            Object value = readCellValue(problems, key, row.getCell(entry.getValue()));
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Reads the value for a particular column from the given cell.
     * Problems found will indicate the address of the cell where the problem occurred.
     * @param problems receptacle for problems found
     * @param column the column we want a value for
     * @param cell the cell we want to read a value from
     * @return a value from the given cell
     */
    public Object readCellValue(Collection<String> problems, IColumn column, Cell cell) {
        try {
            return cellValue(column.getDataType(), cell);
        } catch (RuntimeException e) {
            problems.add("At cell "+cell.getAddress()+": "+e.getMessage());
        }
        return null;
    }

    public LifeStage valueToLifeStage(Collection<String> problems, String lifeStageString) {
        if (!nullOrEmpty(lifeStageString) && !lifeStageString.equalsIgnoreCase("n/a")) {
            try {
                return LifeStage.valueOf(lifeStageString.toLowerCase());
            } catch (IllegalArgumentException e) {
                problems.add("Unknown life stage: " + repr(lifeStageString));
            }
        }
        return null;
    }

    public Address valueToAddress(Collection<String> problems, String addressString) {
        if (!nullOrEmpty(addressString)) {
            try {
                return Address.valueOf(addressString);
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        return null;
    }

    /**
     * Tries to get a date out of a cell.
     * If the cell claims to be a string, try and parse it as day/month/year.
     * @param cell a cell
     * @return the date found, or null if the cell seems to be empty
     * @exception IllegalArgumentException if the cell value can't be interpreted as a date
     * @exception IllegalStateException may be thrown from the POI cells if they are of the wrong type
     */
    public LocalDate cellDate(Cell cell) {
        if (cell.getCellType()==CellType.STRING) {
            String string = cell.getStringCellValue();
            if (nullOrEmpty(string)) {
                return null;
            }
            return LocalDate.parse(string, DATE_FORMAT);
        }
        LocalDateTime ldt = cell.getLocalDateTimeCellValue();
        if (ldt==null) {
            return null;
        }
        return ldt.toLocalDate();
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
        if (cellType==null || cellType==CellType.BLANK || type==Void.class) {
            return null;
        }
        if (type==LocalDate.class) {
            return (T) cellDate(cell);
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
        return getUnique(values, multipleValues, String::equalsIgnoreCase);
    }

    /**
     * Gets the unique value from the given stream. Null values are ignored.
     * If multiple different values are found, the <tt>multipleValues</tt> function is called,
     * and the first found value is returned.
     * @param values the stream of values
     * @param multipleValues callback to run if multiple different values are found
     * @return the value found, or null if no value is found
     * @param <V> the type of values
     */
    public <V> V getUnique(Stream<V> values, Runnable multipleValues) {
        return getUnique(values, multipleValues, null);
    }

    /**
     * Gets the unique value from the given stream. Null values are ignored.
     * If multiple different values are found, the <tt>multipleValues</tt> function is called,
     * and the first found value is returned.
     * @param values the stream of values
     * @param multipleValues callback to run if multiple different values are found
     * @param equal predicate to test if two values count as equal
     * @return the value found, or null if no value is found
     * @param <V> the type of values
     */
    public <V> V getUnique(Stream<V> values, Runnable multipleValues, BiPredicate<V,V> equal) {
        if (equal==null) {
            equal = Object::equals;
        }
        Iterable<V> iter = values::iterator;
        V found = null;
        for (V value : iter) {
            if (found==null) {
                found = value;
            } else if (value != null && !equal.test(found, value)) {
                multipleValues.run();
                break;
            }
        }
        return found;
    }
}
