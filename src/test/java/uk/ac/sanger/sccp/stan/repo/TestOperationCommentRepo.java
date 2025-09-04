package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link OperationCommentRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestOperationCommentRepo {
    @Autowired
    OperationRepo opRepo;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    OperationCommentRepo opCommentRepo;
    @Autowired
    CommentRepo commentRepo;

    @Test
    @Transactional
    public void testFindAllByOperationIdIn() {
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "EXT1");
        int sam1id = entityCreator.createSample(tissue, 1).getId();
        int sam2id = entityCreator.createSample(tissue, 2).getId();
        OperationType opType = entityCreator.createOpType("Frying", null);
        User user = entityCreator.createUser("dr6");
        int op1id = opRepo.save(new Operation(null, opType, null, List.of(), user)).getId();
        int op2id = opRepo.save(new Operation(null, opType, null, List.of(), user)).getId();
        Comment comment1 = commentRepo.save(new Comment(null, "Dropped", "Frying"));
        Comment comment2 = commentRepo.save(new Comment(null, "Burned", "Frying"));
        OperationComment oc1 = opCommentRepo.save(new OperationComment(null, comment1, op1id, sam1id, null, null));
        OperationComment oc2 = opCommentRepo.save(new OperationComment(null, comment2, op1id, sam2id, null, null));
        OperationComment oc3 = opCommentRepo.save(new OperationComment(null, comment1, op2id, sam1id, null, null));
        OperationComment oc4 = opCommentRepo.save(new OperationComment(null, comment2, op2id, null, null, null));

        assertThat(opCommentRepo.findAllByOperationIdIn(List.of(op1id, op2id))).containsExactlyInAnyOrder(oc1, oc2, oc3, oc4);
        assertThat(opCommentRepo.findAllByOperationIdIn(List.of(op1id))).containsExactlyInAnyOrder(oc1, oc2);
        assertThat(opCommentRepo.findAllByOperationIdIn(List.of(op1id+op2id))).isEmpty();
    }

    @Test
    @Transactional
    public void testFindAllBySlotIdAndOperationType() {
        Sample sample = entityCreator.createSample(null, 1);
        OperationType frying = entityCreator.createOpType("Frying", null);
        OperationType baking = entityCreator.createOpType("Baking", null);
        User user = entityCreator.createUser("dr6");
        int fryOp = opRepo.save(new Operation(null, frying, null, List.of(), user)).getId();
        int bakeOp = opRepo.save(new Operation(null, baking, null, List.of(), user)).getId();
        Comment com1 = commentRepo.save(new Comment(null, "fried", "Frying"));
        Comment com2 = commentRepo.save(new Comment(null, "dropped", "Frying"));
        LabwareType lt = entityCreator.getTubeType();
        Slot[] slots = IntStream.range(0,2)
                .mapToObj(i -> entityCreator.createLabware("STAN-"+i, lt, sample).getFirstSlot())
                .toArray(Slot[]::new);
        int[] slotIds = Arrays.stream(slots).mapToInt(Slot::getId).toArray();
        int sampleId = sample.getId();
        OperationComment oc1 = opCommentRepo.save(new OperationComment(null, com1, fryOp, sampleId, slotIds[0], null));
        OperationComment oc2 = opCommentRepo.save(new OperationComment(null, com2, fryOp, sampleId, slotIds[0], null));
        OperationComment oc3 = opCommentRepo.save(new OperationComment(null, com1, fryOp, sampleId, slotIds[1], null));
        OperationComment oc4 = opCommentRepo.save(new OperationComment(null, com1, bakeOp, sampleId, slotIds[0], null));

        assertThat(opCommentRepo.findAllBySlotAndOpType(List.of(slotIds[0]), frying)).containsExactlyInAnyOrder(oc1, oc2);
        assertThat(opCommentRepo.findAllBySlotAndOpType(List.of(slotIds[0], slotIds[1]), frying)).containsExactlyInAnyOrder(oc1, oc2, oc3);
        assertThat(opCommentRepo.findAllBySlotAndOpType(List.of(slotIds[0]), baking)).containsExactly(oc4);
    }

    @Test
    @Transactional
    void testFindAllBySlotIdInAndCommentCategory() {
        Sample sample = entityCreator.createSample(null, 1);
        Integer sampleId = sample.getId();
        OperationType opType = entityCreator.createOpType("Frying", null);
        User user = entityCreator.createUser("dr6");
        Integer opId = opRepo.save(new Operation(null, opType, null, List.of(), user)).getId();
        Comment com1 = commentRepo.save(new Comment(null, "Alabama", "A"));
        Comment com2 = commentRepo.save(new Comment(null, "Alaska", "A"));
        Comment com3 = commentRepo.save(new Comment(null, "Birmingham", "B"));
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 3);
        Labware lw = entityCreator.createLabware("STAN-A1", lt, sample, sample, sample);
        List<Slot> slots = lw.getSlots();
        Integer[] slotIds = slots.stream().map(Slot::getId).toArray(Integer[]::new);
        OperationComment oc1 = opCommentRepo.save(new OperationComment(null, com1, opId, sampleId, slotIds[0], null));
        OperationComment oc2 = opCommentRepo.save(new OperationComment(null, com2, opId, sampleId, slotIds[1], null));
        opCommentRepo.save(new OperationComment(null, com1, opId, sampleId, slotIds[2], null));
        opCommentRepo.save(new OperationComment(null, com3, opId, sampleId, slotIds[0], null));
        assertThat(opCommentRepo.findAllBySlotIdInAndCommentCategory(List.of(slotIds[0], slotIds[1]), "A"))
                .containsExactlyInAnyOrder(oc1, oc2);
    }

}
