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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

/** Tests {@link OperationSolutionRepo} */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
class TestOperationSolutionRepo {
    @Autowired
    private SolutionRepo solRepo;
    @Autowired
    private OperationSolutionRepo opSolRepo;
    @Autowired
    private EntityCreator entityCreator;

    @Test
    @Transactional
    void testFind() {
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware lw = entityCreator.createLabware("STAN-1", lt, sample);
        User user = entityCreator.createUser("user1");
        OperationType opType = entityCreator.createOpType("opname", null, OperationTypeFlag.IN_PLACE);
        Operation[] ops = IntStream.range(0,3)
                .mapToObj(i -> entityCreator.simpleOp(opType, user, lw, lw))
                .toArray(Operation[]::new);
        List<Solution> sols = asList(solRepo.saveAll(IntStream.rangeClosed(101,102)
                .mapToObj(i -> new Solution(i, "Solution "+i))
                .toList()));
        List<OperationSolution> opsols = asList(opSolRepo.saveAll(List.of(
                new OperationSolution(ops[0].getId(), sols.get(0).getId(), lw.getId(), sample.getId()),
                new OperationSolution(ops[1].getId(), sols.get(1).getId(), lw.getId(), sample.getId())
        )));
        assertThat(opSolRepo.findAllByOperationId(ops[0].getId())).containsExactly(opsols.get(0));
        assertThat(opSolRepo.findAllByOperationId(ops[1].getId())).containsExactly(opsols.get(1));
        assertThat(opSolRepo.findAllByOperationId(ops[2].getId())).isEmpty();

        assertThat(opSolRepo.findAllByOperationIdIn(List.of(ops[0].getId(), ops[1].getId())))
                .containsExactlyInAnyOrderElementsOf(opsols);
        assertThat(opSolRepo.findAllByOperationIdIn(List.of(ops[1].getId(), ops[2].getId())))
                .containsExactly(opsols.get(1));

        assertThat(opSolRepo.findAllByLabwareIdIn(List.of(lw.getId())))
                .containsExactlyInAnyOrderElementsOf(opsols);
    }
}