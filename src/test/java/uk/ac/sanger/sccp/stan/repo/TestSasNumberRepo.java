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
import uk.ac.sanger.sccp.stan.model.SasNumber.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;

import javax.persistence.*;
import javax.transaction.Transactional;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link SasNumberRepo}
 * @author dr6
 * @see SasNumber
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestSasNumberRepo {
    private final EntityManager entityManager;
    private final EntityCreator entityCreator;
    private final PlatformTransactionManager transactionManager;

    private final SasNumberRepo sasRepo;

    private final OperationRepo opRepo;
    private final OperationTypeRepo opTypeRepo;

    private final UserRepo userRepo;

    private OperationType opType;
    private User user;

    @Autowired
    public TestSasNumberRepo(EntityManager entityManager, EntityCreator entityCreator,
                             PlatformTransactionManager transactionManager,
                             SasNumberRepo sasRepo, OperationRepo opRepo, OperationTypeRepo opTypeRepo,
                             UserRepo userRepo) {
        this.entityManager = entityManager;
        this.entityCreator = entityCreator;
        this.transactionManager = transactionManager;
        this.sasRepo = sasRepo;
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
    public void testGetBySasNumber() {
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("S5000");
        SasType sasType = entityCreator.createSasType("Drywalling");
        assertThrows(EntityNotFoundException.class, () -> sasRepo.getBySasNumber("SAS404"));
        SasNumber sas = sasRepo.save(new SasNumber(null, "SAS404", sasType, pr, cc, Status.active));
        assertEquals(sas, sasRepo.getBySasNumber("sas404"));
    }

    @Transactional
    @Test
    public void testFindAllByStatusIn() {
        Project pr = entityCreator.createProject("Stargate");
        CostCode cc = entityCreator.createCostCode("S5000");
        SasType type = entityCreator.createSasType("Drywalling");
        Map<Status, SasNumber> sases = new EnumMap<>(Status.class);
        final Status[] statuses = Status.values();
        int n = 7000;
        for (Status st : statuses) {
            sases.put(st, sasRepo.save(new SasNumber(null, "SAS" + n, type, pr, cc, st)));
            ++n;
        }
        for (Status st : statuses) {
            assertThat(sasRepo.findAllByStatusIn(List.of(st))).containsExactly(sases.get(st));
        }
        assertThat(sasRepo.findAllByStatusIn(List.of(Status.active, Status.paused)))
                .containsExactlyInAnyOrder(sases.get(Status.active), sases.get(Status.paused));
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
        SasType sasType = entityCreator.createSasType("Drywalling");
        SasNumber sas = sasRepo.save(new SasNumber(null, "SAS123", sasType, project, cc, Status.active));
        assertNotNull(sas.getId());
        List<Integer> opIds;
        if (problem==1) {
            opIds = List.of(404);
        } else {
            opIds = IntStream.range(0,2).mapToObj(i -> createOpId()).collect(toList());
        }
        sas.setOperationIds(opIds);
        List<Integer> sampleIds = (problem==2 ? List.of(404, 405) : createSampleIds(2));
        List<Integer> slotIds = (problem==3 ? List.of(404, 405) : createSlotIds(2));
        List<SampleSlotId> sampleSlotIds = List.of(
                new SampleSlotId(sampleIds.get(0), slotIds.get(0)),
                new SampleSlotId(sampleIds.get(0), slotIds.get(1)),
                new SampleSlotId(sampleIds.get(1), slotIds.get(1))
        );
        sas.setSampleSlotIds(sampleSlotIds);
        sasRepo.save(sas);
        if (problem==0) {
            entityManager.flush();
            entityManager.refresh(sas);
            assertThat(sas.getOperationIds()).containsExactlyInAnyOrderElementsOf(opIds);
            assertThat(sas.getSampleSlotIds()).containsExactlyInAnyOrderElementsOf(sampleSlotIds);
        } else {
            assertThrows(PersistenceException.class, entityManager::flush);
        }
    }

    @Test
    public void testGetPrefixes() {
        assertThat(sasRepo.getPrefixes()).containsExactlyInAnyOrder("SAS", "R&D");
    }

    @Transactional
    @Test
    public void testCreateNumber() {
        String a = sasRepo.createNumber("SAS");
        String b = sasRepo.createNumber("SAS");
        String c = sasRepo.createNumber("R&D");
        assertNotEquals(a, b);
        assertThat(a).matches("SAS\\d{4,}");
        assertThat(b).matches("SAS\\d{4,}");
        assertThat(c).matches("R&D\\d{2,}");
    }

    @SuppressWarnings("BusyWait")
    @Test
    public void testCreateNumberIsolation() throws InterruptedException {
        final Supplier<String> supplier = () -> sasRepo.createNumber("SAS");
        final SasThread th1 = new SasThread(transactionManager, supplier);
        final SasThread th2 = new SasThread(transactionManager, supplier);
        th1.start();
        while (th1.stage < 2) {
            Thread.sleep(10L);
        }
        // Thread 1 has created an SAS number and has an open transaction
        th2.start();
        while (th2.stage < 1) {
            Thread.sleep(10L);
        }
        // Thread 2 is trying to create an SAS number and has an open transaction
        th1.stage = 3; // let thread 1 finish
        while (th2.stage < 2) {
            // wait for thread 2 to read its value
            Thread.sleep(10L);
        }
        th2.stage = 3; // let thread 2 finish
        th1.join();
        th2.join();
        String sas1 = th1.value;
        String sas2 = th2.value;
        assertNotEquals(sas1, sas2); // This is the main point of the test
        assertNotNull(sas1);
        assertNotNull(sas2);
        assertThat(sas1).matches("SAS\\d{4,}");
        assertThat(sas2).matches("SAS\\d{4,}");
    }

    private static class SasThread extends Thread {
        final Supplier<String> sasSupplier;
        final PlatformTransactionManager ptm;
        volatile int stage = 0;
        volatile String value;

        SasThread(PlatformTransactionManager ptm, Supplier<String> sasSupplier) {
            this.ptm = ptm;
            this.sasSupplier = sasSupplier;
        }

        @SuppressWarnings("BusyWait")
        @Override
        public void run() {
            try {
                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                TransactionStatus status = ptm.getTransaction(def);
                stage = 1; // Stage 1: opened a transaction
                value = sasSupplier.get();
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
