package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.filereader.BlockRegisterFileReader.Column;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class BlockRegisterFileReaderImp extends BaseRegisterFileReader<BlockRegisterRequest, Column>
        implements BlockRegisterFileReader {

    protected BlockRegisterFileReaderImp() {
        super(Column.class, 1, 3);
    }

    @Override
    protected BlockRegisterRequest createRequest(Collection<String> problems, List<Map<Column, Object>> rows) {
        List<BlockRegisterLabware> brlw = createLabwareRequests(problems, rows);
        Set<String> workNumberSet = getUnique(rows.stream().map(row -> workNumberSet((String) row.get(Column.Work_number))),
                () -> problems.add("All rows must list the same work numbers."));
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        List<String> workNumbers = (nullOrEmpty(workNumberSet) ? List.of() : new ArrayList<>(workNumberSet));
        return new BlockRegisterRequest(workNumbers, brlw);
    }

    /**
     * Gets the set of work numbers specified in a row.
     * Null if none are specified.
     * @param string the string listing zero, one or more work numbers
     * @return a nonempty set of work numbers, or null
     */
    public static Set<String> workNumberSet(String string) {
        if (string != null) {
            string = string.trim().toUpperCase();
        }
        if (nullOrEmpty(string)) {
            return null;
        }
        String[] wns = string.replace(',',' ').split("\\s+");
        return Arrays.stream(wns)
                .filter(s -> !s.isEmpty())
                .collect(toSet());
    }

    /**
     * Parses the rows and groups them into labware.
     * @param problems receptacle for problems
     * @param rows rows from the file
     * @return the labware requests
     */
    public List<BlockRegisterLabware> createLabwareRequests(Collection<String> problems, List<Map<Column, Object>> rows) {
        Map<String, List<Map<Column, Object>>> groups = new LinkedHashMap<>();
        boolean anyMissing = false;
        for (var row : rows) {
            String xb = (String) row.get(Column.External_barcode);
            if (nullOrEmpty(xb)) {
                anyMissing = true;
                continue;
            }
            groups.computeIfAbsent(xb.toUpperCase(), k -> new ArrayList<>()).add(row);
        }
        if (anyMissing) {
            problems.add("Cannot process blocks without an external barcode.");
        } else if (groups.isEmpty()) {
            problems.add("No blocks requested.");
            return List.of();
        }
        return groups.values().stream().map(group -> toLabwareRequest(problems, group)).toList();
    }

    /** Stream the values in a particular column from a group of rows. */
    static <T> Stream<T> streamValues(Collection<Map<Column, Object>> rows, Column column) {
        //noinspection unchecked
        return rows.stream().map(row -> (T) row.get(column));
    }

    /** Gets the unique value from a column in a group of rows. */
    String uniqueRowValue(final Collection<String> problems,
                          Collection<Map<Column, Object>> rows, Column column,
                          Supplier<String> tooManyErrorSupplier) {
        return getUniqueString(streamValues(rows, column), () -> problems.add(tooManyErrorSupplier.get()));
    }

    /**
     * Converts a list of rows into a labware request.
     * @param problems receptacle for problems
     * @param rows rows related to the same labware
     * @return the labware request
     */
    public BlockRegisterLabware toLabwareRequest(Collection<String> problems, List<Map<Column, Object>> rows) {
        String externalBarcode = ((String) rows.getFirst().get(Column.External_barcode)).toUpperCase();
        String labwareType = uniqueRowValue(problems, rows, Column.Labware_type,
                () -> "Multiple labware types specified for external barcode "+repr(externalBarcode)+".");
        String medium = uniqueRowValue(problems, rows, Column.Embedding_medium,
                () -> "Multiple media specified for external barcode "+repr(externalBarcode)+".");
        String fixative = uniqueRowValue(problems, rows, Column.Fixative,
                () -> "Multiple fixatives specified for external barcode "+repr(externalBarcode)+".");
        List<BlockRegisterSample> samples = rows.stream().map(row -> toSample(problems, row)).toList();
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setExternalBarcode(externalBarcode);
        brl.setLabwareType(labwareType);
        brl.setMedium(medium);
        brl.setFixative(fixative);
        brl.setSamples(samples);
        return brl;
    }

    /**
     * Reads the info about a single row into a sample request
     * @param problems receptacle for problems
     * @param row data from one row
     * @return the sample information from the row
     */
    public BlockRegisterSample toSample(Collection<String> problems, Map<Column, Object> row) {
        BlockRegisterSample sample = new BlockRegisterSample();
        sample.setBioRiskCode((String) row.get(Column.Bio_risk));
        sample.setCellClass((String) row.get(Column.Cell_class));
        sample.setDonorIdentifier((String) row.get(Column.Donor_identifier));
        sample.setHmdmc((String) row.get(Column.HuMFre));
        sample.setLifeStage(valueToLifeStage(problems, (String) row.get(Column.Life_stage)));
        sample.setReplicateNumber((String) row.get(Column.Replicate_number));
        sample.setSpecies((String) row.get(Column.Species));
        sample.setTissueType((String) row.get(Column.Tissue_type));
        if (row.get(Column.Spatial_location)==null) {
            problems.add("Spatial location not specified.");
        } else {
            sample.setSpatialLocation((Integer) row.get(Column.Spatial_location));
        }
        if (row.get(Column.Last_known_section)==null) {
            problems.add("Last known section not specified.");
        } else {
            sample.setHighestSection((Integer) row.get(Column.Last_known_section));
        }
        sample.setExternalIdentifier((String) row.get(Column.External_identifier));
        sample.setSampleCollectionDate((LocalDate) row.get(Column.Collection_date));
        sample.setAddresses(parseAddresses(problems, (String) row.get(Column.Slot_address)));
        return sample;
    }

    /** Parses a string into a list of slot addresses. */
    static List<Address> parseAddresses(Collection<String> problems, String string) {
        if (string != null) {
            string = string.trim();
        }
        if (nullOrEmpty(string)) {
            return List.of();
        }
        String stringUpper = string.toUpperCase();
        String[] parts = stringUpper.replace(',',' ').split("\\s+");
        try {
            return Arrays.stream(parts).map(Address::valueOf).toList();
        } catch (IllegalArgumentException e) {
            // OK, try again with commas included
        }
        parts = stringUpper.split("\\s+");
        try {
            return Arrays.stream(parts).map(Address::valueOf).toList();
        } catch (IllegalArgumentException e) {
            problems.add("Couldn't parse slot addresses: "+repr(string));
        }
        return List.of();
    }


    /**
     * Test function to read an Excel file
     */
    public static void main(String[] args) throws IOException {
        BlockRegisterFileReader r = new BlockRegisterFileReaderImp();
        final Path path = Paths.get("/Users/dr6/Desktop/blockreg.xlsx");
        BlockRegisterRequest request;
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(path))) {
            request = r.read(wb.getSheetAt(SHEET_INDEX));
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
