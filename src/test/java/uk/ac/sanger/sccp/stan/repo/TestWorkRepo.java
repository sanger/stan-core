package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.Work.Status;

import javax.persistence.*;
import javax.transaction.Transactional;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link WorkRepo}
 * @author dr6
 * @see Work
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestWorkRepo {
    private final EntityManager entityManager;
    private final EntityCreator entityCreator;
    private final PlatformTransactionManager transactionManager;

    private final WorkRepo workRepo;

    private final OperationRepo opRepo;
    private final OperationTypeRepo opTypeRepo;

    private final UserRepo userRepo;

    private OperationType opType;
    private User user;

    @Autowired
    public TestWorkRepo(EntityManager entityManager, EntityCreator entityCreator,
                        PlatformTransactionManager transactionManager,
                        WorkRepo workRepo, OperationRepo opRepo, OperationTypeRepo opTypeRepo,
                        UserRepo userRepo) {
        this.entityManager = entityManager;
        this.entityCreator = entityCreator;
        this.transactionManager = transactionManager;
        this.workRepo = workRepo;
        this.opRepo = opRepo;
        this.opTypeRepo = opTypeRepo;
        this.userRepo = userRepo;
    }

    private User getUser() {
        if (user==null) {
            user = entityCreator.getAny(userRepo);
        }
        return user;
    }

    private int createOpId() {
        if (opType==null) {
            opType = entityCreator.getAny(opTypeRepo);
        }
        Operation op = new Operation(null, opType, null, List.of(), getUser());
        return opRepo.save(op).getId();
    }

    private List<Integer> createSampleIds(int number) {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        return IntStream.range(0, number)
                .mapToObj(i -> entityCreator.createSample(tissue, i==0 ? null : i).getId())
                .collect(toList());
    }

    private List<Integer> createSlotIds(int number) {
        LabwareType lt = entityCreator.createLabwareType("lt "+number, 1, number);
        Labware lw = entityCreator.createLabware("LW-100", lt);
        return lw.getSlots().stream().map(Slot::getId).collect(toList());
    }

    @Transactional
    @Test
    public void testGetByWorkNumber() {
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("S5000");
        WorkType workType = entityCreator.createWorkType("Drywalling");
        assertThrows(EntityNotFoundException.class, () -> workRepo.getByWorkNumber("SGP404"));
        Work work = workRepo.save(new Work(null, "SGP404", workType, pr, cc, Status.active));
        assertEquals(work, workRepo.getByWorkNumber("sgp404"));
    }

    @Transactional
    @Test
    public void testFindAllByWorkNumbersIn() {
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("S5000");
        WorkType workType = entityCreator.createWorkType("Drywalling");
        List<Work> newWorks = IntStream.range(0,3).mapToObj(n -> new Work(null, "SGP"+n, workType, pr, cc, Status.active))
                .collect(toList());
        var saved = workRepo.saveAll(newWorks);
        List<Work> loaded = workRepo.findAllByWorkNumberIn(List.of("SGP0", "SGP1", "SGP2", "SGP404"));
        assertThat(loaded).containsExactlyInAnyOrderElementsOf(saved);
    }

    @Transactional
    @Test
    public void testFindAllByStatusIn() {
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("S5000");
        WorkType type = entityCreator.createWorkType("Drywalling");
        Map<Status, Work> works = new EnumMap<>(Status.class);
        final Status[] statuses = Status.values();
        int n = 7000;
        for (Status st : statuses) {
            works.put(st, workRepo.save(new Work(null, "SGP" + n, type, pr, cc, st)));
            ++n;
        }
        for (Status st : statuses) {
            assertThat(workRepo.findAllByStatusIn(List.of(st))).containsExactly(works.get(st));
        }
        assertThat(workRepo.findAllByStatusIn(List.of(Status.active, Status.paused)))
                .containsExactlyInAnyOrder(works.get(Status.active), works.get(Status.paused));
    }

    @Transactional
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    // 0: valid foreign keys
    // 1: invalid op ids
    // 2: invalid sample ids
    // 3: invalid slot ids
    // All except case 0 should raise an exception.
    public void testEmbeddedCollections(int problem) {
        CostCode cc = entityCreator.createCostCode("S1000");
        Project project = entityCreator.createProject("Stargate");
        WorkType workType = entityCreator.createWorkType("Drywalling");
        Work work = workRepo.save(new Work(null, "SGP123", workType, project, cc, Status.active));
        assertNotNull(work.getId());
        List<Integer> opIds;
        if (problem==1) {
            opIds = List.of(404);
        } else {
            opIds = IntStream.range(0,2).mapToObj(i -> createOpId()).collect(toList());
        }
        work.setOperationIds(opIds);
        List<Integer> sampleIds = (problem==2 ? List.of(404, 405) : createSampleIds(2));
        List<Integer> slotIds = (problem==3 ? List.of(404, 405) : createSlotIds(2));
        List<SampleSlotId> sampleSlotIds = List.of(
                new SampleSlotId(sampleIds.get(0), slotIds.get(0)),
                new SampleSlotId(sampleIds.get(0), slotIds.get(1)),
                new SampleSlotId(sampleIds.get(1), slotIds.get(1))
        );
        work.setSampleSlotIds(sampleSlotIds);
        workRepo.save(work);
        if (problem==0) {
            entityManager.flush();
            entityManager.refresh(work);
            assertThat(work.getOperationIds()).containsExactlyInAnyOrderElementsOf(opIds);
            assertThat(work.getSampleSlotIds()).containsExactlyInAnyOrderElementsOf(sampleSlotIds);
        } else {
            assertThrows(PersistenceException.class, entityManager::flush);
        }
    }

    @Test
    public void testGetPrefixes() {
        assertThat(workRepo.getPrefixes()).containsExactlyInAnyOrder("SGP", "R&D");
    }

    @Transactional
    @Test
    public void testCreateNumber() {
        String a = workRepo.createNumber("SGP");
        String b = workRepo.createNumber("SGP");
        String c = workRepo.createNumber("R&D");
        assertNotEquals(a, b);
        assertThat(a).matches("SGP\\d{1,}");
        assertThat(b).matches("SGP\\d{1,}");
        assertThat(c).matches("R&D\\d{2,}");
    }

    @Transactional
    @Test
    public void testFindWorkNumbersForOpIds() {
        Work work1 = entityCreator.createWork(null, null, null);
        Work work2 = entityCreator.createWork(work1.getWorkType(), work1.getProject(), work1.getCostCode());
        String workNum1 = work1.getWorkNumber();
        String workNum2 = work2.getWorkNumber();
        int[] opIds = IntStream.range(0,4).map(i -> createOpId()).toArray();
        List<Integer> opIdList = Arrays.stream(opIds).boxed().collect(toList());
        Map<Integer, Set<String>> workMap = workRepo.findWorkNumbersForOpIds(opIdList);
        assertThat(workMap).hasSameSizeAs(opIds);
        for (int opId : opIds) {
            assertThat(workMap.get(opId)).isEmpty();
        }
        work1.setOperationIds(List.of(opIds[0], opIds[1]));
        work2.setOperationIds(List.of(opIds[1], opIds[2]));
        workRepo.saveAll(List.of(work1, work2));
        entityManager.flush();

        workMap = workRepo.findWorkNumbersForOpIds(List.of(opIds[3]));
        assertThat(workMap).hasSize(1);
        assertThat(workMap.get(opIds[3])).isEmpty();

        workMap = workRepo.findWorkNumbersForOpIds(opIdList);

        assertThat(workMap).hasSameSizeAs(opIdList);
        assertThat(workMap.get(opIds[0])).containsExactly(workNum1);
        assertThat(workMap.get(opIds[1])).containsExactlyInAnyOrder(workNum1, workNum2);
        assertThat(workMap.get(opIds[2])).containsExactly(workNum2);
        assertThat(workMap.get(opIds[3])).isEmpty();
    }

    @SuppressWarnings("BusyWait")
    @Test
    public void testCreateNumberIsolation() throws InterruptedException {
        final Supplier<String> supplier = () -> workRepo.createNumber("SGP");
        final WorkThread th1 = new WorkThread(transactionManager, supplier);
        final WorkThread th2 = new WorkThread(transactionManager, supplier);
        th1.start();
        while (th1.stage < 2) {
            Thread.sleep(10L);
        }
        // Thread 1 has created a work number and has an open transaction
        th2.start();
        while (th2.stage < 1) {
            Thread.sleep(10L);
        }
        // Thread 2 is trying to create a work number and has an open transaction
        th1.stage = 3; // let thread 1 finish
        while (th2.stage < 2) {
            // wait for thread 2 to read its value
            Thread.sleep(10L);
        }
        th2.stage = 3; // let thread 2 finish
        th1.join();
        th2.join();
        String workNumber1 = th1.value;
        String workNumber2 = th2.value;
        assertNotEquals(workNumber1, workNumber2); // This is the main point of the test
        assertNotNull(workNumber1);
        assertNotNull(workNumber2);
        assertThat(workNumber1).matches("SGP\\d{1,}");
        assertThat(workNumber2).matches("SGP\\d{1,}");
    }

    private static class WorkThread extends Thread {
        final Supplier<String> workNumberSupplier;
        final PlatformTransactionManager ptm;
        volatile int stage = 0;
        volatile String value;

        WorkThread(PlatformTransactionManager ptm, Supplier<String> workNumberSupplier) {
            this.ptm = ptm;
            this.workNumberSupplier = workNumberSupplier;
        }

        @SuppressWarnings("BusyWait")
        @Override
        public void run() {
            try {
                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                TransactionStatus status = ptm.getTransaction(def);
                stage = 1; // Stage 1: opened a transaction
                value = workNumberSupplier.get();
                stage = 2; // Stage 2: read the value
                while (stage < 3) {
                    // Hold the transaction open until stage is 3
                    Thread.sleep(10L);
                }
                ptm.commit(status);
                stage = 4; // transaction finished
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
