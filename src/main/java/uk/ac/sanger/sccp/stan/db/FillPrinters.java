package uk.ac.sanger.sccp.stan.db;

import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.exception.*;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.*;
import uk.ac.sanger.sccp.stan.model.Printer;

import java.io.IOException;
import java.util.*;

/**
 * The SQL change to set up the printers from a csv file
 * @author dr6
 */
public class FillPrinters implements CustomSqlChange {
    /** The enum type representing expected columns in the csv file */
    private enum Column {
        name,
        label_type,
        service,
    }

    /**
     * An entry representing a printer to add
     */
    private static class PrinterEntry {
        String name;
        Printer.Service service;
        Set<String> labelTypes = new LinkedHashSet<>();

        public PrinterEntry(String name, Printer.Service service, String labelType) {
            this.name = name;
            this.service = service;
            if (labelType!=null) {
                this.labelTypes.add(labelType);
            }
        }
    }

    private ResourceAccessor resourceAccessor;
    private int printerCount;

    @Override
    public SqlStatement[] generateStatements(Database db) throws CustomChangeException {
        try {
            return generateStatementsInner();
        } catch (Exception e) {
            e.printStackTrace();
            throw new CustomChangeException(e);
        }
    }

    /**
     * Reads the printers csv file and creates SQL statements to put the data into the database.
     * @return the sql statements to add the printers
     * @exception IOException there was a problem reading the resource
     */
    private SqlStatement[] generateStatementsInner() throws IOException {
        var data = CsvResourceReader.readData(Column.class, ",", resourceAccessor, "db/printers.csv");
        var entries = toEntries(data);
        List<SqlStatement> statements = new ArrayList<>();
        InsertSetStatement insertPrinters = new InsertSetStatement(null, null, "printer");
        statements.add(insertPrinters);
        for (var entry : entries) {
            InsertStatement insert = new InsertStatement(null, null, "printer");
            insert.addColumnValue("name", entry.name);
            insert.addColumnValue("service", entry.service.name());
            insertPrinters.addInsertStatement(insert);
            for (String lt : entry.labelTypes) {
                statements.add(new RawSqlStatement("INSERT INTO printer_label_type (printer_id, label_type_id)" +
                        " SELECT p.id, lt.id" +
                        " FROM printer p, label_type lt" +
                        " WHERE p.name='"+entry.name+"' and lt.name='"+lt+"'"));
            }
        }
        printerCount = entries.size();
        return statements.toArray(new SqlStatement[0]);
    }

    /**
     * Converts csv data to printer entries
     * @param data a list of maps, each representing a row in the csv file
     * @return printer entries to add to the database
     */
    private Collection<PrinterEntry> toEntries(List<Map<Column, String>> data) {
        Map<String, PrinterEntry> entries = new LinkedHashMap<>();
        for (var row : data) {
            String printerName = row.get(Column.name);
            String key = printerName.toUpperCase();
            Printer.Service service = Printer.Service.valueOf(row.get(Column.service));
            String labelType = row.get(Column.label_type);
            if (labelType!=null && labelType.isBlank()) {
                labelType = null;
            }
            PrinterEntry entry = entries.get(key);
            if (entry==null) {
                entry = new PrinterEntry(printerName, service, labelType);
                entries.put(key, entry);
            } else {
                if (entry.service != service) {
                    throw new IllegalArgumentException("Expected service "+entry.service+" for printer "+printerName);
                }
                if (labelType!=null && entry.labelTypes.contains(labelType)) {
                    throw new IllegalArgumentException("Repeated label type id "+labelType+" given for "+printerName);
                }
                if (labelType!=null) {
                    entry.labelTypes.add(labelType);
                }
            }
        }
        return entries.values();
    }

    @Override
    public String getConfirmationMessage() {
        return "Inserted "+printerCount+" printers.";
    }

    @Override
    public void setUp() throws SetupException {}

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }
}
