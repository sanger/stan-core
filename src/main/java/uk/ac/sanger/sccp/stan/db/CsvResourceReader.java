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

    public CsvResourceReader(Class<C> columnClass, String separator) {
        this.columnClass = columnClass;
        this.separator = separator;
    }

    public List<Map<C, String>> readData(ResourceAccessor resourceAccessor, String path) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                resourceAccessor.openStream(null, path)))) {
            Map<C, Integer> indexes = columnIndexes(in.readLine());
            return in.lines().filter(line -> !line.isBlank())
                    .map(line -> toColumnData(indexes, line))
                    .collect(toList());
        }
    }

    public static <C extends Enum<C>> List<Map<C, String>> readData(Class<C> columnClass, String separator,
                                                                    ResourceAccessor resourceAccessor, String path)
            throws IOException {
        CsvResourceReader<C> reader = new CsvResourceReader<>(columnClass, separator);
        return reader.readData(resourceAccessor, path);
    }

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


    private <V> Map<C, V> cMap() {
        return new EnumMap<>(columnClass);
    }

    private String[] split(String line) {
        return line.split(this.separator);
    }

}
