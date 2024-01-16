package uk.ac.sanger.sccp.stan.db;

import liquibase.resource.ResourceAccessor;

import java.io.*;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Helper to read csv/tsv resource data
 * @author dr6
 */
public class CsvResourceReader<C extends Enum<C>> {
    private final Class<C> columnClass;
    private final String separator;

    /**
     * Creates a new csv reader
     * @param columnClass the enum class whose values are used to represent expected columns in the csv file
     * @param separator the separator between values in the csv. This is used by {@link String#split(String)}, so
     * it should be regex-compatible
     */
    public CsvResourceReader(Class<C> columnClass, String separator) {
        this.columnClass = columnClass;
        this.separator = separator;
    }

    /**
     * Reads CSV data from a file, as a list of maps where each map is the column/data info for a row.
     * @param resourceAccessor accessor to get resource
     * @param path the file path to read from the resource
     * @return a list of maps
     * @exception IOException there was a problem reading the stream
     */
    public List<Map<C, String>> readData(ResourceAccessor resourceAccessor, String path) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                resourceAccessor.openStream(null, path)))) {
            Map<C, Integer> indexes = columnIndexes(in.readLine());
            return in.lines().filter(line -> !line.isBlank())
                    .map(line -> toColumnData(indexes, line))
                    .collect(toList());
        }
    }

    /**
     * Creates a reader as specified and uses it to read the specified resource.
     * @param columnClass the enum class indicating the columns
     * @param separator the separator between columns in the csv (e.g. a comma)
     * @param resourceAccessor the accessor to get the resource
     * @param path the file path to read from the resource
     * @return a list of maps, each representing the column/value data in a row
     * @param <C> the enum type of the columns
     * @exception IOException there was a problem reading the stream
     */
    public static <C extends Enum<C>> List<Map<C, String>> readData(Class<C> columnClass, String separator,
                                                                    ResourceAccessor resourceAccessor, String path)
            throws IOException {
        CsvResourceReader<C> reader = new CsvResourceReader<>(columnClass, separator);
        return reader.readData(resourceAccessor, path);
    }

    /**
     * Converts a line in a csv file to a map of column to value
     * @param indexes the indexes of each column
     * @param line a line in the csv file
     * @return a map from the column type to the values for that row
     */
    private Map<C, String> toColumnData(Map<C, Integer> indexes, String line) {
        String[] parts = split(line);
        Map<C, String> data = cMap();
        for (var entry : indexes.entrySet()) {
            int index = entry.getValue();
            if (index >= 0 && index < parts.length) {
                data.put(entry.getKey(), parts[index]);
            }
        }
        return data;
    }

    /**
     * Finds the indexes of each column using the header line in the csv
     * @param hline the header line in the csv file
     * @return a map from column type to the index of that column in the header line
     */
    private Map<C, Integer> columnIndexes(String hline) {
        String[] headings = split(hline);
        Map<C, Integer> indexes = cMap();
        final C[] columns = columnClass.getEnumConstants();
        for (int i = 0; i < headings.length; ++i) {
            String heading = headings[i].trim();
            for (C column : columns) {
                if (heading.equalsIgnoreCase(column.toString())) {
                    if (indexes.get(column)!=null) {
                        throw new IllegalArgumentException("Repeated column: "+heading);
                    }
                    indexes.put(column, i);
                    break;
                }
            }
        }
        for (C column : columns) {
            if (indexes.get(column)==null) {
                throw new IllegalArgumentException("Missing column: "+column);
            }
        }
        return indexes;
    }

    /**
     * Creates a new empty map from this reader's column type
     * @return a new empty map
     * @param <V> the type of values accepted by the map
     */
    private <V> Map<C, V> cMap() {
        return new EnumMap<>(columnClass);
    }

    /**
     * Splits the given line using this reader's separator field
     * @param line a line of text in the csv file
     * @return the split result of the line
     */
    private String[] split(String line) {
        return line.split(this.separator);
    }

}
