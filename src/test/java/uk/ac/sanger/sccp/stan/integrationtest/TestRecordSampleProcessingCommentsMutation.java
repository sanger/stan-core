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

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Test recordSampleProcessingComments mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestRecordSampleProcessingCommentsMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private OperationCommentRepo opComRepo;

    @Transactional
    @Test
    public void testSampleProcessingCommentRequest() throws Exception {
        User user = entityCreator.createUser("user1");
        OperationType opType = entityCreator.createOpType("Add sample processing comments", null, OperationTypeFlag.IN_PLACE);
        tester.setUser(user);
        String mutation = tester.readGraphQL("recordSampleProcessingComments.graphql");
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware lw = entityCreator.createLabware("STAN-A1", lt, sample);
        Object result = tester.post(mutation);
        List<Map<String, ?>> opsData = chainGet(result, "data", "recordSampleProcessingComments", "operations");
        assertThat(opsData).hasSize(1);
        Map<String, ?> opData = opsData.get(0);
        assertEquals(opType.getName(), chainGet(opData, "operationType", "name"));
        Integer opId = (Integer) opData.get("id");
        assertNotNull(opId);
        List<OperationComment> opComs = opComRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opComs).hasSize(2);
        assertThat(opComs.stream().map(oc -> oc.getComment().getId())).containsExactlyInAnyOrder(1,2);
        for (OperationComment opCom : opComs) {
            assertEquals(opId, opCom.getOperationId());
            assertEquals(lw.getFirstSlot().getId(), opCom.getSlotId());
        }
    }
}
