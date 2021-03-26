package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link OperationRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestOperationRepo {
    @Autowired
    OperationRepo opRepo;
    @Autowired
    ActionRepo actionRepo;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    EntityManager entityManager;

    User user;

    @Test
    @Transactional
    public void testFindAllByOperationTypeAndSampleIdIn() {
        OperationType opType1 = entityCreator.createOpType("Alpha", null);
        OperationType opType2 = entityCreator.createOpType("Beta", null);

        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample[] samples = IntStream.range(1, 4)
                .mapToObj(i -> entityCreator.createSample(tissue, i))
                .toArray(Sample[]::new);

        LabwareType lt = entityCreator.createLabwareType("lt", 1, 1);
        Labware[] lw = IntStream.range(0, 4)
                .mapToObj(i -> {
                    Sample sample = samples[(i==3) ? 2 : i];
                    return entityCreator.createLabware("STAN-A"+i, lt, sample);
                })
                .toArray(Labware[]::new);

        Operation[] ops = {
                makeOp(opType1, lw[0]),
                makeOp(opType1, lw[1], lw[2]),
                makeOp(opType1, lw[2], lw[3]),
                makeOp(opType2, lw[1])
        };

        List<Operation> foundOps = opRepo.findAllByOperationTypeAndSampleIdIn(opType1, List.of(samples[1].getId(), samples[2].getId()));
        assertThat(foundOps).containsExactlyInAnyOrder(ops[1], ops[2]);
    }

    private Operation makeOp(OperationType opType, Labware... labware) {
        if (user==null) {
            user = entityCreator.createUser("user1");
        }
        Operation op = opRepo.save(new Operation(null, opType, null, null, user));
        List<Action> actions = Arrays.stream(labware)
                .flatMap(lw -> lw.getSlots().stream())
                .flatMap(slot -> slot.getSamples().stream()
                        .map(sam -> new Action(null, op.getId(), slot, slot, sam, sam)))
                .collect(Collectors.toList());
        actionRepo.saveAll(actions);
        entityManager.refresh(op);
        return op;
    }
}
