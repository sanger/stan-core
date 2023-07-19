package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.filereader.BlockRegisterFileReader.Column;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class BlockRegisterFileReaderImp extends BaseRegisterFileReader<RegisterRequest, Column>
        implements BlockRegisterFileReader {

    protected BlockRegisterFileReaderImp() {
        super(Column.class, 1, 3);
    }

    @Override
    protected RegisterRequest createRequest(Collection<String> problems, List<Map<Column, Object>> rows) {
        List<BlockRegisterRequest> blockRequests = rows.stream()
                .map(row -> createBlockRequest(problems, row))
                .collect(toList());
        String workNumber = getUniqueString(rows.stream().map(row -> (String) row.get(Column.Work_number)),
                () -> problems.add("Multiple work numbers specified."));
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        return new RegisterRequest(blockRequests, workNumber==null ? List.of() : List.of(workNumber));
    }

    /**
     * Creates the part of the request for registering one block from a row of data
     * @param problems receptacle for problems found
     * @param row the data from one row of the excel file
     * @return a block register request based on the given row
     */
    public BlockRegisterRequest createBlockRequest(Collection<String> problems, Map<Column, Object> row) {
        BlockRegisterRequest br = new BlockRegisterRequest();
        br.setDonorIdentifier((String) row.get(Column.Donor_identifier));
        br.setFixative((String) row.get(Column.Fixative));
        br.setHmdmc((String) row.get(Column.HuMFre));
        br.setMedium((String) row.get(Column.Embedding_medium));
        br.setExternalIdentifier((String) row.get(Column.External_identifier));
        br.setSpecies((String) row.get(Column.Species));
        br.setSampleCollectionDate((LocalDate) row.get(Column.Collection_date));
        br.setLifeStage(valueToLifeStage(problems, (String) row.get(Column.Life_stage)));
        br.setTissueType((String) row.get(Column.Tissue_type));
        if (row.get(Column.Spatial_location)==null) {
            problems.add("Spatial location not specified.");
        } else {
            br.setSpatialLocation((Integer) row.get(Column.Spatial_location));
        }
        br.setReplicateNumber((String) row.get(Column.Replicate_number));
        if (row.get(Column.Last_known_section)==null) {
            problems.add("Last known section not specified.");
        } else {
            br.setHighestSection((Integer) row.get(Column.Last_known_section));
        }
        br.setLabwareType((String) row.get(Column.Labware_type));
        return br;
    }

    /**
     * Test function to read an Excel file
     */
    public static void main(String[] args) throws IOException {
        BlockRegisterFileReader r = new BlockRegisterFileReaderImp();
        final Path path = Paths.get("/Users/dr6/Desktop/regtest.xlsx");
        RegisterRequest request;
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(path))) {
            request = r.read(wb.getSheetAt(2));
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
