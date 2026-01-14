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
import uk.ac.sanger.sccp.stan.repo.LabwareNoteRepo;
import uk.ac.sanger.sccp.stan.repo.OperationCommentRepo;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.*;

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
    private OperationCommentRepo opComRepo;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private LabwareNoteRepo lwNoteRepo;

    @Test
    @Transactional
    public void testPlanAndRecordSection() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        entityCreator.createLabwareType(LabwareType.FETAL_WASTE_NAME, 1, 1);
        entityCreator.createBioState("Fetal waste");

        Sample[] blockSamples = {
                entityCreator.createBlockSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1")),
                entityCreator.createBlockSample(entityCreator.createTissue(entityCreator.createDonor("DONOR2"), "TISSUE2")),
        };
        Labware[] sourceBlocks = {
                entityCreator.createTube("STAN-B70C", blockSamples[0]),
                entityCreator.createTube("STAN-B70D", blockSamples[1]),
        };

        // Recording the plan

        String mutation = tester.readGraphQL("plan.graphql");
        mutation = mutation.replace("55555", String.valueOf(blockSamples[0].getId()));
        mutation = mutation.replace("55556", String.valueOf(blockSamples[1].getId()));
        Map<String, ?> result = tester.post(mutation);
        assertNoErrors(result);
        Object resultPlan = chainGet(result, "data", "plan");
        List<?> planResultLabware = chainGet(resultPlan, "labware");
        assertEquals(3, planResultLabware.size());
        String[] barcodes = IntStream.range(0,3)
                .<String>mapToObj(i -> chainGet(planResultLabware, i, "barcode"))
                .toArray(String[]::new);
        for (String barcode : barcodes) {
            assertNotNull(barcode);
        }
        assertEquals("Visium TO", chainGet(planResultLabware, 0, "labwareType", "name"));
        for (int i = 1; i < 3; ++i) {
            assertEquals(LabwareType.FETAL_WASTE_NAME, chainGet(planResultLabware, i, "labwareType", "name"));
        }
        List<?> resultOps = chainGet(resultPlan, "operations");
        assertEquals(3, resultOps.size());

        for (int i = 0; i < 3; ++i) {
            assertEquals("Section", chainGet(resultOps, i, "operationType", "name"));
        }
        List<Map<String, ?>> resultActions = chainGet(resultOps, 0, "planActions");

        String[] expectedPlanDestAddresses = { "A1", "A2", "B2" };
        int[] expectedPlanSampleId = {blockSamples[0].getId(), blockSamples[0].getId(), blockSamples[1].getId()};
        assertEquals(expectedPlanDestAddresses.length, resultActions.size());

        for (int i = 0; i < expectedPlanDestAddresses.length; ++i) {
            Map<String, ?> resultAction = resultActions.get(i);
            assertEquals("A1", chainGet(resultAction, "source", "address"));
            assertEquals(expectedPlanDestAddresses[i], chainGet(resultAction, "destination", "address"));
            assertEquals((Integer) expectedPlanSampleId[i], chainGet(resultAction, "sample", "id"));
            assertNotNull(chainGet(resultAction, "destination", "labwareId"));
            if (i == expectedPlanDestAddresses.length - 1) {
                assertEquals("2.5", resultAction.get("sampleThickness"));
            }
        }

        testRetrievePlanData(barcodes);

        testConfirm(blockSamples, sourceBlocks, barcodes);
    }

    private void testRetrievePlanData(String[] barcodes) throws Exception {
        String planQuery = tester.readGraphQL("plandata.graphql");
        Map<String, ?> result = tester.post(planQuery.replace("$BARCODE", barcodes[0]));

        Map<String, ?> planData = chainGet(result, "data", "planData");
        String[] sources = {"STAN-B70C", "STAN-B70D"};
        assertEquals("Section", chainGet(planData, "plan", "operationType", "name"));
        assertEquals(barcodes[0], chainGet(planData, "destination", "barcode"));
        assertThat(IntegrationTestUtils.<Map<String, String>>chainGetList(planData, "sources").stream()
                .map(m -> m.get("barcode"))
                .collect(toList())).containsExactlyInAnyOrder(sources);
        List<Map<String,?>> planActionsData = chainGet(planData, "plan", "planActions");
        assertThat(planActionsData).hasSize(3);
        assertEquals(List.of(List.of("A1", "A2"), List.of("B2")), planData.get("groups"));

        for (int i = 1; i < barcodes.length; ++i) {
            String barcode = barcodes[i]; // fetal waste barcode
            result = tester.post(planQuery.replace("$BARCODE", barcode));
            planData = chainGet(result, "data", "planData");
            assertEquals("Section", chainGet(planData, "plan", "operationType", "name"));
            assertEquals(barcode, chainGet(planData, "destination", "barcode"));
            assertEquals(LabwareType.FETAL_WASTE_NAME, chainGet(planData, "destination", "labwareType", "name"));
            assertThat(IntegrationTestUtils.<Map<String, String>>chainGetList(planData, "sources").stream()
                    .map(m -> m.get("barcode"))
                    .collect(toList())).containsExactlyInAnyOrder(sources[i-1]);
            planActionsData = chainGet(planData, "plan", "planActions");
            assertThat(planActionsData).hasSize(1);
        }
        assertEquals(List.of(List.of("A1")), planData.get("groups"));
    }

    private static List<Integer> sampleIdInSlot(List<?> slots, String address) {
        for (Object slot : slots) {
            if (chainGet(slot, "address").equals(address)) {
                return chainGetList(slot, "samples").stream()
                        .<Integer>map(sam -> chainGet(sam, "id"))
                        .toList();
            }
        }
        return null;
    }

    private void testConfirm(Sample[] blockSamples, Labware[] sourceBlocks, String[] barcodes) throws Exception {
        String barcode = barcodes[0];
        Work work = entityCreator.createWork(null, null, null, null, null);

        StringBuilder sb = new StringBuilder(tester.readGraphQL("confirmsection.graphql"));
        for (int i=0; i < 3; ++i) {
            sbReplace(sb, "$BARCODE"+i, barcodes[i]);
        }
        sbReplace(sb, "55555", String.valueOf(blockSamples[0].getId()));
        sbReplace(sb, "55556", String.valueOf(blockSamples[1].getId()));
        sbReplace(sb, "SGP4000", work.getWorkNumber());
        Map<String, ?> result = tester.post(sb.toString());
        assertNoErrors(result);

        Object resultConfirm = chainGet(result, "data", "confirmSection");
        List<?> resultLabware = chainGet(resultConfirm, "labware");
        assertEquals(3, resultLabware.size());
        assertEquals(barcode, chainGet(resultLabware, 0, "barcode"));
        List<?> slots = chainGet(resultLabware, 0, "slots");
        assertEquals(8, slots.size());

        List<Map<String, ?>> a1Samples = chainGetList(slots.stream()
                .filter(sd -> chainGet(sd, "address").equals("A1"))
                .findAny()
                .orElseThrow(), "samples");

        int[] expectedSecNum = { 14 };

        assertEquals(expectedSecNum.length, a1Samples.size());
        for (int i = 0; i < expectedSecNum.length; ++i) {
            Map<String, ?> sam = a1Samples.get(i);
            assertEquals("TISSUE1", chainGet(sam, "tissue", "externalName"));
            assertEquals(expectedSecNum[i], (int) sam.get("section"));
            assertEquals("Tissue", chainGet(sam, "bioState", "name"));
        }

        // A1 and A2 should contain the same sample
        List<Integer> a1SampleIds = sampleIdInSlot(slots, "A1");
        assertThat(a1SampleIds).hasSize(1);
        List<Integer> a2SampleIds = sampleIdInSlot(slots, "A2");
        assertEquals(a1SampleIds, a2SampleIds);
        // B2 should contain a different sample
        List<Integer> b2SampleIds = sampleIdInSlot(slots, "B2");
        assertThat(b2SampleIds).hasSize(1);
        assertNotEquals(a1SampleIds, b2SampleIds);

        List<Map<String, ?>> b2Samples = chainGetList(slots.stream()
                .filter(sd -> chainGet(sd, "address").equals("B2"))
                .findAny()
                .orElseThrow(), "samples");

        assertEquals(1, b2Samples.size());
        Map<String, ?> sam = b2Samples.get(0);
        assertEquals("TISSUE2", chainGet(sam, "tissue", "externalName"));
        assertEquals(17, (int) sam.get("section"));

        List<Map<String, ?>> resultOps = chainGet(resultConfirm, "operations");
        assertEquals(3, resultOps.size());
        Map<String, ?> resultOp = resultOps.get(0);
        assertNotNull(chainGet(resultOp, "performed"));
        assertEquals("Section", chainGet(resultOp, "operationType", "name"));
        List<Map<String,?>> actions = chainGet(resultOp, "actions");
        int[] expectedSourceLabwareIds = {sourceBlocks[0].getId(), sourceBlocks[0].getId(), sourceBlocks[1].getId()};
        String[] expectedDestAddress = { "A1", "A2", "B2" };
        String[] expectedActionTissues = { "TISSUE1",  "TISSUE1", "TISSUE2" };
        int[] expectedActionSecNum = { 14,14,17 };

        assertEquals(expectedSourceLabwareIds.length, actions.size());
        int destLabwareId = -1;
        List<Integer> opIds = resultOps.stream()
                .map(ro -> (Integer) ro.get("id"))
                .collect(toList());
        List<Integer> sampleIds = new ArrayList<>(expectedSourceLabwareIds.length);
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
            sampleIds.add(chainGet(action, "sample", "id"));
        }

        // Check the fetal waste tubes:
        for (int i = 1; i < 3; ++i) {
            assertEquals(barcodes[i], chainGet(resultLabware, i, "barcode"));
            Integer labwareId = chainGet(resultLabware, i, "id");
            assertNotNull(labwareId);
            List<Map<String, ?>> slotsData = chainGet(resultLabware, i, "slots");
            assertThat(slotsData).hasSize(1);
            Map<String, ?> slotData = slotsData.get(0);
            assertEquals("A1", slotData.get("address"));
            assertThat((List<?>) slotData.get("samples")).hasSize(1);
            Map<String, ?> samData = chainGet(slotData, "samples", 0);
            assertThat(samData).containsKey("section");
            assertNull(samData.get("section"));
            assertEquals("Fetal waste", chainGet(samData, "bioState", "name"));
            assertEquals("TISSUE"+i, chainGet(samData, "tissue", "externalName"));

            resultOp = resultOps.get(i);
            assertNotNull(resultOp.get("performed"));
            assertEquals("Section", chainGet(resultOp, "operationType", "name"));
            assertThat((List<?>) resultOp.get("actions")).hasSize(1);
            Map<String, ?> action = chainGet(resultOp, "actions", 0);
            assertEquals(sourceBlocks[i-1].getId(), chainGet(action, "source", "labwareId"));
            assertEquals("A1", chainGet(action, "source", "address"));
            assertEquals(labwareId, chainGet(action, "destination", "labwareId"));
            assertEquals("A1", chainGet(action, "destination", "address"));
            //noinspection unchecked
            sam = (Map<String, ?>) action.get("sample");
            assertThat(sam).containsKey("section");
            assertNull(sam.get("section"));
            assertEquals("Fetal waste", chainGet(sam, "bioState", "name"));
        }


        // Check that the source blocks' highest section numbers have been updated
        entityManager.refresh(sourceBlocks[0]);
        entityManager.refresh(sourceBlocks[1]);
        assertEquals(14, sourceBlocks[0].getFirstSlot().getSamples().getFirst().getBlockHighestSection());
        assertEquals(17, sourceBlocks[1].getFirstSlot().getSamples().getFirst().getBlockHighestSection());
        entityManager.flush();
        entityManager.refresh(work);
        assertThat(work.getOperationIds()).hasSize(3);
        assertThat(work.getSampleSlotIds()).hasSize(5);

        List<LabwareNote> notes = lwNoteRepo.findAllByOperationIdIn(opIds);
        assertThat(notes).hasSize(2);
        Map<String, String> noteMap = new HashMap<>(2);
        for (int ni = 0; ni < 2; ++ni) {
            LabwareNote note = notes.get(ni);
            assertEquals(opIds.get(0), note.getOperationId());
            assertEquals(destLabwareId, note.getLabwareId());
            noteMap.put(note.getName(), note.getValue());
        }
        assertEquals(Map.of("costing", "SGP", "lot", "1234567"), noteMap);

        Integer opId = opIds.get(0);
        final List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(opIds);
        Integer slotId = opcoms.get(0).getSlotId();
        assertThat(opcoms).hasSize(3);
        assertOpCom(opcoms.get(0), 2, opId, sampleIds.get(0), slotId);
    }

    private static void assertOpCom(OperationComment opcom, Integer commentId, Integer opId, Integer sampleId, Integer slotId) {
        assertEquals(commentId, opcom.getComment().getId());
        assertEquals(opId, opcom.getOperationId());
        assertEquals(sampleId, opcom.getSampleId());
        assertEquals(slotId, opcom.getSlotId());
    }

    private static StringBuilder sbReplace(StringBuilder sb, String original, String replacement) {
        int i = 0;
        while ((i = sb.indexOf(original, i)) >= 0) {
            sb.replace(i, i + original.length(), replacement);
            i += replacement.length();
        }
        return sb;
    }
}
