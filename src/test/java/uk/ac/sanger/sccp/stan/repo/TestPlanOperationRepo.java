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
 * Tests {@link PlanOperationRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestPlanOperationRepo {
    @Autowired
    EntityCreator entityCreator;

    @Autowired
    PlanOperationRepo planOpRepo;

    @Test
    @Transactional
    public void testFindAllByDestinationIdIn() {
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUE1");
        Labware block = entityCreator.createBlock("STAN-BLOCK", entityCreator.createSample(tissue, null));
        Slot source = block.getFirstSlot();
        LabwareType lt = entityCreator.createLabwareType("1x2", 1, 2);
        Labware lw1 = entityCreator.createLabware("STAN-1", lt);
        Labware lw2 = entityCreator.createLabware("STAN-2", lt);
        Labware lw3 = entityCreator.createLabware("STAN-3", lt);
        User user = entityCreator.createUser("user");
        OperationType opType = entityCreator.createOpType("Transmit", null);
        PlanOperation plan1 = entityCreator.createPlan(opType, user,
                source, lw1.getFirstSlot(), source, lw2.getFirstSlot(), source, lw1.getSlots().get(1),
                source, lw2.getSlots().get(1));
        PlanOperation plan2 = entityCreator.createPlan(opType, user,
                source, lw2.getFirstSlot());
        entityCreator.createPlan(opType, user, source, lw3.getFirstSlot()); // another plan that is not involved

        List<PlanOperation> plans = planOpRepo.findAllByDestinationIdIn(List.of(lw1.getId(), lw2.getId()));
        assertThat(plans).containsExactly(plan1, plan2);
    }
}
