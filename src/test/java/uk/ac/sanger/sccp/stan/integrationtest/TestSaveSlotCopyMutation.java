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

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests save/resume slot copy
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestSaveSlotCopyMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Test
    @Transactional
    public void testSlotCopySave() throws Exception {
        User user = entityCreator.createUser("user1");
        Work work = entityCreator.createWork(null, null, null, null, null);
        OperationType opType = entityCreator.createOpType("opname", null);
        tester.setUser(user);
        String mutation = tester.readGraphQL("saveslotcopy.graphql").replace("[WORK]", work.getWorkNumber());

        Object response = tester.post(mutation);
        Map<String, String> data = chainGet(response, "data", "saveSlotCopy");
        assertEquals("STAN-A", data.get("barcode"));
        assertEquals(opType.getName(), data.get("operationType"));
        assertEquals(work.getWorkNumber(), data.get("workNumber"));
        assertEquals("LP1", data.get("lpNumber"));

        String query = tester.readGraphQL("reloadslotcopy.graphql").replace("[WORK]", work.getWorkNumber());

        response = tester.post(query);
        data = chainGet(response, "data", "reloadSlotCopy");
        assertEquals(opType.getName(), data.get("operationType"));
        assertEquals("STAN-A", data.get("barcode"));
        assertEquals(work.getWorkNumber(), data.get("workNumber"));
        assertEquals("LP1", data.get("lpNumber"));
        assertEquals("manual", data.get("executionType"));
        assertEquals("pb1", data.get("preBarcode"));
        assertEquals("lt1", data.get("labwareType"));
        assertEquals("lot1", data.get("lotNumber"));
        assertEquals("probe1", data.get("probeLotNumber"));
        assertEquals("123456", data.get("reagentALot"));
        assertEquals("987654", data.get("reagentBLot"));
        assertEquals("bs", data.get("bioState"));
        assertEquals("SGP", data.get("costing"));
        List<Map<String, ?>> sourceData = chainGet(data, "sources");
        List<Map<String, ?>> contentData = chainGet(data, "contents");
        assertThat(sourceData).containsExactly(Map.of("barcode", "STAN-0", "labwareState", "active"));
        assertThat(contentData).containsExactly(
                Map.of("sourceBarcode", "STAN-0", "sourceAddress", "A2", "destinationAddress", "A1"),
                Map.of("sourceBarcode", "STAN-1", "sourceAddress", "A1", "destinationAddress", "A2")
        );

        // Replace the save with a new save
        mutation = mutation.replace("pb1", "pb2").replace("costing: SGP", "costing: Faculty")
                .replace("manual", "automated");
        response = tester.post(mutation);
        assertEquals("STAN-A", chainGet(response, "data", "saveSlotCopy", "barcode"));
        response = tester.post(query);

        data = chainGet(response, "data", "reloadSlotCopy");
        assertEquals("pb2", data.get("preBarcode"));
        assertEquals("automated", data.get("executionType"));
        assertEquals("Faculty", data.get("costing"));
    }
}
