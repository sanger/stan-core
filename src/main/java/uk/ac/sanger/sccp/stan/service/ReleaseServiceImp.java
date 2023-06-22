package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.config.StanConfig;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.BasicLocation;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ReleaseRequest;
import uk.ac.sanger.sccp.stan.request.ReleaseRequest.ReleaseLabware;
import uk.ac.sanger.sccp.stan.request.ReleaseResult;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class ReleaseServiceImp implements ReleaseService {
    private final StanConfig stanConfig;
    private final Transactor transactor;
    private final EntityManager entityManager;
    private final ReleaseDestinationRepo destinationRepo;
    private final ReleaseRecipientRepo recipientRepo;
    private final LabwareRepo labwareRepo;
    private final StoreService storeService;
    private final ReleaseRepo releaseRepo;
    private final SnapshotService snapshotService;
    private final EmailService emailService;
    private final WorkService workService;

    @Autowired
    public ReleaseServiceImp(StanConfig stanConfig, Transactor transactor, EntityManager entityManager,
                             ReleaseDestinationRepo destinationRepo, ReleaseRecipientRepo recipientRepo,
                             LabwareRepo labwareRepo, StoreService storeService, ReleaseRepo releaseRepo,
                             SnapshotService snapshotService, EmailService emailService, WorkService workService) {
        this.stanConfig = stanConfig;
        this.transactor = transactor;
        this.entityManager = entityManager;
        this.destinationRepo = destinationRepo;
        this.recipientRepo = recipientRepo;
        this.labwareRepo = labwareRepo;
        this.storeService = storeService;
        this.releaseRepo = releaseRepo;
        this.snapshotService = snapshotService;
        this.emailService = emailService;
        this.workService = workService;
    }

    @Override
    public ReleaseResult releaseAndUnstore(User user, ReleaseRequest request) {
        requireNonNull(user, "No user supplied.");
        requireNonNull(request, "No request supplied.");
        // Load info and do basic validation before the transaction
        ReleaseRecipient recipient = recipientRepo.getByUsername(request.getRecipient());
        ReleaseDestination destination = destinationRepo.getByName(request.getDestination());
        if (request.getReleaseLabware()==null || request.getReleaseLabware().isEmpty()) {
            throw new IllegalArgumentException("No labware specified to release.");
        }
        if (!recipient.isEnabled()) {
            throw new IllegalArgumentException("Release recipient "+recipient.getUsername()+" is not enabled.");
        }
        if (!destination.isEnabled()) {
            throw new IllegalArgumentException("Release destination "+destination.getName()+" is not enabled.");
        }
        List<String> barcodes = request.getReleaseLabware().stream()
                .map(ReleaseLabware::getBarcode)
                .collect(toList());
        List<Labware> labware = loadLabware(barcodes);
        UCMap<Work> workMap = loadWork(request.getReleaseLabware());
        validateLabware(labware);
        validateContents(labware);
        List<ReleaseRecipient> otherRecs = loadOtherRecipients(request.getOtherRecipients());

        // Looks valid, so load storage locations before the transaction
        UCMap<BasicLocation> locations = storeService.loadBasicLocationsOfItems(labware.stream().map(Labware::getBarcode).collect(toList()));

        // Perform the release inside a transaction
        List<Release> releases = transactRelease(user, recipient, otherRecs, destination, labware, locations, workMap);

        // Unstore the labware after the transaction
        storeService.discardStorage(user, barcodes);

        String recipientEmail = canonicaliseEmail(recipient.getUsername());

        List<String> otherEmails = otherRecs.stream()
                .map(rec -> canonicaliseEmail(rec.getUsername()))
                .collect(toList());

        List<String> workNumbers = workMap.values().stream()
                .map(Work::getWorkNumber)
                .sorted()
                .collect(toList());

        emailService.tryReleaseEmail(recipientEmail, otherEmails, workNumbers, releaseFileLink(releases));

        return new ReleaseResult(releases);
    }

    public List<ReleaseRecipient> loadOtherRecipients(Collection<String> usernames) {
        if (nullOrEmpty(usernames)) {
            return List.of();
        }
        final List<ReleaseRecipient> foundRecs = recipientRepo.getAllByUsernameIn(usernames);
        Set<ReleaseRecipient> seen = new HashSet<>(foundRecs.size());
        List<String> disabledRecs = new ArrayList<>();
        List<ReleaseRecipient> recs = new ArrayList<>(foundRecs.size());
        for (ReleaseRecipient rec : foundRecs) {
            if (seen.add(rec)) {
                if (rec.isEnabled()) {
                    recs.add(rec);
                } else {
                    disabledRecs.add(rec.getUsername());
                }
            }
        }
        if (!disabledRecs.isEmpty()) {
            throw new IllegalArgumentException("Other recipients disabled: "+disabledRecs);
        }
        return recs;
    }

    public String canonicaliseEmail(String email) {
        return (email.indexOf('@') < 0 ? (email + "@sanger.ac.uk") : email);
    }

    /**
     * Path to the release file for the given releases
     * @param releases the releases
     * @return the path to the release file, as a string
     */
    public String releaseFileLink(Collection<Release> releases) {
        String joinedIds = releases.stream().map(r -> r.getId().toString()).collect(joining(","));
        return stanConfig.getRoot() + "release?id=" + joinedIds;
    }

    /**
     * Revalidates the labware and performs the release (not including unstoring).
     * This should be called inside a transaction.
     * @param user the user responsible for the release
     * @param recipient the recipient of the release
     * @param otherRecs other recipients
     * @param destination the destination of the release
     * @param labware the labware that will be released
     * @param locations the locations (if any) of the labware, mapped from the labware barcode
     * @param workMap map of labware barcode to work
     * @return the created releases
     */
    public List<Release> release(User user, ReleaseRecipient recipient, List<ReleaseRecipient> otherRecs,
                                 ReleaseDestination destination, Collection<Labware> labware,
                                 UCMap<BasicLocation> locations, UCMap<Work> workMap) {
        // Reload the labware inside the transaction
        labware.forEach(entityManager::refresh);
        // Revalidate inside the transaction, in case anything has happened
        validateLabware(labware);
        validateContents(labware);

        // execution
        labware = updateReleasedLabware(labware);
        final List<Release> releases = recordReleases(user, destination, recipient, otherRecs, labware, locations);
        link(releases, workMap);
        return releases;
    }

    // NB @Transactional annotation does not work for method calls within the same instance
    public List<Release> transactRelease(User user, ReleaseRecipient recipient, List<ReleaseRecipient> otherRecs,
                                         ReleaseDestination destination, List<Labware> labware,
                                         UCMap<BasicLocation> locations, UCMap<Work> workMap) {
        return transactor.transact("Release transaction",
                () -> release(user, recipient, otherRecs, destination, labware, locations, workMap));
    }

    /**
     * Links the given releases to the indicates works.
     * @param releases the new releases
     * @param workMap map of labware barcode to work
     */
    public void link(Collection<Release> releases, UCMap<Work> workMap) {
        Map<Integer, List<Release>> workIdReleases = new HashMap<>();
        for (Release release : releases) {
            Work work = workMap.get(release.getLabware().getBarcode());
            if (work!=null) {
                workIdReleases.computeIfAbsent(work.getId(), x -> new ArrayList<>()).add(release);
            }
        }
        Map<Integer, Work> workIdMap = new HashMap<>();
        workMap.values().forEach(work -> workIdMap.put(work.getId(), work));
        for (Map.Entry<Integer, Work> entry : workIdMap.entrySet()) {
            List<Release> workReleases = workIdReleases.get(entry.getKey());
            if (workReleases!=null) {
                workService.linkReleases(entry.getValue(), workReleases);
            }
        }
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
     * Loads the works indicated (if any). Errors if the work numbers are unknown or unusable.
     * @param rls barcodes and work numbers
     * @return a map of labware barcode to work
     */
    public UCMap<Work> loadWork(Collection<ReleaseLabware> rls) {
        UCMap<Work> bcMap = new UCMap<>(rls.size());
        Set<String> workNumbers = rls.stream()
                .map(ReleaseLabware::getWorkNumber)
                .filter(Objects::nonNull)
                .collect(toSet());
        if (workNumbers.isEmpty()) {
            return bcMap;
        }
        UCMap<Work> workNumberMap = workService.getUsableWorkMap(workNumbers);
        for (ReleaseLabware rl : rls) {
            String bc = rl.getBarcode();
            String workNumber = rl.getWorkNumber();
            if (workNumber!=null) {
                bcMap.put(bc, workNumberMap.get(workNumber));
            }
        }
        return bcMap;
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
     * Checks that all the labware given is allowed to be released in one batch.
     * You're not allowed to release a mix of cDNA and other.
     * <p>Technically there's no reason not to allow this, as long as each individual labware
     * contains only one bio state. But it would cause confusion to the user if they
     * cannot go on to download a release file for all the labware just released.
     * @param labware the labware being released
     * @exception IllegalArgumentException if the contents of the labware cannot be released together
     */
    public void validateContents(Collection<Labware> labware) {
        Set<BioState> bioStates = labware.stream()
                .flatMap(lw -> lw.getSlots().stream())
                .flatMap(slot -> slot.getSamples().stream())
                .map(Sample::getBioState)
                .collect(toSet());
        if (bioStates.size() > 1) {
            final Predicate<BioState> isCdna = bs -> bs.getName().equalsIgnoreCase("cDNA");
            if (bioStates.stream().anyMatch(isCdna) && !bioStates.stream().allMatch(isCdna)) {
                throw new IllegalArgumentException("Cannot release a mix of cDNA and other bio states.");
            }
        }
    }

    /**
     * Records releases to the database
     * @param user the user responsible for the release
     * @param destination the release destination
     * @param recipient the release recipient
     * @param otherRecs other recipients
     * @param labware the collection of labware being released
     * @return a list of newly recorded releases for the indicated labware
     */
    public List<Release> recordReleases(User user, ReleaseDestination destination,
                                        ReleaseRecipient recipient, List<ReleaseRecipient> otherRecs,
                                        Collection<Labware> labware, UCMap<BasicLocation> locations) {
        return labware.stream()
                .map(lw -> recordRelease(user, destination, recipient, otherRecs, lw, locations.get(lw.getBarcode())))
                .collect(toList());
    }

    /**
     * Records a release to the database (including snapshotting the current contents of the labware).
     * @param user the user responsible for the release
     * @param destination the release destination
     * @param recipient the release recipient
     * @param otherRecs other recipients
     * @param labware the item of labware being released
     * @param location the location barcode and item address where the labware was stored, if any
     * @return the newly recorded release
     */
    public Release recordRelease(User user, ReleaseDestination destination,
                                 ReleaseRecipient recipient, List<ReleaseRecipient> otherRecs,
                                 Labware labware, BasicLocation location) {
        Snapshot snapshot = snapshotService.createSnapshot(labware);

        final Release newRelease = new Release(labware, user, destination, recipient, snapshot.getId());
        if (location!=null) {
            newRelease.setLocationBarcode(location.getBarcode());
            newRelease.setStorageAddress(location.getAddress());
        }
        newRelease.setOtherRecipients(otherRecs);
        return releaseRepo.save(newRelease);
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
