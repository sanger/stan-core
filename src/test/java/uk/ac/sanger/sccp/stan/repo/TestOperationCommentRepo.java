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

}
