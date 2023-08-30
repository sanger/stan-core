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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestQCLabwareMutation {
    @Autowired
    GraphQLTester tester;
    @Autowired
    EntityCreator entityCreator;

    @Autowired
    OperationCommentRepo opComRepo;

    @Transactional
    @Test
    public void testRecordQCLabware() throws Exception {
        String mutation = tester.readGraphQL("recordqclabware.graphql");
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        entityCreator.createOpType("fizzle", null, OperationTypeFlag.IN_PLACE);
        Work work = entityCreator.createWork(null, null, null, null, null);
        mutation = mutation.replace("SGP1", work.getWorkNumber());
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware lw = entityCreator.createLabware("STAN-1", lt, sample);
        Object result = tester.post(mutation);
        Integer opId = chainGet(result, "data", "recordQCLabware", "operations", 0, "id");
        assertNotNull(opId);
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opcoms).hasSize(1);
        OperationComment opcom = opcoms.get(0);
        assertEquals(1, opcom.getComment().getId());
        assertEquals(opId, opcom.getOperationId());
        assertEquals(lw.getId(), opcom.getLabwareId());
    }
}
