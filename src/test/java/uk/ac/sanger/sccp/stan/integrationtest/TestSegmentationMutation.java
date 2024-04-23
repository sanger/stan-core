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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the segmentation mutation.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestSegmentationMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSegmentation() throws Exception {
        LabwareType lt = entityCreator.createLabwareType("lt", 1,2);
        Tissue tissue = entityCreator.createTissue(null, "EXT1");
        Sample sam1 = entityCreator.createSample(tissue, null);
        Sample sam2 = entityCreator.createSample(tissue, null);
        Labware lw = entityCreator.createLabware("STAN-A1", lt, sam1, sam2);
        entityCreator.createOpType("Cell segmentation", null, OperationTypeFlag.IN_PLACE);
        User user = entityCreator.createUser("user1");

        Work work = entityCreator.createWork(null, null, null, null, null);

        String mutation = tester.readGraphQL("segmentation.graphql")
                .replace("[BC]", lw.getBarcode())
                .replace("[WORK]", work.getWorkNumber());

        tester.setUser(user);

        Object response = tester.post(mutation);
        Object data = chainGet(response, "data", "segmentation");
        assertEquals(lw.getBarcode(), chainGet(data, "labware", 0, "barcode"));
        Object opData = chainGet(data, "operations", 0);
        assertEquals("Cell segmentation", chainGet(opData, "operationType", "name"));
        Integer opId = chainGet(opData, "id");

        entityManager.flush();
        assertThat(work.getOperationIds()).containsExactly(opId);
        Slot slot1 = lw.getFirstSlot();
        Slot slot2 = lw.getSlot(new Address(1,2));
        assertThat(work.getSampleSlotIds()).containsExactlyInAnyOrder(
                new Work.SampleSlotId(sam1.getId(), slot1.getId()),
                new Work.SampleSlotId(sam2.getId(), slot2.getId())
        );

        testSegmentationQC(work, lw);
    }

    private void testSegmentationQC(Work work, Labware lw) throws Exception {
        OperationType opType = entityCreator.createOpType("Cell segmentation QC", null, OperationTypeFlag.IN_PLACE);
        String mutation = tester.readGraphQL("segmentationqc.graphql")
                .replace("[BC]", lw.getBarcode())
                .replace("[WORK]", work.getWorkNumber());

        Object response = tester.post(mutation);
        Object data = chainGet(response, "data", "segmentation");
        assertEquals(lw.getBarcode(), chainGet(data, "labware", 0, "barcode"));
        Object opData = chainGet(data, "operations", 0);
        assertEquals(opType.getName(), chainGet(opData, "operationType", "name"));
        Integer opId = chainGet(opData, "id");

        entityManager.flush();
        assertThat(work.getOperationIds()).contains(opId);
    }
}
