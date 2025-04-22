package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.SampleSlotId;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.utils.BasicUtils.hashSetOf;

/**
 * Tests setOperationWork mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestSetOperationWorkMutation {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private ActionRepo actionRepo;
    @Autowired
    private WorkRepo workRepo;
    @Autowired
    private SnapshotRepo snapshotRepo;
    @Autowired
    private SnapshotElementRepo snapshotElementRepo;
    @Autowired
    private ReleaseRepo releaseRepo;
    @Autowired
    private WorkChangeRepo workChangeRepo;
    @Autowired
    private WorkChangeLinkRepo workChangeLinkRepo;

    private OperationType opType;
    private User user;
    private Tissue tissue;
    private ReleaseDestination releaseDestination;
    private ReleaseRecipient releaseRecipient;

    private User getUser() {
        if (user==null) {
            user = entityCreator.createUser("user1");
        }
        return user;
    }

    private OperationType getOpType() {
        if (opType==null) {
            opType = entityCreator.createOpType("opname", null);
        }
        return opType;
    }

    private Tissue getTissue() {
        if (tissue==null) {
            tissue = entityCreator.createTissue(null, null);
        }
        return tissue;
    }

    private ReleaseDestination getReleaseDestination() {
        if (releaseDestination==null) {
            releaseDestination = entityCreator.createReleaseDestination("The Moon");
        }
        return releaseDestination;
    }

    private ReleaseRecipient getReleaseRecipient() {
        if (releaseRecipient==null) {
            releaseRecipient = entityCreator.createReleaseRecipient("jimmy");
        }
        return releaseRecipient;
    }

    private Sample[] makeSamples(int n) {
        Tissue tissue = getTissue();
        return IntStream.rangeClosed(1,n).mapToObj(i -> entityCreator.createSample(tissue, i)).toArray(Sample[]::new);
    }

    private Work[] makeWorks(int n) {
        Work[] works = new Work[n];
        works[0] = entityCreator.createWork(null, null, null, null, null);
        for (int i = 1; i < works.length; ++i) {
            works[i] = entityCreator.createWorkLike(works[0]);
        }
        return works;
    }

    private Operation makeOp(Object... args) {
        OperationType opType = getOpType();
        User user = getUser();
        Operation op = opRepo.save(new Operation(null, opType, null, null, user));
        List<Action> actions = new ArrayList<>(args.length/2);
        for (int i = 0; i < args.length; i+=2) {
            Slot slot = (Slot) args[i];
            Sample sample = (Sample) args[i+1];
            Action action = new Action(null, op.getId(), slot, slot, sample, sample);
            actions.add(action);
        }
        actionRepo.saveAll(actions);
        entityManager.refresh(op);
        return op;
    }

    private Release makeRelease(Labware lw, Object... args) {
        Snapshot snapshot = snapshotRepo.save(new Snapshot(lw.getId()));
        List<SnapshotElement> elements = new ArrayList<>(args.length/2);
        for (int i = 0; i < args.length; i+=2) {
            Slot slot = (Slot) args[i];
            Sample sample = (Sample) args[i+1];
            SnapshotElement el = new SnapshotElement(null, snapshot.getId(), slot.getId(), sample.getId());
            elements.add(el);
        }
        snapshotElementRepo.saveAll(elements);
        entityManager.refresh(snapshot);
        Release release = new Release(lw, getUser(), getReleaseDestination(), getReleaseRecipient(), snapshot.getId());
        return releaseRepo.save(release);
    }

    private Set<SampleSlotId> ssIds(Sample[] samples, Labware lw, int... indexes) {
        Set<SampleSlotId> ssIds = new HashSet<>(indexes.length);
        final List<Slot> slots = lw.getSlots();
        for (int index : indexes) {
            ssIds.add(new SampleSlotId(samples[index].getId(), slots.get(index).getId()));
        }
        return ssIds;
    }

    @Test
    @Transactional
    public void testSetOperationWork() throws Exception {
        tester.setUser(getUser());
        Sample[] samples = makeSamples(4);
        Work[] works = makeWorks(3);
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 5);
        Labware lw = entityCreator.createLabware("STAN-1", lt, samples);
        List<Slot> slots = lw.getSlots();
        Operation op0 = makeOp(slots.get(0), samples[0], slots.get(1), samples[1]);
        Operation op1 = makeOp(slots.get(1), samples[1], slots.get(2), samples[2]);
        Operation op2 = makeOp(slots.get(0), samples[0], slots.get(3), samples[3]);
        works[0].setOperationIds(hashSetOf(op0.getId(), op2.getId()));
        works[1].setOperationIds(hashSetOf(op1.getId()));
        Release release = makeRelease(lw, slots.get(1), samples[1], slots.get(3), samples[3]);
        works[1].setReleaseIds(hashSetOf(release.getId()));

        works[0].setSampleSlotIds(ssIds(samples, lw, 0, 1, 3));

        works[1].setSampleSlotIds(ssIds(samples, lw, 1, 2, 3));

        workRepo.saveAll(Arrays.asList(works));

        String mutation = tester.readGraphQL("setoperationwork.graphql")
                .replace("[WORK]", works[2].getWorkNumber())
                .replace("999", op0.getId()+","+op1.getId());
        Object response = tester.post(mutation);

        List<Map<String, ?>> responseOps = chainGet(response, "data", "setOperationWork");
        assertThat(responseOps).hasSize(2);
        assertEquals(op0.getId(), responseOps.get(0).get("id"));
        assertEquals(op1.getId(), responseOps.get(1).get("id"));

        entityManager.flush();
        Arrays.stream(works).forEach(entityManager::refresh);

        assertThat(works[2].getOperationIds()).containsExactlyInAnyOrder(op0.getId(), op1.getId());
        assertThat(works[2].getSampleSlotIds()).containsExactlyInAnyOrderElementsOf(ssIds(samples, lw, 0, 1, 2));

        assertThat(works[0].getOperationIds()).containsExactlyInAnyOrder(op2.getId());
        assertThat(works[1].getOperationIds()).isEmpty();
        assertThat(works[1].getReleaseIds()).containsExactly(release.getId());
        assertThat(works[0].getSampleSlotIds()).containsExactlyInAnyOrderElementsOf(ssIds(samples, lw, 0, 3));
        assertThat(works[1].getSampleSlotIds()).containsExactlyInAnyOrderElementsOf(ssIds(samples, lw, 1, 3));

        // Adding work 2 to operations 0 -- slotsamples 0,0, 1,1 -- and 1 -- slotsamples 1,1, 2,2
        //  op 0 is already linked to work 0 (0-0 and 1-1)
        //  op 1 is already linked to work 1 (1-1 and 2-2)
        // work 0 0-0 is covered by op 2 (0-0, 3-3)
        // work 1 1-1 is covered by release 0 (1-1, 3-3)
        // So after the update, work 2 should be linked to ops 1 and 2 with their ss,
        //   work 0 should only be linked to op 2, ss 0-0,3-3
        //   work 1 should only be linked to release 0, ss 1-1,3-3

        WorkChange change = onlyItem(workChangeRepo.findAll());
        assertNotNull(change.getId());
        assertEquals(user.getId(), change.getUserId());
        Iterable<WorkChangeLink> links = workChangeLinkRepo.findAll();
        List<int[]> changes = new ArrayList<>(4);
        links.forEach(link -> {
            assertEquals(change.getId(), link.getWorkChangeId());
            assertNotNull(link.getId());
            changes.add(new int[] {link.getOperationId(), link.getWorkId(), link.isLink() ? 1 : 0});
        });
        assertThat(changes).hasSize(4);
        assertThat(changes).containsExactlyInAnyOrder(new int[][] {
                {op0.getId(), works[2].getId(), 1},
                {op1.getId(), works[2].getId(), 1},
                {op0.getId(), works[0].getId(), 0},
                {op1.getId(), works[1].getId(), 0},
        });
    }

    private static <E> E onlyItem(Iterable<E> items) {
        Iterator<E> iter = items.iterator();
        E item = iter.next();
        assertFalse(iter.hasNext());
        return item;
    }
}
