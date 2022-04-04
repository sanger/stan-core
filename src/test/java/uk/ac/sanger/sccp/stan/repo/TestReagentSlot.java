package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ReagentSlotRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import({EntityCreator.class})
public class TestReagentSlot {
    @Autowired
    ReagentSlotRepo reagentSlotRepo;
    @Autowired
    ReagentPlateRepo reagentPlateRepo;
    @Autowired
    ReagentActionRepo reagentActionRepo;
    @Autowired
    OperationRepo opRepo;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    public void testReagentSlot() {
        ReagentPlate plate = reagentPlateRepo.save(new ReagentPlate(null, "123", null));
        List<ReagentSlot> rslots = Address.stream(2,3)
                .map(ad -> new ReagentSlot(null, plate.getId(), ad, false))
                .collect(Collectors.toList());
        rslots = BasicUtils.asList(reagentSlotRepo.saveAll(rslots));
        plate.setSlots(rslots);
        for (ReagentSlot slot : rslots) {
            assertFalse(slot.isUsed());
        }
        OperationType opType = entityCreator.createOpType("Foo", null);
        User user = entityCreator.createUser("user1");
        Operation op = opRepo.save(new Operation(null, opType, null, null, user));
        ReagentSlot rslot = rslots.get(0);
        Labware lw = entityCreator.createTube("STAN-A");
        reagentActionRepo.save(new ReagentAction(null, op.getId(), rslot, lw.getFirstSlot()));
        // Note that the reagentAction.reagentSlot() needs refreshing before it will show as used
        entityManager.refresh(rslot);
        assertTrue(rslot.isUsed());
        ReagentSlot rslot2 = rslots.get(1);
        entityManager.refresh(rslot2);
        assertFalse(rslot2.isUsed());
    }
}
