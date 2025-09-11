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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;
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

    OperationType opType1, opType2;
    Sample[] samples;
    Operation[] ops;
    Labware[] lws;

    private void setUpOps() {
        if (opType1==null) {
            opType1 = entityCreator.createOpType("Alpha", null);
        }
        if (opType2==null) {
            opType2 = entityCreator.createOpType("Beta", null);
        }

        if (samples==null) {
            Donor donor = entityCreator.createDonor("DONOR1");
            Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
            samples = IntStream.range(1, 4)
                    .mapToObj(i -> entityCreator.createSample(tissue, i))
                    .toArray(Sample[]::new);
        }

        if (ops==null) {
            LabwareType lt = entityCreator.createLabwareType("lt", 1, 1);
            lws = IntStream.range(0, 4)
                    .mapToObj(i -> {
                        Sample sample = samples[(i == 3) ? 2 : i];
                        return entityCreator.createLabware("STAN-A" + i, lt, sample);
                    })
                    .toArray(Labware[]::new);

            ops = new Operation[]{
                    makeOp(opType1, lws[0]),
                    makeOp(opType1, lws[1], lws[2]),
                    makeOp(opType1, lws[2], lws[3]),
                    makeOp(opType2, lws[1])
            };
        }
    }

    @Test
    @Transactional
    public void testFindAllByOperationTypeAndSampleIdIn() {
        setUpOps();
        List<Operation> foundOps = opRepo.findAllByOperationTypeAndSampleIdIn(opType1, List.of(samples[1].getId(), samples[2].getId()));
        assertThat(foundOps).containsExactlyInAnyOrder(ops[1], ops[2]);
    }

    @Test
    @Transactional
    public void testFindAllBySampleIdIn() {
        setUpOps();
        List<Operation> foundOps = opRepo.findAllBySampleIdIn(List.of(samples[1].getId(), samples[2].getId()));
        assertThat(foundOps).containsExactlyInAnyOrder(ops[1], ops[2], ops[3]);
    }

    @Test
    @Transactional
    public void testFindAllByOperationTypeAndDestinationLabwareId() {
        setUpOps();
        List<Operation> foundOps = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType1, List.of(lws[0].getId(), lws[1].getId()));
        assertThat(foundOps).containsExactlyInAnyOrder(ops[0], ops[1]);
    }

    @Test
    @Transactional
    public void testFindAllByOperationTypeAndDestinationSlotIdIn() {
        setUpOps();
        List<Operation> foundOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType1, List.of(lws[0].getFirstSlot().getId(), lws[1].getFirstSlot().getId()));
        assertThat(foundOps).containsExactlyInAnyOrder(ops[0], ops[1]);
    }

    @Test
    @Transactional
    public void testFindOpSlotSampleIds() {
        setUpOps();
        int op0id = ops[0].getId();
        int op1id = ops[1].getId();
        int[] slotIds = Arrays.stream(lws).mapToInt(lw -> lw.getFirstSlot().getId()).toArray();
        int[] sampleIds = Arrays.stream(samples).mapToInt(Sample::getId).toArray();
        List<Integer> opIds = List.of(op0id, op1id);
        Map<Integer, Set<SlotIdSampleId>> opSsids = opRepo.findOpSlotSampleIds(opIds);
        assertThat(opSsids.keySet()).containsExactlyInAnyOrderElementsOf(opIds);
        assertThat(opSsids.get(op0id)).containsExactly(
                new SlotIdSampleId(slotIds[0], sampleIds[0])
        );
        assertThat(opSsids.get(op1id)).containsExactlyInAnyOrder(
                new SlotIdSampleId(slotIds[1], sampleIds[1]),
                new SlotIdSampleId(slotIds[2], sampleIds[2])
        );
    }

    @Test
    @Transactional
    public void testFindEarliestPerformedIntoLabware() {
        setUpOps();
        Set<Integer> lwIds = Arrays.stream(lws).map(Labware::getId).collect(toSet());
        Map<Integer, LocalDateTime> map = opRepo.findEarliestPerformedIntoLabware(lwIds);
        assertThat(map.keySet()).containsExactlyInAnyOrderElementsOf(lwIds);
        map.values().forEach(dt -> assertThat(dt).isInstanceOf(LocalDateTime.class));
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
