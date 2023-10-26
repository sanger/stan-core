package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.request.register.OriginalSampleData;
import uk.ac.sanger.sccp.stan.request.register.OriginalSampleRegisterRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.register.filereader.OriginalSampleRegisterFileReader.Column;

import java.time.LocalDate;
import java.util.*;

import static java.util.stream.Collectors.toList;

@Service
public class OriginalSampleRegisterFileReaderImp extends BaseRegisterFileReader<OriginalSampleRegisterRequest, Column>
        implements OriginalSampleRegisterFileReader {
    static final int HEADING_ROW = 1, FIRST_DATA_ROW = 3;

    public OriginalSampleRegisterFileReaderImp() {
        super(Column.class, HEADING_ROW, FIRST_DATA_ROW);
    }

    @Override
    protected OriginalSampleRegisterRequest createRequest(Collection<String> problems, List<Map<Column, Object>> rows) {
        List<OriginalSampleData> data = rows.stream()
                .map(row -> createSampleData(problems, row))
                .filter(Objects::nonNull)
                .collect(toList());
        if (!problems.isEmpty()) {
            throw new ValidationException("The file contents are invalid.", problems);
        }
        return new OriginalSampleRegisterRequest(data);
    }

    public OriginalSampleData createSampleData(Collection<String> problems, Map<Column, ?> row) {
        if (row.entrySet().stream().allMatch(e -> e.getKey()==Column.Labware_type || e.getValue()==null)) {
            return null;
            // If every value is null (except the labware type), then it's an empty row
        }
        OriginalSampleData data = new OriginalSampleData();
        data.setWorkNumber((String) row.get(Column.Work_number));
        data.setDonorIdentifier((String) row.get(Column.Donor_identifier));
        data.setLifeStage(valueToLifeStage(problems, (String) row.get(Column.Life_stage)));
        data.setSampleCollectionDate((LocalDate) row.get(Column.Collection_date));
        data.setSpecies((String) row.get(Column.Species));
        data.setHmdmc((String) row.get(Column.HuMFre));
        data.setTissueType((String) row.get(Column.Tissue_type));
        data.setExternalIdentifier((String) row.get(Column.External_identifier));
        data.setSpatialLocation((Integer) row.get(Column.Spatial_location));
        data.setReplicateNumber((String) row.get(Column.Replicate_number));
        data.setLabwareType((String) row.get(Column.Labware_type));
        data.setFixative((String) row.get(Column.Fixative));
        data.setSolution((String) row.get(Column.Solution));
        return data;
    }
}
