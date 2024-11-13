package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;
import static uk.ac.sanger.sccp.utils.BasicUtils.hashSetOf;

/**
 * Tests the history queries
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestHistoryQuery {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ActionRepo actionRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private WorkRepo workRepo;

    @Transactional
    @Test
    public void testHistory() throws Exception {
        entityCreator.createBioRisk("biorisk1");
        Work work = entityCreator.createWork(null, null, null, null, null);
        String mutation = tester.readGraphQL("register.graphql").replace("SGP1", work.getWorkNumber());
        User user = entityCreator.createUser("user1");
        tester.setUser(user);

        Map<String, ?> response = tester.post(mutation);
        Map<String, ?> lwData = chainGet(response, "data", "register", "labware", 0);
        String barcode = chainGet(lwData, "barcode");
        int sampleId = chainGet(lwData, "slots", 0, "samples", 0, "id");

        String[] queryHeaders = {
                "historyForSampleId(sampleId: "+sampleId+")",
                "historyForLabwareBarcode(barcode: \""+barcode+"\")",
                "historyForExternalName(externalName: \"TISSUE1\")",
                "historyForDonorName(donorName: \"DONOR1\")",
        };

        String baseQuery = tester.readGraphQL("history.graphql");
        String queryBody = baseQuery.substring(baseQuery.indexOf(')') + 1);

        for (String queryHeader : queryHeaders) {
            String query = "query { " + queryHeader + queryBody;
            response = tester.post(query);

            String queryName = queryHeader.substring(0, queryHeader.indexOf('('));
            Map<String, ?> historyData = chainGet(response, "data", queryName);
            List<Map<String,?>> entries = chainGetList(historyData, "entries");
            assertThat(entries).hasSize(1);
            Map<String,?> entry = entries.getFirst();
            assertNotNull(entry.get("eventId"));
            assertEquals("Register", entry.get("type"));
            assertEquals(sampleId, entry.get("sampleId"));
            int labwareId = (int) entry.get("sourceLabwareId");
            assertEquals(labwareId, entry.get("destinationLabwareId"));
            assertNotNull(entry.get("time"));
            assertEquals("user1", entry.get("username"));
            assertEquals(List.of(), entry.get("details"));

            List<Map<String,?>> labwareData = chainGetList(historyData, "labware");
            assertThat(labwareData).hasSize(1);
            lwData = labwareData.getFirst();
            assertEquals(labwareId, lwData.get("id"));
            assertEquals(barcode, lwData.get("barcode"));
            assertEquals("active", lwData.get("state"));

            List<Map<String,?>> samplesData = chainGetList(historyData, "samples");
            assertThat(samplesData).hasSize(1);
            Map<String,?> sampleData = samplesData.getFirst();
            assertEquals(sampleId, sampleData.get("id"));
            assertEquals("TISSUE1", chainGet(sampleData, "tissue", "externalName"));
            assertEquals("Bone", chainGet(sampleData, "tissue", "spatialLocation", "tissueType", "name"));
            assertEquals("DONOR1", chainGet(sampleData, "tissue", "donor", "donorName"));
            assertNull(sampleData.get("section"));
        }
    }

    @Transactional
    @Test
    public void testHistoryForWorkNumber() throws Exception {
        Work work = entityCreator.createWork(null, null, null, null, null);
        User user = entityCreator.createUser("user1");

        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware lw = entityCreator.createLabware("STAN-A1", lt, sample);
        Slot slot = lw.getFirstSlot();
        OperationType opType = entityCreator.createOpType("Toast", null, OperationTypeFlag.IN_PLACE);
        Operation op = opRepo.save(new Operation(null, opType, null, null, user));
        actionRepo.save(new Action(null, op.getId(), slot, slot, sample, sample));
        entityManager.refresh(op);
        work.setOperationIds(hashSetOf(op.getId()));
        workRepo.save(work);

        String baseQuery = tester.readGraphQL("history.graphql");
        String queryBody = baseQuery.substring(baseQuery.indexOf(')') + 1);
        String query = "query { historyForWorkNumber(workNumber: \"" + work.getWorkNumber() + "\")" + queryBody;
        tester.setUser(user);

        Map<String, ?> response = tester.post(query);
        Map<String, List<Map<String, ?>>> historyData = chainGet(response, "data", "historyForWorkNumber");
        List<Map<String, ?>> entriesData = historyData.get("entries");
        List<Map<String, ?>> labwaresData = historyData.get("labware");
        List<Map<String, ?>> samplesData = historyData.get("samples");

        assertThat(labwaresData).hasSize(1);
        assertEquals(lw.getId(), labwaresData.getFirst().get("id"));
        assertEquals(lw.getBarcode(), labwaresData.getFirst().get("barcode"));

        assertThat(samplesData).hasSize(1);
        assertEquals(sample.getId(), samplesData.getFirst().get("id"));

        assertThat(entriesData).hasSize(1);
        Map<String, ?> entryData = entriesData.getFirst();
        assertEquals(op.getId(), entryData.get("eventId"));
        assertEquals(opType.getName(), entryData.get("type"));
        assertEquals(work.getWorkNumber(), entryData.get("workNumber"));
    }
}
