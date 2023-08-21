package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.BeforeEach;
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
 * Tests {@link RoiRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestRoiRepo {
    @Autowired
    private RoiRepo roiRepo;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private OperationRepo opRepo;

    private SlotIdSampleIdOpId ids;

    @BeforeEach
    public void setup() {
        Sample sample = entityCreator.createSample(null, null);
        Slot slot = entityCreator.createTube("STAN-1").getFirstSlot();
        Operation op = createOp();
        ids = new SlotIdSampleIdOpId(slot.getId(), sample.getId(), op.getId());
    }

    private Operation createOp() {
        User user = entityCreator.createUser("user1");
        OperationType opType = entityCreator.createOpType("fizzle", null);
        Operation op = new Operation(null, opType, null, List.of(), user);
        return opRepo.save(op);
    }

    @Test
    @Transactional
    public void testLoadSave() {
        assertThat(roiRepo.findById(ids)).isEmpty();
        Roi roi = roiRepo.save(new Roi(ids.getSlotId(), ids.getSampleId(), ids.getOperationId(), "Hello"));
        assertThat(roiRepo.findById(ids)).contains(roi);
    }

    @Test
    @Transactional
    public void testFindAllByOperationIdIn() {
        assertThat(roiRepo.findAllByOperationIdIn(List.of(1,2,ids.getOperationId()))).isEmpty();
        Roi roi = roiRepo.save(new Roi(ids.getSlotId(), ids.getSampleId(), ids.getOperationId(), "Hello"));
        assertThat(roiRepo.findAllByOperationIdIn(List.of(1,2,ids.getOperationId()))).containsExactly(roi);
        assertThat(roiRepo.findAllByOperationIdIn(List.of(ids.getOperationId()+1))).isEmpty();
    }

}