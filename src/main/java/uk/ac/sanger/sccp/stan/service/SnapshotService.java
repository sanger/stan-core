package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SnapshotElementRepo;
import uk.ac.sanger.sccp.stan.repo.SnapshotRepo;

import java.util.ArrayList;
import java.util.List;

/**
 * Service dealing with {@link Snapshot labware snapshots}.
 * @author dr6
 */
@Service
public class SnapshotService {
    private final SnapshotRepo snapshotRepo;
    private final SnapshotElementRepo snapshotElementRepo;

    public SnapshotService(SnapshotRepo snapshotRepo, SnapshotElementRepo snapshotElementRepo) {
        this.snapshotRepo = snapshotRepo;
        this.snapshotElementRepo = snapshotElementRepo;
    }

    /**
     * Creates a snapshot describing the current contents of a piece of labware.
     * @param labware the labware to snapshot
     * @return the new snapshot
     */
    public Snapshot createSnapshot(Labware labware) {
        Snapshot snapshot = snapshotRepo.save(new Snapshot(labware.getId()));
        var elements = createElements(labware, snapshot.getId());
        snapshot.setElements(elements);
        return snapshot;
    }

    /**
     * Creates the elements listing each sample in each slot of the labware
     */
    private Iterable<SnapshotElement> createElements(Labware labware, Integer snapshotId) {
        List<SnapshotElement> elements = new ArrayList<>();
        for (Slot slot : labware.getSlots()) {
            final Integer slotId = slot.getId();
            for (Sample sample : slot.getSamples()) {
                elements.add(new SnapshotElement(null, snapshotId, slotId, sample.getId()));
            }
        }
        return snapshotElementRepo.saveAll(elements);
    }
}
