package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.User;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

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

    @Transactional
    @Test
    public void testHistory() throws Exception {
        String mutation = tester.readGraphQL("register.graphql");
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
            Map<String,?> entry = entries.get(0);
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
            lwData = labwareData.get(0);
            assertEquals(labwareId, lwData.get("id"));
            assertEquals(barcode, lwData.get("barcode"));
            assertEquals("active", lwData.get("state"));

            List<Map<String,?>> samplesData = chainGetList(historyData, "samples");
            assertThat(samplesData).hasSize(1);
            Map<String,?> sampleData = samplesData.get(0);
            assertEquals(sampleId, sampleData.get("id"));
            assertEquals("TISSUE1", chainGet(sampleData, "tissue", "externalName"));
            assertEquals("Bone", chainGet(sampleData, "tissue", "spatialLocation", "tissueType", "name"));
            assertEquals("DONOR1", chainGet(sampleData, "tissue", "donor", "donorName"));
            assertNull(sampleData.get("section"));
        }
    }
}
