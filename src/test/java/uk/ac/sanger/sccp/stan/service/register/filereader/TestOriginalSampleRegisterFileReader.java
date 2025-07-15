package uk.ac.sanger.sccp.stan.service.register.filereader;

import org.apache.poi.ss.usermodel.Row;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.stan.request.register.OriginalSampleData;
import uk.ac.sanger.sccp.stan.request.register.OriginalSampleRegisterRequest;
import uk.ac.sanger.sccp.stan.service.register.filereader.OriginalSampleRegisterFileReader.Column;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.spy;

/**
 * Tests {@link OriginalSampleRegisterFileReaderImp}
 * @author dr6
 */
public class TestOriginalSampleRegisterFileReader extends BaseTestFileReader {

    // Check that the pattern for each column accepts that column's name
    @Test
    void testColumns() {
        final Column[] columns = Column.values();
        for (Column column : columns) {
            if (!column.name().startsWith("_")) {
                assertSame(column, IColumn.forHeading(columns, column.toString()));
            }
        }
    }

    @Test
    void testHeadings() {
        OriginalSampleRegisterFileReaderImp reader = spy(new OriginalSampleRegisterFileReaderImp());
        String[] headings = {"mandatory whatever", "SGP number", "donor identifier", "life stage",
                "if such and such date of collection", "species", "cell class", "bio risk", "humfre", "tissue type",
                "external identifier", "spatial location", "replicate", "labware type",
                "fixative", "solution of things", "information about whatever"};
        Row row = mockRow(headings);
        List<String> problems = new ArrayList<>(0);
        Map<Column, Integer> columnIndexes = reader.indexColumns(problems, row);
        for (Column column : Column.values()) {
            if (column.getDataType()!=Void.class) {
                assertEquals(column.ordinal(), columnIndexes.get(column), column.name());
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void testCreateSampleData() {
        OriginalSampleRegisterFileReaderImp reader = spy(new OriginalSampleRegisterFileReaderImp());
        List<String> problems = new ArrayList<>(0);
        Map<Column, Object> row = new EnumMap<>(Column.class);
        final LocalDate date = LocalDate.of(2023, 1, 2);
        Object[] values = {
                "junk", "SGP15", "DONOR1", "fetal", date,
                "human", "tissue", "risk1", "12345", "tt1", "EXT1", 12, "11A", "bowl", "fix1", "sol1", "junkyjunk"
        };
        Column[] columns = Column.values();
        for (int i = 0; i < values.length; ++i) {
            if (columns[i].getDataType()!=Void.class) {
                row.put(columns[i], values[i]);
            }
        }
        OriginalSampleData data = reader.createSampleData(problems, row);
        assertThat(problems).isEmpty();

        assertEquals("SGP15", data.getWorkNumber());
        assertEquals("DONOR1", data.getDonorIdentifier());
        assertEquals(LifeStage.fetal, data.getLifeStage());
        assertEquals(date, data.getSampleCollectionDate());
        assertEquals("human", data.getSpecies());
        assertEquals("tissue", data.getCellClass());
        assertEquals("risk1", data.getBioRiskCode());
        assertEquals("12345", data.getHmdmc());
        assertEquals("tt1", data.getTissueType());
        assertEquals("EXT1", data.getExternalIdentifier());
        assertEquals(12, data.getSpatialLocation());
        assertEquals("11A", data.getReplicateNumber());
        assertEquals("bowl", data.getLabwareType());
        assertEquals("fix1", data.getFixative());
        assertEquals("sol1", data.getSolution());
    }

    @Test
    void testCreateRequest() {
        OriginalSampleRegisterFileReaderImp reader = spy(new OriginalSampleRegisterFileReaderImp());
        Map<Column, Object> row1 = Map.of(Column.Donor_identifier, "DONOR1");
        Map<Column, Object> row2 = Map.of(Column.Donor_identifier, "DONOR2");
        List<String> problems = new ArrayList<>(0);
        OriginalSampleRegisterRequest request = reader.createRequest(problems, List.of(row1, row2));
        List<OriginalSampleData> osds = request.getSamples();
        assertThat(osds).hasSize(2);
        assertEquals("DONOR1", osds.get(0).getDonorIdentifier());
        assertEquals("DONOR2", osds.get(1).getDonorIdentifier());
    }
}
