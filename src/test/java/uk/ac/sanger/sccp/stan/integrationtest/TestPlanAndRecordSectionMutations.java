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

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests the plan and record section mutations
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestPlanAndRecordSectionMutations {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testPlanAndRecordSection() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        Sample sample1 = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1"), null);
        Sample sample2 = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR2"), "TISSUE2"), null);
        Labware sourceBlock1 = entityCreator.createBlock("STAN-B70C", sample1);
        Labware sourceBlock2 = entityCreator.createBlock("STAN-B70D", sample2);

        // Recording the plan

        String mutation = tester.readGraphQL("plan.graphql");
        mutation = mutation.replace("55555", String.valueOf(sample1.getId()));
        mutation = mutation.replace("55556", String.valueOf(sample2.getId()));
        Map<String, ?> result = tester.post(mutation);
        assertNull(result.get("errors"));
        Object resultPlan = chainGet(result, "data", "plan");
        List<?> planResultLabware = chainGet(resultPlan, "labware");
        assertEquals(1, planResultLabware.size());
        String barcode = chainGet(planResultLabware, 0, "barcode");
        assertNotNull(barcode);
        assertEquals("Slide", chainGet(planResultLabware, 0, "labwareType", "name"));
        List<?> resultOps = chainGet(resultPlan, "operations");
        assertEquals(resultOps.size(), 1);
        assertEquals("Section", chainGet(resultOps, 0, "operationType", "name"));
        List<Map<String, ?>> resultActions = chainGet(resultOps, 0, "planActions");

        String[] expectedPlanDestAddresses = { "A1", "A1", "B1", "B2" };
        int[] expectedPlanSampleId = {sample1.getId(), sample2.getId(), sample1.getId(), sample2.getId()};
        assertEquals(expectedPlanDestAddresses.length, resultActions.size());

        for (int i = 0; i < expectedPlanDestAddresses.length; ++i) {
            Map<String, ?> resultAction = resultActions.get(i);
            assertEquals("A1", chainGet(resultAction, "source", "address"));
            assertEquals(expectedPlanDestAddresses[i], chainGet(resultAction, "destination", "address"));
            assertEquals((Integer) expectedPlanSampleId[i], chainGet(resultAction, "sample", "id"));
            assertNotNull(chainGet(resultAction, "destination", "labwareId"));
        }

        // Retrieving plan data

        String planQuery = tester.readGraphQL("plandata.graphql").replace("$BARCODE", barcode);
        result = tester.post(planQuery);

        Map<String, ?> planData = chainGet(result, "data", "planData");
        assertEquals("Section", chainGet(planData, "plan", "operationType", "name"));
        assertEquals(barcode, chainGet(planData, "destination", "barcode"));
        assertThat(IntegrationTestUtils.<Map<String, String>>chainGetList(planData, "sources").stream()
                .map(m -> m.get("barcode"))
                .collect(toList())).containsExactlyInAnyOrder("STAN-B70C", "STAN-B70D");

        // Confirming
        Work work = entityCreator.createWork(null, null, null);

        String recordMutation = tester.readGraphQL("confirmsection.graphql");
        recordMutation = recordMutation.replace("$BARCODE", barcode)
                .replace("55555", String.valueOf(sample1.getId()))
                .replace("55556", String.valueOf(sample2.getId()))
                .replace("SGP4000", work.getWorkNumber());
        result = tester.post(recordMutation);
        assertNull(result.get("errors"));

        Object resultConfirm = chainGet(result, "data", "confirmSection");
        List<?> resultLabware = chainGet(resultConfirm, "labware");
        assertEquals(1, resultLabware.size());
        assertEquals(barcode, chainGet(resultLabware, 0, "barcode"));
        List<?> slots = chainGet(resultLabware, 0, "slots");
        assertEquals(6, slots.size()); // Slide has 6 slots

        List<Map<String, ?>> a1Samples = chainGetList(slots.stream()
                .filter(sd -> chainGet(sd, "address").equals("A1"))
                .findAny()
                .orElseThrow(), "samples");

        String[] expectedTissueNames = { "TISSUE1", "TISSUE1", "TISSUE2" };
        int[] expectedSecNum = { 14, 15, 15 };

        assertEquals(expectedTissueNames.length, a1Samples.size());
        for (int i = 0; i < expectedTissueNames.length; ++i) {
            Map<String, ?> sam = a1Samples.get(i);
            assertEquals(expectedTissueNames[i], chainGet(sam, "tissue", "externalName"));
            assertEquals(expectedSecNum[i], (int) sam.get("section"));
        }

        List<Map<String, ?>> b2Samples = chainGetList(slots.stream()
                .filter(sd -> chainGet(sd, "address").equals("B2"))
                .findAny()
                .orElseThrow(), "samples");

        assertEquals(1, b2Samples.size());
        Map<String, ?> sam = b2Samples.get(0);
        assertEquals("TISSUE2", chainGet(sam, "tissue", "externalName"));
        assertEquals(17, (int) sam.get("section"));

        resultOps = chainGet(resultConfirm, "operations");
        assertEquals(resultOps.size(), 1);
        Object resultOp = resultOps.get(0);
        assertNotNull(chainGet(resultOp, "performed"));
        assertEquals("Section", chainGet(resultOp, "operationType", "name"));
        List<Map<String,?>> actions = chainGet(resultOp, "actions");
        int[] expectedSourceLabwareIds = {sourceBlock1.getId(), sourceBlock1.getId(), sourceBlock2.getId(), sourceBlock2.getId()};
        String[] expectedDestAddress = { "A1", "A1", "A1", "B2" };
        String[] expectedActionTissues = { "TISSUE1", "TISSUE1", "TISSUE2", "TISSUE2" };
        int[] expectedActionSecNum = { 14,15,15,17 };

        assertEquals(expectedSourceLabwareIds.length, actions.size());
        int destLabwareId = -1;
        for (int i = 0; i < expectedSourceLabwareIds.length; ++i) {
            Map<String, ?> action = actions.get(i);
            assertEquals("A1", chainGet(action, "source", "address"));
            assertEquals(expectedSourceLabwareIds[i], (int) chainGet(action, "source", "labwareId"));
            assertEquals(expectedDestAddress[i], chainGet(action, "destination", "address"));
            if (i==0) {
                destLabwareId = chainGet(action, "destination", "labwareId");
            } else {
                assertEquals(destLabwareId, (int) chainGet(action, "destination", "labwareId"));
            }
            assertEquals(expectedActionTissues[i], chainGet(action, "sample", "tissue", "externalName"));
            assertEquals(expectedActionSecNum[i], (int) chainGet(action, "sample", "section"));
        }
        // Check that the source blocks' highest section numbers have been updated
        entityManager.refresh(sourceBlock1);
        entityManager.refresh(sourceBlock2);
        assertEquals(15, sourceBlock1.getFirstSlot().getBlockHighestSection());
        assertEquals(17, sourceBlock2.getFirstSlot().getBlockHighestSection());
        entityManager.flush();
        entityManager.refresh(work);
        assertThat(work.getOperationIds()).hasSize(1);
        assertThat(work.getSampleSlotIds()).hasSize(4);
    }
}
