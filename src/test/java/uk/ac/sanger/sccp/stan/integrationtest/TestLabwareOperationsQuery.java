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
import uk.ac.sanger.sccp.stan.repo.OperationTypeRepo;
import uk.ac.sanger.sccp.stan.service.StainServiceImp;

import javax.transaction.Transactional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the labwareOperations query
 * @author bt8
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestLabwareOperationsQuery {

    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private StainServiceImp stainServiceImp;
    @Autowired
    private OperationTypeRepo operationTypeRepo;

    @Test
    @Transactional
    public void testLabwareOperations() throws Exception {
        LabwareType lt = entityCreator.createLabwareType("pair", 2, 1);
        Sample sample1 = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DNR1"), "EXT1"), 25);
        Sample sample2 = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DNR2"), "EXT1"), 26);
        Labware lw1 = entityCreator.createLabware("STAN-100", lt, sample1);
        Labware lw2 = entityCreator.createLabware("STAN-101", lt, sample2);


        User user = entityCreator.createUser("user1");
        List<StainType> sts = List.of(
                new StainType(1, "Coffee"),
                new StainType(2, "Blood"));
        OperationType opType = operationTypeRepo.getByName("Stain");
        Operation op = stainServiceImp.createOperation(user, lw1, opType, sts);

        // Checks stained labware returns the labware's stain operations
        Object data = tester.post(tester.readGraphQL("labwareOperations.graphql"));
        Object labwareOperationsData = chainGet(data, "data", "labwareOperations", 0);
        assertEquals(op.getOperationType().toString(), chainGet(labwareOperationsData, "operationType", "name"));

        // Checks unstained labware returns empty list
        data = tester.post(tester.readGraphQL("labwareOperations.graphql").replace("STAN-100", "STAN-101"));
        assertEquals(List.of(), chainGet(data, "data", "labwareOperations"));
    }
}