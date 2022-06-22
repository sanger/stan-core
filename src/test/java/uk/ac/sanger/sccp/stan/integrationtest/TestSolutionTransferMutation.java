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

import javax.transaction.Transactional;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the performSolutionTransfer mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestSolutionTransferMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private SolutionRepo solutionRepo;
    @Autowired
    private OperationSolutionRepo opSolRepo;

    @Test
    @Transactional
    public void testSolutionTransfer() throws Exception {
        Sample sample = entityCreator.createSample(null, null);
        Labware lw = entityCreator.createLabware("STAN-A1", entityCreator.getTubeType(), sample);
        Work work = entityCreator.createWork(null, null, null);
        User user = entityCreator.createUser("user1");
        OperationType opType = entityCreator.createOpType("Solution transfer", null, OperationTypeFlag.IN_PLACE);
        Solution solution = solutionRepo.save(new Solution(null, "Columbo"));
        String mutation = tester.readGraphQL("solutiontransfer.graphql").replace("SGP1", work.getWorkNumber());
        tester.setUser(user);
        Object result = tester.post(mutation);
        Object data = chainGet(result, "data", "performSolutionTransfer");
        assertEquals(lw.getBarcode(), chainGet(data, "labware", 0, "barcode"));
        Map<String, ?> opData = chainGet(data, "operations", 0);
        Integer opId = (Integer) opData.get("id");
        assertNotNull(opId);
        assertEquals(opType.getName(), chainGet(opData, "operationType", "name"));

        List<OperationSolution> opSols = opSolRepo.findAllByOperationId(opId);
        assertThat(opSols).hasSize(1);
        OperationSolution opSol = opSols.get(0);
        assertEquals(opId, opSol.getOperationId());
        assertEquals(sample.getId(), opSol.getSampleId());
        assertEquals(lw.getId(), opSol.getLabwareId());
        assertEquals(solution.getId(), opSol.getSolutionId());
    }
}
