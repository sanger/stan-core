package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SnapshotElementRepo;
import uk.ac.sanger.sccp.stan.repo.SnapshotRepo;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SnapshotService}
 * @author dr6
 */
public class TestSnapshotService {
    private SnapshotRepo mockSnapshotRepo;
    private SnapshotElementRepo mockSnapshotElementRepo;

    private SnapshotService service;

    @BeforeEach
    void setup() {
        mockSnapshotRepo = mock(SnapshotRepo.class);
        mockSnapshotElementRepo = mock(SnapshotElementRepo.class);

        service = new SnapshotService(mockSnapshotRepo, mockSnapshotElementRepo);
    }

    @Test
    public void testCreateSnapshot() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware labware = EntityFactory.makeEmptyLabware(lt);
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId()+1, 8, sample1.getTissue(), sample1.getBioState());
        final Slot slot1 = labware.getFirstSlot();
        slot1.getSamples().addAll(List.of(sample1, sample2));
        final Slot slot2 = labware.getSlots().get(1);
        slot2.getSamples().add(sample1);
        final int[] idCounter = {0};

        when(mockSnapshotRepo.save(any())).then(invocation -> {
            Snapshot snapshot = invocation.getArgument(0);
            snapshot.setId(++idCounter[0]);
            return snapshot;
        });

        when(mockSnapshotElementRepo.saveAll(any())).then(invocation -> {
            Iterable<SnapshotElement> elements = invocation.getArgument(0);
            for (var element : elements) {
                element.setId(++idCounter[0]);
            }
            return elements;
        });

        Snapshot snapshot = service.createSnapshot(labware);
        Integer snapshotId = snapshot.getId();
        assertNotNull(snapshotId);
        assertEquals(snapshot.getLabwareId(), labware.getId());
        List<SnapshotElement> elements = snapshot.getElements();
        assertThat(elements).hasSize(3);
        Set<List<Integer>> slotSampleIds = new HashSet<>(3);
        for (var el : elements) {
            assertNotNull(el.getId());
            assertEquals(snapshotId, el.getSnapshotId());
            slotSampleIds.add(List.of(el.getSlotId(), el.getSampleId()));
        }
        assertEquals(Set.of(List.of(slot1.getId(), sample1.getId()), List.of(slot1.getId(), sample2.getId()),
                List.of(slot2.getId(), sample1.getId())), slotSampleIds);

        verify(mockSnapshotRepo).save(snapshot);
        verify(mockSnapshotElementRepo).saveAll(elements);
    }
}
