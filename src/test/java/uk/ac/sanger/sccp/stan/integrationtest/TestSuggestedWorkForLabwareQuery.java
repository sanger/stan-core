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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the query for suggested work for labware
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestSuggestedWorkForLabwareQuery {
    @Autowired
    private WorkRepo workRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private ActionRepo actionRepo;
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Transactional
    @Test
    public void testSuggestedWork() throws Exception {
        Sample sample = entityCreator.createSample(null, null);
        Labware lw = entityCreator.createLabware("STAN-A1", entityCreator.getTubeType(), sample);
        final Slot slot = lw.getFirstSlot();
        OperationType opType = entityCreator.createOpType("Scrape", null);
        Operation op = opRepo.save(new Operation(null, opType, null, null, entityCreator.createUser("user1")));
        Action ac = new Action(null, op.getId(), slot, slot, sample, sample);
        actionRepo.save(ac);
        Work work = entityCreator.createWork(null, null, null, null, null);
        String query = tester.readGraphQL("suggestedwork.graphql").replace("BARCODE", lw.getBarcode());
        Object response1 = tester.post(query);
        entityManager.flush();
        assertNull(chainGet(response1, "data", "suggestedWorkForLabware"));
        work.setOperationIds(new ArrayList<>(List.of(op.getId())));
        workRepo.save(work);
        Object response2 = tester.post(query);
        assertEquals(work.getWorkNumber(), chainGet(response2, "data", "suggestedWorkForLabware", "workNumber"));
    }
}
