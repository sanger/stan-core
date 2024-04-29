package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.hashSetOf;

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
    private final ActionRepo actionRepo;
    private final OperationTypeRepo opTypeRepo;

    private final UserRepo userRepo;
    private final ReleaseRepo releaseRepo;

    private ReleaseDestination dest;
    private ReleaseRecipient rec;
    private OperationType opType;
    private User user;

    @Autowired
    public TestWorkRepo(EntityManager entityManager, EntityCreator entityCreator,
                        PlatformTransactionManager transactionManager,
                        WorkRepo workRepo, OperationRepo opRepo, ActionRepo actionRepo, OperationTypeRepo opTypeRepo,
                        UserRepo userRepo, ReleaseRepo releaseRepo) {
        this.entityManager = entityManager;
        this.entityCreator = entityCreator;
        this.transactionManager = transactionManager;
        this.workRepo = workRepo;
        this.opRepo = opRepo;
        this.actionRepo = actionRepo;
        this.opTypeRepo = opTypeRepo;
        this.userRepo = userRepo;
        this.releaseRepo = releaseRepo;
    }

    private User getUser() {
        if (user==null) {
            user = entityCreator.getAny(userRepo);
        }
        return user;
    }

    private OperationType getOpType() {
        if (opType==null) {
            opType = entityCreator.getAny(opTypeRepo);
        }
        return opType;
    }

    private int createOpId() {
        Operation op = new Operation(null, getOpType(), null, List.of(), getUser());
        return opRepo.save(op).getId();
    }

    private int createReleaseId(Labware lw) {
        if (dest==null) {
            dest = entityCreator.createReleaseDestination("Moon");
        }
        if (rec==null) {
            rec = entityCreator.createReleaseRecipient("uatu");
        }
        Snapshot snap = entityCreator.createSnapshot(lw);
        Release rel = new Release(lw, getUser(), dest, rec, snap.getId());
        return releaseRepo.save(rel).getId();
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
        Program prog = entityCreator.createProgram("Hello");
        CostCode cc = entityCreator.createCostCode("S5000");
        WorkType workType = entityCreator.createWorkType("Drywalling");
        ReleaseRecipient workRequester = entityCreator.createReleaseRecipient("test1");
        assertThrows(EntityNotFoundException.class, () -> workRepo.getByWorkNumber("SGP404"));
        Work work = workRepo.save(new Work(null, "SGP404", workType, workRequester, pr, prog, cc, Status.active));
        assertEquals(work, workRepo.getByWorkNumber("sgp404"));
    }

    @Transactional
    @Test
    public void testFindAllByWorkNumbersIn() {
        Project pr = entityCreator.createProject("Stargate");
        Program prog = entityCreator.createProgram("Hello");
        CostCode cc = entityCreator.createCostCode("S5000");
        WorkType workType = entityCreator.createWorkType("Drywalling");
        ReleaseRecipient workRequester = entityCreator.createReleaseRecipient("test1");
        List<Work> newWorks = IntStream.range(0,3).mapToObj(n -> new Work(null, "SGP"+n, workType, workRequester, pr, prog, cc, Status.active))
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
        Program prog = entityCreator.createProgram("Hello");
        ReleaseRecipient workRequester = entityCreator.createReleaseRecipient("test1");
        Map<Status, Work> works = new EnumMap<>(Status.class);
        final Status[] statuses = Status.values();
        int n = 7000;
        for (Status st : statuses) {
            works.put(st, workRepo.save(new Work(null, "SGP" + n, type, workRequester, pr, prog, cc, st)));
            ++n;
        }
        for (Status st : statuses) {
            assertThat(workRepo.findAllByStatusIn(List.of(st))).containsExactly(works.get(st));
        }
        assertThat(workRepo.findAllByStatusIn(List.of(Status.active, Status.paused)))
                .containsExactlyInAnyOrder(works.get(Status.active), works.get(Status.paused));
    }

    @Transactional
    @Test
    public void testFindAllByWorkTypeIn() {
        Project pr = entityCreator.createProject("Stargate");
        Program prog = entityCreator.createProgram("Hello");
        CostCode cc = entityCreator.createCostCode("S5000");
        WorkType type1 = entityCreator.createWorkType("Drywalling");
        WorkType type2 = entityCreator.createWorkType("Lasers");
        WorkType type3 = entityCreator.createWorkType("Death Star");
        ReleaseRecipient workRequester = entityCreator.createReleaseRecipient("test1");
        Status st = Status.active;
        Work work1 = workRepo.save(new Work(null, "SGP1", type1, workRequester, pr, prog, cc, st));
        Work work2 = workRepo.save(new Work(null, "SGP2", type2, workRequester, pr, prog, cc, st));
        Work work3 = workRepo.save(new Work(null, "SGP3", type1, workRequester, pr, prog, cc, st));
        assertThat(workRepo.findAllByWorkTypeIn(List.of(type1))).containsExactlyInAnyOrder(work1, work3);
        assertThat(workRepo.findAllByWorkTypeIn(List.of(type1, type2))).containsExactlyInAnyOrder(work1, work2, work3);
        assertThat(workRepo.findAllByWorkTypeIn(List.of(type3))).isEmpty();
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
        ReleaseRecipient workRequester = entityCreator.createReleaseRecipient("test1");
        Program prog = entityCreator.createProgram("Hello");
        Work work = workRepo.save(new Work(null, "SGP123", workType, workRequester, project, prog, cc, Status.active));
        assertNotNull(work.getId());
        Set<Integer> opIds;
        if (problem==1) {
            opIds = hashSetOf(404);
        } else {
            opIds = IntStream.range(0,2).mapToObj(i -> createOpId()).collect(toCollection(HashSet::new));
        }
        work.setOperationIds(opIds);
        List<Integer> sampleIds = (problem==2 ? List.of(404, 405) : createSampleIds(2));
        List<Integer> slotIds = (problem==3 ? List.of(404, 405) : createSlotIds(2));
        Set<SampleSlotId> sampleSlotIds = hashSetOf(
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
        assertThat(a).matches("SGP\\d+");
        assertThat(b).matches("SGP\\d+");
        assertThat(c).matches("R&D\\d+");
    }

    @Transactional
    @Test
    public void testFindLabwareIdsForWorkIds() {
        Work work1 = entityCreator.createWork(null, null, null, null, null);
        Work work2 = entityCreator.createWork(work1.getWorkType(), work1.getProject(), work1.getProgram(), work1.getCostCode(), work1.getWorkRequester());

        List<Integer> labwareIds = workRepo.findLabwareIdsForWorkIds(List.of(work1.getId(), work2.getId()));
        assertThat(labwareIds).isEmpty();

        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);

        Sample sample = entityCreator.createSample(null, null);
        final Integer sampleId = sample.getId();
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 2);
        Labware lw1 = entityCreator.createLabware("STAN-A1", lt, sample, sample);
        Labware lw2 = entityCreator.createLabware("STAN-A2", lt, sample, sample);
        work1.setSampleSlotIds(hashSetOf(new SampleSlotId(sampleId, lw1.getSlot(A1).getId()),
                new SampleSlotId(sampleId, lw1.getSlot(A2).getId()),
                new SampleSlotId(sampleId, lw2.getSlot(A1).getId())));
        workRepo.save(work1);
        work2.setSampleSlotIds(hashSetOf(new SampleSlotId(sampleId, lw2.getSlot(A2).getId())));
        workRepo.save(work2);

        labwareIds = workRepo.findLabwareIdsForWorkIds(List.of(work1.getId(), -1));
        assertThat(labwareIds).containsExactlyInAnyOrder(lw1.getId(), lw2.getId());

        labwareIds = workRepo.findLabwareIdsForWorkIds(List.of(work1.getId(), work2.getId()));
        assertThat(labwareIds).containsExactlyInAnyOrder(lw1.getId(), lw2.getId());

        labwareIds = workRepo.findLabwareIdsForWorkIds(List.of(work2.getId()));
        assertThat(labwareIds).containsExactlyInAnyOrder(lw2.getId());
    }

    @Transactional
    @Test
    public void testFindWorkForSampleIdAndSlotId() {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue1 = entityCreator.createTissue(donor, "TISSUE1");
        BioState bs = entityCreator.anyBioState();
        Tissue tissue2 = entityCreator.createTissue(donor, "TISSUE2", "2");
        Sample[] samples = {
                entityCreator.createSample(tissue1, 1, bs),
                entityCreator.createSample(tissue1, 2, bs),
                entityCreator.createSample(tissue2, 3, bs),
        };

        LabwareType lt1 = entityCreator.createLabwareType("lt1", 1, 1);

        Labware[] labware = {
                entityCreator.createLabware("STAN-01", lt1, samples[0]),
                entityCreator.createLabware("STAN-02", lt1, samples[1]),
                entityCreator.createLabware("STAN-03", lt1, samples[2]),
        };

        Work work1 = entityCreator.createWork(null, null, null, null, null);
        Work work2 = entityCreator.createWork(work1.getWorkType(), work1.getProject(), work1.getProgram(), work1.getCostCode(), work1.getWorkRequester());

        work1.setSampleSlotIds(Set.of(new Work.SampleSlotId(samples[0].getId(), labware[0].getSlots().get(0).getId())));
        work2.setSampleSlotIds(Set.of(
                new Work.SampleSlotId(samples[0].getId(), labware[0].getSlots().get(0).getId()),
                new Work.SampleSlotId(samples[1].getId(), labware[1].getSlots().get(0).getId())
        ));

        Set<Work> works = workRepo.findWorkForSampleIdAndSlotId(samples[0].getId(), labware[0].getSlots().get(0).getId());
        assertThat(works).containsExactlyInAnyOrder(work1, work2);

        works = workRepo.findWorkForSampleIdAndSlotId(samples[1].getId(), labware[1].getSlots().get(0).getId());
        assertThat(works).containsExactlyInAnyOrder(work2);

        works = workRepo.findWorkForSampleIdAndSlotId(samples[2].getId(), labware[2].getSlots().get(0).getId());
        assertThat(works).isEmpty();
    }

    @Transactional
    @Test
    public void testFindWorkNumbersForReleaseids() {
        Work work1 = entityCreator.createWork(null, null, null, null, null);
        Work work2 = entityCreator.createWork(work1.getWorkType(), work1.getProject(), work1.getProgram(), work1.getCostCode(), work1.getWorkRequester());
        String wn1 = work1.getWorkNumber();
        int[] rids = IntStream.range(0,3)
                .map(i -> createReleaseId(entityCreator.createTube("STAN-"+i)))
                .toArray();
        List<Integer> ridList = Arrays.stream(rids).boxed().collect(toList());
        Map<Integer, String> workMap = workRepo.findWorkNumbersForReleaseIds(ridList);
        assertNull(workMap.get(rids[0]));
        assertNull(workMap.get(rids[1]));
        work1.setReleaseIds(new HashSet<>(ridList.subList(0,2)));
        workRepo.saveAll(List.of(work1, work2));
        workMap = workRepo.findWorkNumbersForReleaseIds(ridList);
        assertEquals(wn1, workMap.get(rids[0]));
        assertEquals(wn1, workMap.get(rids[1]));
        assertNull(workMap.get(rids[2]));
    }

    @Transactional
    @Test
    public void testFindWorkNumbersForOpIds() {
        Work work1 = entityCreator.createWork(null, null, null, null, null);
        Work work2 = entityCreator.createWork(work1.getWorkType(), work1.getProject(), work1.getProgram(), work1.getCostCode(), work1.getWorkRequester());
        String workNum1 = work1.getWorkNumber();
        String workNum2 = work2.getWorkNumber();
        int[] opIds = IntStream.range(0,4).map(i -> createOpId()).toArray();
        List<Integer> opIdList = Arrays.stream(opIds).boxed().collect(toList());
        Map<Integer, Set<String>> workMap = workRepo.findWorkNumbersForOpIds(opIdList);
        assertThat(workMap).hasSameSizeAs(opIds);
        for (int opId : opIds) {
            assertThat(workMap.get(opId)).isEmpty();
        }
        work1.setOperationIds(hashSetOf(opIds[0], opIds[1]));
        work2.setOperationIds(hashSetOf(opIds[1], opIds[2]));
        workRepo.saveAll(List.of(work1, work2));

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

    @Transactional
    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "true,true,false", "true,false,true", "true,true,true"})
    public void testFindLatestActiveWorkIdForLabwareId(boolean exists, boolean latestIsActive, boolean includeInactive) {
        Sample sample = entityCreator.createSample(null, null);
        Labware lw = entityCreator.createLabware("STAN-A1", entityCreator.getTubeType(), sample);
        Work work1 = entityCreator.createWork(null, null, null, null, null);
        Work work2 = entityCreator.createWorkLike(work1);
        Work work3 = entityCreator.createWorkLike(work1);

        Operation[] ops = IntStream.rangeClosed(1,3).mapToObj(d -> saveOp(lw, day(d))).toArray(Operation[]::new);
        work1.setOperationIds(hashSetOf(ops[0].getId()));
        work2.setOperationIds(hashSetOf(ops[1].getId()));
        work3.setOperationIds(hashSetOf(ops[2].getId()));
        if (!exists) {
            work1.setStatus(Status.completed);
            work2.setStatus(Status.failed);
        }
        if (!latestIsActive) {
            work3.setStatus(Status.paused);
        }
        workRepo.saveAll(List.of(work1, work2, work3));

        final Integer workId = includeInactive ? workRepo.findLatestWorkIdForLabwareId(lw.getId())
                : workRepo.findLatestActiveWorkIdForLabwareId(lw.getId());
        assertEquals(exists && (latestIsActive || includeInactive) ? work3.getId() : null, workId);
    }

    private Operation saveOp(Labware lw, LocalDateTime performed) {
        Slot slot = lw.getFirstSlot();
        Sample sample = slot.getSamples().get(0);
        Operation op = opRepo.save(new Operation(null, getOpType(), null, null, getUser()));
        Action a = actionRepo.save(new Action(null, op.getId(), slot, slot, sample, sample));
        op.setActions(new ArrayList<>(List.of(a)));
        op.setPerformed(performed);
        entityManager.flush();
        return opRepo.save(op);
    }

    private static LocalDateTime day(int n) {
        return LocalDateTime.of(2023,1, n, 12, 0);
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
        assertThat(workNumber1).matches("SGP\\d+");
        assertThat(workNumber2).matches("SGP\\d+");
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
