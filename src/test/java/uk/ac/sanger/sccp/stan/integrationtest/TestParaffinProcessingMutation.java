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
import uk.ac.sanger.sccp.stan.repo.OperationCommentRepo;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the mutation performParaffinProcessing
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestParaffinProcessingMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OperationCommentRepo opComRepo;

    @Test
    @Transactional
    public void testParaffinProcessing() throws Exception {
        OperationType opType = entityCreator.createOpType("Paraffin processing", null, OperationTypeFlag.IN_PLACE);
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware lw = entityCreator.createLabware("STAN-A1", lt, sample);
        User user = entityCreator.createUser("user1");
        Work work = entityCreator.createWork(null, null, null, null, null);
        tester.setUser(user);
        String mutation = tester.readGraphQL("paraffinprocessing.graphql")
                .replace("WORK", work.getWorkNumber());
        Object result = tester.post(mutation);
        Object data = chainGet(result, "data", "performParaffinProcessing");
        assertEquals(lw.getBarcode(), chainGet(data, "labware", 0, "barcode"));
        Map<String, ?> opData = chainGet(data, "operations", 0);
        assertEquals(opType.getName(), chainGet(opData, "operationType", "name"));
        Integer opId = (Integer) opData.get("id");
        final List<OperationComment> opComments = opComRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opComments).hasSize(1);
        var opComment = opComments.getFirst();
        assertEquals(1, opComment.getComment().getId());
        entityManager.flush();
        Tissue tissue = sample.getTissue();
        entityManager.refresh(tissue);
        assertEquals("Paraffin", tissue.getMedium().getName());
        entityManager.refresh(lw.getFirstSlot());
        assertTrue(lw.getFirstSlot().isBlock()); // Block is created in the labware
    }
}
