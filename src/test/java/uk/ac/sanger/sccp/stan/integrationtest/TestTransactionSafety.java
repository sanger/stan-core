package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests thread safety adding op ids to a work in two simultaneous transactions in separate threads
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(EntityCreator.class)
public class TestTransactionSafety {
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private OperationTypeRepo opTypeRepo;
    @Autowired
    private ProjectRepo projectRepo;
    @Autowired
    private ProgramRepo programRepo;
    @Autowired
    private WorkRepo workRepo;
    @Autowired
    private CostCodeRepo costCodeRepo;
    @Autowired
    private WorkTypeRepo workTypeRepo;
    @Autowired
    private UserRepo userRepo;

    private Program newProgram;
    private Operation[] ops;

    @Autowired
    private Transactor transactor;

    private void setup() {
        Optional<Work> optWork = workRepo.findByWorkNumber("SGPX");
        if (optWork.isPresent()) {
            Work work = optWork.get();
            work.getOperationIds().clear();
            workRepo.save(work);
        } else {
            WorkType workType = entityCreator.getAny(workTypeRepo);
            Project project = entityCreator.getAny(projectRepo);
            newProgram = entityCreator.createProgram("prog1");
            CostCode costCode = entityCreator.getAny(costCodeRepo);
            Work work = new Work(null, "SGPX", workType, null, project, newProgram, costCode, Work.Status.active);
            workRepo.save(work);
        }
        OperationType opType = entityCreator.getAny(opTypeRepo);
        User user = entityCreator.getAny(userRepo);
        ops = new Operation[]{opRepo.save(new Operation(null, opType, null, List.of(), user)),
                opRepo.save(new Operation(null, opType, null, List.of(), user))};
    }

    @Test
    public void testWork() throws Exception {
        try {
            transact(this::setup);

            TestThread thread1 = new TestThread(ops[0].getId());
            TestThread thread2 = new TestThread(ops[1].getId());
            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();

            transact(this::checkOpIds);
        } finally {
            transact(this::cleanup);
        }
    }

    private void checkOpIds() {
        Work work = workRepo.getByWorkNumber("SGPX");
        assertThat(work.getOperationIds()).containsExactlyInAnyOrder(ops[0].getId(), ops[1].getId());
    }

    private void cleanup() {
        Work work = workRepo.findByWorkNumber("SGPX").orElse(null);
        if (work!=null) {
            work.getOperationIds().clear();
            workRepo.delete(work);
        }
        if (ops!=null) {
            for (Operation op : ops) {
                if (op!=null) {
                    opRepo.delete(op);
                }
            }
        }
        if (newProgram!=null) {
            programRepo.delete(newProgram);
        }
    }

    class TestThread extends Thread {
        private final int opId;
        public TestThread(int opId) {
            this.opId = opId;
        }

        @Override
        public void run() {
            transact(this::execute);
        }

        public void execute() {
            Work work = workRepo.getByWorkNumber("SGPX");
            Set<Integer> opIds = work.getOperationIds();
            opIds.add(this.opId);
            workRepo.save(work);
        }
    }

    private void transact(final Runnable runnable) {
        Supplier<Void> supplier = () -> { runnable.run(); return null; };
        transactor.transact("transaction safety test", supplier);
    }
}
