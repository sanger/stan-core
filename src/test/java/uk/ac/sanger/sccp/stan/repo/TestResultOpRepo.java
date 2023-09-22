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
import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * Tests {@link ResultOpRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestResultOpRepo {
    @Autowired
    private ResultOpRepo resOpRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private EntityCreator entityCreator;

    /**
     * Tests {@link ResultOpRepo#findAllByOperationIdIn} and {@link ResultOpRepo#findAllByRefersToOpIdIn}
     */
    @Test
    @Transactional
    public void testFindByOperationIds() {
        OperationType opType = entityCreator.createOpType("RecordResult", null, OperationTypeFlag.RESULT, OperationTypeFlag.IN_PLACE);
        User user = entityCreator.createUser("user1");
        Integer opId = opRepo.save(new Operation(null, opType, null, null, user)).getId();
        Integer referredOpId = opRepo.save(new Operation(null, opType, null, null, user)).getId();
        Integer sampleId = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "TISSUSE1"), 1, entityCreator.anyBioState()).getId();
        final Labware lw = entityCreator.createLabware("STAN-100", entityCreator.createLabwareType("LT", 2, 1));
        Integer slot1id = lw.getFirstSlot().getId();
        Integer slot2id = lw.getSlots().get(1).getId();
        List<ResultOp> resOps = newArrayList(resOpRepo.saveAll(List.of(
                new ResultOp(null, PassFail.pass, opId, sampleId, slot1id, referredOpId),
                new ResultOp(null, PassFail.fail, opId, sampleId, slot2id, referredOpId)
        )));

        assertThat(resOpRepo.findAllByOperationIdIn(List.of())).isEmpty();
        assertThat(resOpRepo.findAllByOperationIdIn(List.of(-4))).isEmpty();
        assertThat(resOpRepo.findAllByOperationIdIn(List.of(opId, -4))).containsExactlyInAnyOrderElementsOf(resOps);

        assertThat(resOpRepo.findAllByRefersToOpIdIn(List.of())).isEmpty();
        assertThat(resOpRepo.findAllByRefersToOpIdIn(List.of(-3))).isEmpty();
        assertThat(resOpRepo.findAllByRefersToOpIdIn(List.of(referredOpId, -3))).containsExactlyElementsOf(resOps);
    }

}
