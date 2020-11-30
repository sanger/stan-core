package uk.ac.sanger.sccp.stan.db;

import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.exception.*;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.*;

import java.io.*;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
public class FillTissueTypesAndSpatialLocations implements CustomSqlChange {

    enum Column {
        TTCODE("Tissue type code"),
        TTNAME("Tissue type name"),
        SLCODE("Spatial location code"),
        SLNAME("Spatial location name"),
        ;

        final String heading;
        Column(String heading) {
            this.heading = heading;
        }
    }

    private ResourceAccessor resourceAccessor;
    private int ttCount;

    @Override
    public SqlStatement[] generateStatements(Database db) throws CustomChangeException {
        try {
            List<Map<Column, String>> slRows = readData("db/tissue_types_and_spatial_locations.tsv");
            List<Map<Column, String>> ttRows = slRows.stream()
                    .map(row -> {
                        EnumMap<Column, String> tissueTypeData = new EnumMap<>(Column.class);
                        tissueTypeData.put(Column.TTCODE, row.get(Column.TTCODE));
                        tissueTypeData.put(Column.TTNAME, row.get(Column.TTNAME));
                        return tissueTypeData;
                    })
                    .distinct()
                    .collect(toList());
            List<SqlStatement> statements = new ArrayList<>();
            InsertSetStatement insertTissueTypes = new InsertSetStatement(null, null, "tissue_type");
            for (Map<Column, String> ttRow : ttRows) {
                InsertStatement insertTT = new InsertStatement(null, null, "tissue_type");
                insertTT.addColumnValue("name", ttRow.get(Column.TTNAME));
                insertTT.addColumnValue("code", ttRow.get(Column.TTCODE));
                insertTissueTypes.addInsertStatement(insertTT);
            }
            statements.add(insertTissueTypes);
            for (var ttRow : ttRows) {
                String ttName = ttRow.get(Column.TTNAME);
                statements.add(new RawSqlStatement("INSERT INTO spatial_location (tissue_type_id, code, name)" +
                        " SELECT tt.id, 0, 'Unknown'" +
                        " FROM tissue_type tt " +
                        " WHERE tt.name='"+ttName+"'"));
                if (ttName.equalsIgnoreCase("Unknown")) {
                    continue;
                }
                for (int i = 1; i <= 6; ++i) {
                    statements.add(new RawSqlStatement("INSERT INTO spatial_location (tissue_type_id, code, name)" +
                            " SELECT tt.id, "+i+", 'Location "+i+"'" +
                            " FROM tissue_type tt " +
                            " WHERE tt.name='"+ttName+"'"));
                }
            }
            for (Map<Column, String> slRow : slRows) {
                if (slRow.get(Column.SLCODE).equals("0")) {
                    continue;
                }
                statements.add(new RawSqlStatement("UPDATE spatial_location sl" +
                        " JOIN tissue_type tt ON (sl.tissue_type_id=tt.id)" +
                        " SET sl.name='"+slRow.get(Column.SLNAME)+"'" +
                        " WHERE tt.name='"+slRow.get(Column.TTNAME)+"'" +
                        " AND sl.code="+slRow.get(Column.SLCODE)));
            }
            ttCount = ttRows.size();
            return statements.toArray(new SqlStatement[0]);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CustomChangeException(e);
        }
    }

    private Map<Column, Integer> columnIndexes(String hline) {
        String[] headings = hline.split("\t");
        Map<Column, Integer> indexes = new EnumMap<>(Column.class);
        Column[] columns = Column.values();
        for (int i = 0; i < headings.length; ++i) {
            String heading = headings[i];
            for (Column column : columns) {
                if (column.heading.equalsIgnoreCase(heading)) {
                    indexes.put(column, i);
                }
            }
        }
        for (Column column : columns) {
            if (indexes.get(column)==null) {
                throw new IllegalArgumentException("Missing heading in file: "+column.heading);
            }
        }
        return indexes;
    }

    private Map<Column, String> toColumnData(Map<Column, Integer> indexes, String line) {
        String[] parts = line.split("\t");
        Map<Column, String> data = new EnumMap<>(Column.class);
        for (Map.Entry<Column, Integer> entry : indexes.entrySet()) {
            int index = entry.getValue();
            if (index >= 0 && index < parts.length) {
                data.put(entry.getKey(), parts[index]);
            }
        }
        return data;
    }

    public List<Map<Column, String>> readData(String path) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                resourceAccessor.openStream(null, path)))) {
            Map<Column, Integer> indexes = columnIndexes(in.readLine());
            return in.lines().filter(line -> !line.trim().isEmpty())
                    .map(line -> toColumnData(indexes, line))
                    .collect(toList());
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Inserted "+ttCount+" tissue types with spatial locations.";
    }

    @Override
    public void setUp() throws SetupException {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Override
    public ValidationErrors validate(Database db) {
        return null;
    }

}