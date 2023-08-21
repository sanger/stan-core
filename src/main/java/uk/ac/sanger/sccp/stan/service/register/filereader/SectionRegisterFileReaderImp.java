package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.filereader.SectionRegisterFileReader.Column;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class SectionRegisterFileReaderImp extends BaseRegisterFileReader<SectionRegisterRequest, Column>
        implements SectionRegisterFileReader {

    public SectionRegisterFileReaderImp() {
        super(Column.class, 1, 3);
    }

    /**
     * Creates a registration request from the given list of column/value data.
     * @param problems receptacle for problems
     * @param rows a list of row data, where each row's data is a map from a conceptual column to the value
     * read for that column
     * @return a request created from the given data
     */
    @Override
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
        String prebarcode = getUniqueString(group.stream().map(row -> (String) row.get(Column.Prebarcode)),
                () -> problems.add("Multiple different prebarcodes specified for external ID "+externalName+"."));
        if (lwType==null) {
            problems.add("No labware type specified for external ID "+externalName+".");
        } else if (lwType.equalsIgnoreCase("xenium") && nullOrEmpty(prebarcode)) {
            problems.add("A prebarcode is expected for Xenium labware.");
        }
        String workNumber = group.stream().map(row -> (String) row.get(Column.Work_number))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if(nullOrEmpty(workNumber)) {
            problems.add("No work number specified for external ID "+externalName+".");
        }

        List<SectionRegisterContent> srcs = group.stream()
                .map(row -> createRequestContent(problems, row))
                .filter(Objects::nonNull)
                .collect(toList());
        final SectionRegisterLabware srl = new SectionRegisterLabware(externalName, lwType, srcs);
        srl.setPreBarcode(prebarcode);
        return srl;
    }

    /**
     * Creates the part of the request linked to one section (sample)
     * @param problems receptacle for problems
     * @param row the row describing a particular section in a piece of labware
     * @return the request content for that section
     */
    public SectionRegisterContent createRequestContent(Collection<String> problems, Map<Column, Object> row) {
        SectionRegisterContent content = new SectionRegisterContent();
        content.setAddress(valueToAddress(problems, (String) row.get(Column.Section_address)));
        content.setExternalIdentifier((String) row.get(Column.Section_external_ID));
        content.setFixative((String) row.get(Column.Fixative));
        content.setHmdmc((String) row.get(Column.HuMFre));
        content.setSectionNumber((Integer) row.get(Column.Section_number));
        content.setMedium((String) row.get(Column.Embedding_medium));
        content.setDonorIdentifier((String) row.get(Column.Donor_ID));
        content.setLifeStage(valueToLifeStage(problems, (String) row.get(Column.Life_stage)));
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
