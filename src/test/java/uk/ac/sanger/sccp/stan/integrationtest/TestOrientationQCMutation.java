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
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestOrientationQCMutation {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private LabwareNoteRepo noteRepo;

    @Transactional
    @Test
    public void testOrientationQC() throws Exception {
        Sample sample = entityCreator.createBlockSample(null);
        Labware lw = entityCreator.createTube("STAN-1", sample);
        Work work = entityCreator.createWork(null, null, null, null, null);

        OperationType opType = entityCreator.createOpType("Orientation QC", null,
                OperationTypeFlag.IN_PLACE, OperationTypeFlag.SOURCE_IS_BLOCK);

        User user = entityCreator.createUser("user1");

        String mutation = tester.readGraphQL("orientationqc.graphql")
                .replace("SGP1", work.getWorkNumber());
        tester.setUser(user);

        Object response = tester.post(mutation);

        Integer opId = chainGet(response, "data", "recordOrientationQC", "operations", 0, "id");
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals(opType, op.getOperationType());
        assertThat(op.getActions()).hasSize(1);
        Action action = op.getActions().get(0);
        Slot slot = lw.getFirstSlot();
        assertEquals(slot, action.getSource());
        assertEquals(slot, action.getDestination());
        assertEquals(sample, action.getSample());

        entityManager.flush();
        assertThat(work.getOperationIds()).contains(opId);

        List<LabwareNote> notes = noteRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(notes).hasSize(1);

        LabwareNote note = notes.get(0);
        assertEquals(lw.getId(), note.getLabwareId());
        assertThat(note.getName()).isEqualToIgnoringCase("orientation");
        assertThat(note.getValue()).isEqualToIgnoringCase("correct");

    }
}
