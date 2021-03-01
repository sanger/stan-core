package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ReleaseRequest;
import uk.ac.sanger.sccp.stan.request.ReleaseResult;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * @author dr6
 */
@Service
public class ReleaseServiceImp implements ReleaseService {
    private final Transactor transactor;
    private final ReleaseDestinationRepo destinationRepo;
    private final ReleaseRecipientRepo recipientRepo;
    private final LabwareRepo labwareRepo;
    private final StoreService storeService;
    private final ReleaseRepo releaseRepo;
    private final SnapshotService snapshotService;

    @Autowired
    public ReleaseServiceImp(Transactor transactor,
                             ReleaseDestinationRepo destinationRepo, ReleaseRecipientRepo recipientRepo,
                             LabwareRepo labwareRepo, StoreService storeService, ReleaseRepo releaseRepo,
                             SnapshotService snapshotService) {
        this.transactor = transactor;
        this.destinationRepo = destinationRepo;
        this.recipientRepo = recipientRepo;
        this.labwareRepo = labwareRepo;
        this.storeService = storeService;
        this.releaseRepo = releaseRepo;
        this.snapshotService = snapshotService;
    }

    @Override
    public ReleaseResult releaseAndUnstore(User user, ReleaseRequest request) {
        List<Release> releases = transactRelease(user, request);
        storeService.discardStorage(user, request.getBarcodes());
        return new ReleaseResult(releases);
    }

    /**
     * Transactionally validates and performs the release request (not including unstoring).
     * @param user the user responsible for the release
     * @param request the details of the release
     * @return the created releases
     */
    public List<Release> release(User user, ReleaseRequest request) {
        ReleaseRecipient recipient = recipientRepo.getByUsername(request.getRecipient());
        ReleaseDestination destination = destinationRepo.getByName(request.getDestination());
        if (request.getBarcodes()==null || request.getBarcodes().isEmpty()) {
            throw new IllegalArgumentException("No barcodes supplied to release.");
        }
        Collection<Labware> labware = loadLabware(request.getBarcodes());
        validateLabware(labware);
        labware = updateReleasedLabware(labware);
        return recordReleases(user, destination, recipient, labware);
    }

    // NB @Transactional annotation does not work for method calls within the same object
    public List<Release> transactRelease(User user, ReleaseRequest request) {
        return transactor.transact("Release transaction", () -> release(user, request));
    }

    /**
     * Loads the labware with the given barcodes. Errors if the barcodes are not valid or not distinct.
     * The order of the labware returned is not expected to match the order of the barcodes.
     * @param barcodes a distinct collection of labware barcodes
     * @return the corresponding labware
     */
    public List<Labware> loadLabware(Collection<String> barcodes) {
        List<Labware> labware = labwareRepo.findByBarcodeIn(barcodes);
        if (labware.size() == barcodes.size()) {
            return labware;
        }
        Set<String> wantedBarcodes = new HashSet<>();
        Set<String> repeated = new HashSet<>();
        for (String barcode : barcodes) {
            if (barcode==null) {
                throw new IllegalArgumentException("null is not a valid barcode.");
            }
            String bcu = barcode.toUpperCase();
            if (!wantedBarcodes.add(bcu)) {
                repeated.add(bcu);
            }
        }
        if (!repeated.isEmpty()) {
            throw new IllegalArgumentException("Repeated barcodes: " + repeated);
        }
        labware.forEach(lw -> wantedBarcodes.remove(lw.getBarcode().toUpperCase()));
        throw new IllegalArgumentException("Unknown labware barcodes: " + wantedBarcodes);
    }

    /**
     * Checks that the labware can be released.
     * Labware that is empty, destroyed, or already released cannot be released.
     * @param labware the labware to check
     * @exception IllegalArgumentException if the labware cannot be released
     */
    public void validateLabware(Collection<Labware> labware) {
        List<String> emptyLabwareBarcodes = labware.stream()
                .filter(Labware::isEmpty)
                .map(Labware::getBarcode)
                .collect(toList());
        if (!emptyLabwareBarcodes.isEmpty()) {
            throw new IllegalArgumentException("Cannot release empty labware: "+emptyLabwareBarcodes);
        }
        List<String> releasedLabwareBarcodes = labware.stream()
                .filter(Labware::isReleased)
                .map(Labware::getBarcode)
                .collect(toList());
        if (!releasedLabwareBarcodes.isEmpty()) {
            throw new IllegalArgumentException("Labware has already been released: "+releasedLabwareBarcodes);
        }
        List<String> destroyedLabwareBarcodes = labware.stream()
                .filter(Labware::isDestroyed)
                .map(Labware::getBarcode)
                .collect(toList());
        if (!destroyedLabwareBarcodes.isEmpty()) {
            throw new IllegalArgumentException("Labware cannot be released because it is destroyed: "+destroyedLabwareBarcodes);
        }
        List<String> discardedLabwareBarcodes = labware.stream()
                .filter(Labware::isDiscarded)
                .map(Labware::getBarcode)
                .collect(toList());
        if (!discardedLabwareBarcodes.isEmpty()) {
            throw new IllegalArgumentException("Labware cannot be released because it is discarded: "+discardedLabwareBarcodes);
        }
    }

    /**
     * Records releases to the database
     * @param user the user responsible for the release
     * @param destination the release destination
     * @param recipient the release recipient
     * @param labware the collection of labware being released
     * @return a list of newly recorded releases for the indicated labware
     */
    public List<Release> recordReleases(User user, ReleaseDestination destination, ReleaseRecipient recipient,
                                            Collection<Labware> labware) {
        return labware.stream()
                .map(lw -> recordRelease(user, destination, recipient, lw))
                .collect(toList());
    }

    /**
     * Records a release to the database (including snapshotting the current contents of the labware).
     * @param user the user responsible for the release
     * @param destination the release destination
     * @param recipient the release recipient
     * @param labware the item of labware being released
     * @return the newly recorded release
     */
    public Release recordRelease(User user, ReleaseDestination destination, ReleaseRecipient recipient, Labware labware) {
        Snapshot snapshot = snapshotService.createSnapshot(labware);
        return releaseRepo.save(new Release(labware, user, destination, recipient, snapshot.getId()));
    }

    /**
     * Marks all the indicated labware as released, updating the database.
     * @param labware the labware being released
     * @return the updated labware
     */
    public Collection<Labware> updateReleasedLabware(Collection<Labware> labware) {
        labware.forEach(lw -> lw.setReleased(true));
        Iterable<Labware> saved = labwareRepo.saveAll(labware);
        return (saved instanceof Collection ? (Collection<Labware>) saved : newArrayList(saved));
    }
}
