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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestRecordOpWithSlotComments {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private OperationCommentRepo opComRepo;
    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testOpWithSlotComments() throws Exception {
        OperationType opType = entityCreator.createOpType("Cleanup", null, OperationTypeFlag.IN_PLACE);
        Work work = entityCreator.createWork(null, null, null, null, null);
        Sample sample1 = entityCreator.createSample(null, 1);
        Sample sample2 = entityCreator.createSample(sample1.getTissue(), 2);
        Labware lw = entityCreator.createLabware("STAN-1", entityCreator.getTubeType(), sample1, sample2);

        tester.setUser(entityCreator.createUser("user1"));
        Object result = tester.post(tester.readGraphQL("opwithslotcomments.graphql")
                .replace("SGP1", work.getWorkNumber()));
        Map<String, ?> opData = chainGet(result, "data", "recordOpWithSlotComments", "operations", 0);
        Integer opId = (Integer) opData.get("id");
        assertNotNull(opId, "op id");
        assertEquals(opType.getName(), chainGet(opData, "operationType", "name"));

        Integer slotId = lw.getFirstSlot().getId();

        List<OperationComment> opComs = opComRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opComs).hasSize(2);
        OperationComment oc1 = opComs.stream().filter(oc -> oc.getSampleId().equals(sample1.getId())).findAny().orElseThrow();
        OperationComment oc2 = opComs.stream().filter(oc -> oc.getSampleId().equals(sample2.getId())).findAny().orElseThrow();
        Comment comment = oc1.getComment();
        assertEquals(1, comment.getId());
        assertEquals(new OperationComment(oc1.getId(), comment, opId, sample1.getId(), slotId, null), oc1);
        assertEquals(new OperationComment(oc2.getId(), comment, opId, sample2.getId(), slotId, null), oc2);

        entityManager.flush();
        entityManager.refresh(work);
        assertThat(work.getOperationIds()).contains(opId);
    }

}
