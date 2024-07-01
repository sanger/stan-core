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
import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

/**
 * Test {@link LabwareProbeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
class TestLabwareProbeRepo {

    @Autowired
    ProbePanelRepo probeRepo;

    @Autowired
    LabwareProbeRepo lwProbeRepo;

    @Autowired
    EntityCreator entityCreator;

    @Test
    @Transactional
    void testFindAllBy_In() {
        OperationType opType = entityCreator.createOpType("opname", null, OperationTypeFlag.IN_PLACE);
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 1);
        Labware lw1 = entityCreator.createLabware("STAN-1", lt, sample);
        Labware lw2 = entityCreator.createLabware("STAN-2", lt, sample);
        User user = entityCreator.createUser("user1");
        Operation op1 = entityCreator.simpleOp(opType, user, lw1, lw1);
        Operation op2 = entityCreator.simpleOp(opType, user, lw2, lw2);
        ProbePanel panel1 = probeRepo.save(new ProbePanel("Banana"));
        ProbePanel panel2 = probeRepo.save(new ProbePanel("Custard"));

        List<LabwareProbe> lwProbes = asList(lwProbeRepo.saveAll(List.of(
                new LabwareProbe(null, panel1, op1.getId(), lw1.getId(), "lot1", 5, SlideCosting.SGP),
                new LabwareProbe(null, panel2, op2.getId(), lw1.getId(), "lot2", 6, SlideCosting.SGP),
                new LabwareProbe(null, panel2, op2.getId(), lw2.getId(), "lot3", 6, SlideCosting.Faculty)
        )));

        assertThat(lwProbeRepo.findAllByLabwareIdIn(List.of(lw1.getId()))).containsExactlyInAnyOrderElementsOf(lwProbes.subList(0, 2));
        assertThat(lwProbeRepo.findAllByLabwareIdIn(List.of(lw1.getId(), lw2.getId()))).containsExactlyInAnyOrderElementsOf(lwProbes);

        assertThat(lwProbeRepo.findAllByOperationIdIn(List.of(op1.getId()))).containsExactly(lwProbes.get(0));
        assertThat(lwProbeRepo.findAllByOperationIdIn(List.of(op1.getId(), op2.getId()))).containsExactlyInAnyOrderElementsOf(lwProbes);
    }
}