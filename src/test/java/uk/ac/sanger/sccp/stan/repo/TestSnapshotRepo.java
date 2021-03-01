package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link SnapshotRepo} and {@link SnapshotElementRepo}
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestSnapshotRepo {
    @Autowired
    private SnapshotRepo snapshotRepo;
    @Autowired
    private SnapshotElementRepo snapshotElementRepo;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSnapshot() {
        Donor donor = entityCreator.createDonor("DONOR1");
        Tissue tissue = entityCreator.createTissue(donor, "TISSUE1");
        Sample sample1 = entityCreator.createSample(tissue, 1);
        Sample sample2 = entityCreator.createSample(tissue, 2);
        Labware lw = entityCreator.createLabware("STAN-A1", entityCreator.createLabwareType("lt", 1, 2));
        Slot slot1 = lw.getFirstSlot();
        Slot slot2 = lw.getSlots().get(1);
        slot1.getSamples().addAll(List.of(sample1, sample2));
        slot2.getSamples().add(sample1);

        Snapshot snapshot = snapshotRepo.save(new Snapshot(lw.getId()));
        final Integer snapshotId = snapshot.getId();
        Iterable<SnapshotElement> elements = snapshotElementRepo.saveAll(
                lw.getSlots().stream()
                        .flatMap(slot -> slot.getSamples().stream()
                                .map(sam -> new SnapshotElement(null, snapshotId, slot.getId(), sam.getId())))
                        .collect(Collectors.toList())
        );
        entityManager.refresh(snapshot);
        assertThat(snapshot.getElements()).hasSize(3);
        Set<List<Integer>> slotSampleIds = new HashSet<>();
        for (SnapshotElement el : elements) {
            assertNotNull(el.getId());
            assertEquals(snapshotId, el.getSnapshotId());
            slotSampleIds.add(List.of(el.getSlotId(), el.getSampleId()));
        }
        Set<List<Integer>> expectedSlotSampleIds = Stream.of(
                List.of(slot1, sample1),
                List.of(slot1, sample2),
                List.of(slot2, sample1)
        ).map(lst -> List.of(((Slot) lst.get(0)).getId(), ((Sample) lst.get(1)).getId()))
                .collect(toSet());
        assertEquals(expectedSlotSampleIds, slotSampleIds);
    }
}
