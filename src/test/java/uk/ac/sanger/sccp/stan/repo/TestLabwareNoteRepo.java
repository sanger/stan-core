package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test {@link LabwareNoteRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestLabwareNoteRepo {
    @Autowired
    LabwareNoteRepo labwareNoteRepo;
    @Autowired
    OperationRepo opRepo;

    @Autowired
    EntityCreator entityCreator;

    @Test
    @Transactional
    public void testFindByOperationId() {
        OperationType opType = entityCreator.createOpType("Stir", null, OperationTypeFlag.IN_PLACE);
        User user = entityCreator.createUser("user1");
        Integer op1id = opRepo.save(new Operation(null, opType, null, null, user)).getId();
        Integer op2id = opRepo.save(new Operation(null, opType, null, null, user)).getId();

        final LabwareType lt1 = entityCreator.createLabwareType("lt1", 1, 1);
        Labware lw = entityCreator.createLabware("STAN-A1", lt1);

        LabwareNote note1 = labwareNoteRepo.save(new LabwareNote(null, lw.getId(), op1id, "Alpha", "Beta"));
        LabwareNote note2 = labwareNoteRepo.save(new LabwareNote(null, lw.getId(), op1id, "Alpha", "Gamma"));
        LabwareNote note3 = labwareNoteRepo.save(new LabwareNote(null, lw.getId(), op1id, "Delta", "Gamma"));

        LabwareNote note4 = labwareNoteRepo.save(new LabwareNote(null, lw.getId(), op2id, "Epsilon", "Zeta"));

        assertThat(labwareNoteRepo.findAllByOperationIdIn(List.of(-1))).isEmpty();
        assertThat(labwareNoteRepo.findAllByOperationIdIn(List.of(op1id))).containsExactlyInAnyOrder(note1, note2, note3);
        assertThat(labwareNoteRepo.findAllByOperationIdIn(List.of(op2id))).containsExactly(note4);
        assertThat(labwareNoteRepo.findAllByOperationIdIn(List.of(op1id, op2id))).containsExactlyInAnyOrder(note1, note2, note3, note4);
    }
}
