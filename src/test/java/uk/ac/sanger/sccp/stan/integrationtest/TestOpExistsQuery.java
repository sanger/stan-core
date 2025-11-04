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
import uk.ac.sanger.sccp.stan.repo.WorkRepo;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestOpExistsQuery {
    @Autowired
    private LabwareNoteRepo noteRepo;
    @Autowired
    private WorkRepo workRepo;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private GraphQLTester tester;

    @Test
    @Transactional
    public void testOpExists() throws Exception {
        OperationType opType = entityCreator.createOpType("opname", null, OperationTypeFlag.IN_PLACE);
        User user = entityCreator.createUser("user1");
        Sample sample = entityCreator.createSample(null, null);
        Labware lw = entityCreator.createLabware("STAN-1", entityCreator.getTubeType(), sample);
        Operation op = entityCreator.simpleOp(opType, user, lw, lw);
        noteRepo.save(new LabwareNote(null, lw.getId(), op.getId(), "run", "RUN1"));
        Work work = entityCreator.createWork(null, null, null, null, null);
        work.setOperationIds(new HashSet<>(Set.of(op.getId())));
        work = workRepo.save(work);

        String fullQuery = "query { opExists(barcode:\"STAN-1\", operationType:\"opname\", run:\"%s\", workNumber:\"%s\") }";

        assertTrue(queryResult("query { opExists(barcode:\"STAN-1\", operationType:\"opname\") }"));
        assertTrue(queryResult(String.format(fullQuery, "RUN1", work.getWorkNumber())));
        assertFalse(queryResult(String.format(fullQuery, "RUN1", "SGPX")));
        assertFalse(queryResult(String.format(fullQuery, "RUNX", work.getWorkNumber())));
    }

    private boolean queryResult(String query) throws Exception {
        return chainGet(tester.post(query), "data", "opExists");
    }
}
