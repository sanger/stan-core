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
 * Tests {@link ActionRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestActionRepo {
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    ActionRepo actionRepo;
    @Autowired
    OperationRepo opRepo;

    @Transactional
    @Test
    public void testCustomMethods() {
        User user = entityCreator.createUser("user1");
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample sample = entityCreator.createSample(tissue, null);

        LabwareType lt = entityCreator.createLabwareType("lwtype", 1, 1);
        Labware lw1 = entityCreator.createLabware("STAN-01", lt, sample);
        Labware lw2 = entityCreator.createLabware("STAN-02", lt, sample);
        Slot slot1 = lw1.getFirstSlot();
        Slot slot2 = lw2.getFirstSlot();
        OperationType opType = entityCreator.createOpType("optype", null);
        Operation op = opRepo.save(new Operation(null, opType, null, null, user));
        Action action = actionRepo.save(new Action(null, op.getId(), slot1, slot2, sample, sample));

        assertThat(actionRepo.findAllByDestinationIn(List.of(slot1, slot2))).containsOnly(action);
        assertThat(actionRepo.findAllByDestinationIn(List.of(slot1))).isEmpty();
        assertThat(actionRepo.findAllByDestinationIn(List.of(slot2))).containsOnly(action);
        assertThat(actionRepo.findAllBySourceIn(List.of(slot1, slot2))).containsOnly(action);
        assertThat(actionRepo.findAllBySourceIn(List.of(slot2))).isEmpty();
        assertThat(actionRepo.findAllBySourceIn(List.of(slot1))).containsOnly(action);
        assertThat(actionRepo.findSourceLabwareIdsForDestinationLabwareIds(List.of(lw2.getId()))).containsExactly(lw1.getId());
        assertThat(actionRepo.findSourceLabwareIdsForDestinationLabwareIds(List.of(lw1.getId()))).isEmpty();
    }
}
