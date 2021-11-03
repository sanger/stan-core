package uk.ac.sanger.sccp.stan.service.releasefile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.StoredItem;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * Service loading data for release files
 * @author dr6
 */
@Service
public class ReleaseFileService {
    private final ReleaseRepo releaseRepo;
    private final SampleRepo sampleRepo;
    private final LabwareRepo labwareRepo;
    private final MeasurementRepo measurementRepo;
    private final SnapshotRepo snapshotRepo;
    private final Ancestoriser ancestoriser;
    private final StoreService storeService;

    @Autowired
    public ReleaseFileService(ReleaseRepo releaseRepo, SampleRepo sampleRepo, LabwareRepo labwareRepo,
                              MeasurementRepo measurementRepo, SnapshotRepo snapshotRepo, Ancestoriser ancestoriser,
                              StoreService storeService) {
        this.releaseRepo = releaseRepo;
        this.sampleRepo = sampleRepo;
        this.labwareRepo = labwareRepo;
        this.measurementRepo = measurementRepo;
        this.snapshotRepo = snapshotRepo;
        this.ancestoriser = ancestoriser;
        this.storeService = storeService;
    }

    /**
     * Gets the entries which become rows in a release file.
     * Each release maps to 1 or more release entries.
     * @param releaseIds the ids of the releases
     * @return the release entries from the specified releases
     */
    public ReleaseFileContent getReleaseFileContent(Collection<Integer> releaseIds) {
        if (releaseIds.isEmpty()) {
            return new ReleaseFileContent(ReleaseFileMode.NORMAL, List.of());
        }
        List<Release> releases = getReleases(releaseIds);

        Map<Integer, Snapshot> snapshots = loadSnapshots(releases);
        Map<Integer, Sample> samples = loadSamples(releases, snapshots);
        ReleaseFileMode mode = checkMode(samples.values());

        List<ReleaseEntry> entries = releases.stream()
                .flatMap(r -> toReleaseEntries(r, samples, snapshots))
                .collect(toList());

        loadLastSection(entries);
        Ancestry ancestry = findAncestry(entries);
        loadSources(entries, ancestry, mode);
        loadSectionThickness(entries, ancestry);
        loadStorageAddresses(entries);
        return new ReleaseFileContent(mode, entries);
    }

    /**
     * Checks that all the samples can be listed together in one release file.
     * Samples of cDNA cannot be listed together with samples in other bio states.
     * @param samples the samples
     * @return the single mode valid with all the samples
     * @exception IllegalArgumentException if the samples cannot be listed together in one release file
     */
    public ReleaseFileMode checkMode(Collection<Sample> samples) {
        Set<ReleaseFileMode> modes = samples.stream().map(this::mode).collect(toSet());
        if (modes.size() > 1) {
            throw new IllegalArgumentException("Cannot create a release file with a mix of " +
                    "cDNA and other bio states.");
        }
        return modes.stream().findAny().orElse(ReleaseFileMode.NORMAL);
    }

    /**
     * Gets the release mode for a sample.
     * This is {@code CDNA} if the bio state is cDNA; otherwise {@code NORMAL}
     * @param sample the sample
     * @return the mode appropriate for the sample
     */
    private ReleaseFileMode mode(Sample sample) {
        if (sample.getBioState().getName().equalsIgnoreCase("cDNA")) {
            return ReleaseFileMode.CDNA;
        }
        return ReleaseFileMode.NORMAL;
    }

    /**
     * Gets the ancestry for the given release entries;
     * that is, a map of what slot-sample combinations arose from what other
     * slot-sample combinations
     * @param entries the release entries under construction
     * @return a map of destination slot/sample to source slot/sample
     */
    public Ancestry findAncestry(Collection<ReleaseEntry> entries) {
        Set<SlotSample> entrySlotSamples = entries.stream()
                .map(entry -> new SlotSample(entry.getSlot(), entry.getSample()))
                .collect(toSet());
        return ancestoriser.findAncestry(entrySlotSamples);
    }

    /**
     * Loads the samples listed in the given releases.
     * Those that are available are taken from the labware included in each release.
     * The rest are loaded from the database.
     * @param releases the releases we are describing
     * @param snapshots the snapshots for the releases
     * @return a map of sample id to sample
     */
    public Map<Integer, Sample> loadSamples(Collection<Release> releases, Map<Integer, Snapshot> snapshots) {
        final Map<Integer, Sample> sampleMap = new HashMap<>();
        Consumer<Sample> addSample = sam -> sampleMap.put(sam.getId(), sam);
        releases.stream()
                .flatMap(r -> r.getLabware().getSlots().stream())
                .flatMap(slot -> slot.getSamples().stream())
                .forEach(addSample);
        Set<Integer> sampleIds = releases.stream()
                .flatMap(r -> snapshots.get(r.getSnapshotId()).getElements().stream().map(SnapshotElement::getSampleId))
                .filter(sid -> !sampleMap.containsKey(sid))
                .collect(toSet());
        if (!sampleIds.isEmpty()) {
            sampleRepo.getAllByIdIn(sampleIds).forEach(addSample);
        }
        return sampleMap;
    }

    /**
     * Loads and returns a map of snapshot id to shapshot.
     * Errors if any shapshots cannot be found.
     * @param releases the releases indicating the snapshots
     * @return a map of snapshots keyed by their id
     * @exception EntityNotFoundException if any snapshots could not be found
     */
    public Map<Integer, Snapshot> loadSnapshots(Collection<Release> releases) {
        Map<Integer, Snapshot> snapshots = snapshotRepo.findAllByIdIn(
                releases.stream().map(Release::getSnapshotId)
                        .collect(toList())
        ).stream().collect(toMap(Snapshot::getId, snap -> snap));
        if (snapshots.size() < releases.size()) {
            List<Integer> missingReleaseIds = releases.stream()
                    .filter(rel -> snapshots.get(rel.getSnapshotId())==null)
                    .map(Release::getId)
                    .collect(toList());
            if (!missingReleaseIds.isEmpty()) {
                throw new EntityNotFoundException("Labware snapshot missing for release ids: "+missingReleaseIds);
            }
        }
        return snapshots;
    }

    /**
     * Figures and sets the last section field for release entries.
     * The last section is only set on entries that specify a block.
     * The last section is {@link Slot#getBlockHighestSection}
     * @param entries the release entries under construction
     */
    public void loadLastSection(Collection<ReleaseEntry> entries) {
        for (ReleaseEntry entry : entries) {
            if (!entry.getSlot().isBlock() || !entry.getSlot().getBlockSampleId().equals(entry.getSample().getId())) {
                continue;
            }
            entry.setLastSection(entry.getSlot().getBlockHighestSection());
        }
    }

    /**
     * Creates release entries for a given release.
     * One entry per slot/sample in combination.
     * @param release the release
     * @param sampleIdMap a map to look up samples in
     * @param snapshots a map to look up snapshots in
     * @return a stream of release entries
     */
    public Stream<ReleaseEntry> toReleaseEntries(final Release release, Map<Integer, Sample> sampleIdMap,
                                                 Map<Integer, Snapshot> snapshots) {
        final Labware labware = release.getLabware();
        final Map<Integer, Slot> slotIdMap = labware.getSlots().stream()
                .collect(toMap(Slot::getId, slot -> slot));
        return snapshots.get(release.getSnapshotId()).getElements().stream()
                .map(el -> new ReleaseEntry(release.getLabware(), slotIdMap.get(el.getSlotId()), sampleIdMap.get(el.getSampleId())));
    }

    /**
     * Loads the releases from the release repo.
     * @param releaseIds the release ids to look up
     * @return a list of releases
     */
    public List<Release> getReleases(Collection<Integer> releaseIds) {
        return releaseRepo.getAllByIdIn(releaseIds);
    }

    /**
     * Loads the "sources", whatever that means for the release mode.
     * @param entries the contents of the things that were released
     * @param ancestry the ancestors of the released things
     * @param mode the release file mode
     */
    public void loadSources(Collection<ReleaseEntry> entries, Ancestry ancestry, ReleaseFileMode mode) {
        if (mode==ReleaseFileMode.CDNA) {
            loadSourcesForCDNA(entries, ancestry);
        } else {
            loadOriginalBarcodes(entries, ancestry);
        }
    }

    /**
     * Sets the original (block) barcodes for the release entries.
     * Follows the slot/sample combinations through the given ancestry to find the original
     * slot, and looks up the barcode.
     * @param entries the release entries
     * @param ancestry the ancestry map
     */
    public void loadOriginalBarcodes(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Map<Integer, String> labwareIdBarcode = new HashMap<>();
        for (ReleaseEntry entry : entries) {
            Labware lw = entry.getLabware();
            labwareIdBarcode.put(lw.getId(), lw.getBarcode());
        }
        for (ReleaseEntry entry : entries) {
            SlotSample slotSample = new SlotSample(entry.getSlot(), entry.getSample());
            Set<SlotSample> roots = ancestry.getRoots(slotSample);
            SlotSample root = roots.stream()
                    .min(Comparator.naturalOrder())
                    .orElse(slotSample);
            Integer lwId = root.getSlot().getLabwareId();
            String bc = labwareIdBarcode.get(lwId);
            if (bc==null) {
                bc = labwareRepo.getById(lwId).getBarcode();
                labwareIdBarcode.put(lwId, bc);
            }
            entry.setSourceBarcode(bc);
        }
    }

    /**
     * Sets the source barcode and source address to the tissue that originated it
     * @param entries the release entries
     * @param ancestry the ancestry info for the samples and labware involved
     */
    public void loadSourcesForCDNA(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Map<Integer, String> labwareIdBarcode = new HashMap<>();
        for (ReleaseEntry entry : entries) {
            SlotSample ss = selectSourceForCDNA(entry, ancestry);
            if (ss!=null) {
                Slot slot = ss.getSlot();
                final Integer labwareId = slot.getLabwareId();
                String barcode = labwareIdBarcode.get(labwareId);
                if (barcode==null) {
                    barcode = labwareRepo.getById(labwareId).getBarcode();
                    labwareIdBarcode.put(labwareId, barcode);
                }
                entry.setSourceBarcode(barcode);
                entry.setSourceAddress(slot.getAddress());
            }
        }
    }

    public SlotSample selectSourceForCDNA(ReleaseEntry entry, Ancestry ancestry) {
        for (SlotSample ss : ancestry.ancestors(new SlotSample(entry.getSlot(), entry.getSample()))) {
            if (!ss.getSample().getBioState().getName().equalsIgnoreCase("cDNA")) {
                return ss;
            }
        }
        return null;
    }

    /**
     * Sets the section thickness for the release entries.
     * The thickness is a measurement recorded on the specified slot, or any ancestral slot
     * found through the given ancestry map.
     * @param entries the release entries
     * @param ancestry the ancestry map
     */
    public void loadSectionThickness(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Set<Integer> slotIds = ancestry.keySet().stream().map(ss -> ss.getSlot().getId()).collect(toSet());
        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(slotIds);
        Map<Integer, List<Measurement>> slotIdToMeasurement = new HashMap<>();
        for (Measurement measurement : measurements) {
            if (measurement.getOperationId()!=null && measurement.getName().equalsIgnoreCase("Thickness")) {
                List<Measurement> slotIdMeasurements = slotIdToMeasurement.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
                slotIdMeasurements.add(measurement);
            }
        }
        for (ReleaseEntry entry : entries) {
            Measurement measurement = selectMeasurement(entry, slotIdToMeasurement, ancestry);
            if (measurement!=null) {
                entry.setSectionThickness(measurement.getValue());
            }
        }
    }

    public Measurement selectMeasurement(ReleaseEntry entry, Map<Integer, List<Measurement>> slotIdToMeasurement,
                                         Ancestry ancestry) {
        for (SlotSample ss : ancestry.ancestors(new SlotSample(entry.getSlot(), entry.getSample()))) {
            List<Measurement> measurements = slotIdToMeasurement.get(ss.getSlot().getId());
            if (measurements!=null && !measurements.isEmpty()) {
                final Integer sampleId = ss.getSample().getId();
                var optMeasurement = measurements.stream()
                        .filter(m -> m.getSampleId().equals(sampleId))
                        .findAny();
                if (optMeasurement.isPresent()) {
                    return optMeasurement.get();
                }
            }
        }
        return null;
    }

    /**
     * Fills in the storage address.
     * If every labware in the release entries is stored in the same location and has an address,
     * then the addresses will be filled in; otherwise, they will not be filled in for any entries.
     * @param entries release entries under construction
     */
    public void loadStorageAddresses(Collection<ReleaseEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        Set<String> barcodes = entries.stream()
                .map(e -> e.getLabware().getBarcode().toUpperCase())
                .collect(toSet());
        UCMap<Address> addresses = lookUpStorageAddresses(barcodes);
        if (addresses!=null) {
            for (ReleaseEntry entry : entries) {
                entry.setStorageAddress(addresses.get(entry.getLabware().getBarcode()));
            }
        }
    }

    /**
     * Gets a map from item barcode to storage address.
     * This includes checking if the locations are present and similar enough that the storage address
     * should be included: if they are not, then this method should return null.
     * @param barcodes the barcodes that should be looked up. These are assumed to all be for different labware
     *                 (so should not include different cases of the same string).
     * @return the map of labware barcode to address, if the addresses should be filled in; null if the
     *         addresses should not be filled in.
     */
    public UCMap<Address> lookUpStorageAddresses(Set<String> barcodes) {
        var storedList = storeService.getStored(barcodes);
        if (storedList.size() < barcodes.size()) {
            return null;
        }
        UCMap<Address> addresses = new UCMap<>(storedList.size());
        Integer locationId = null;
        for (StoredItem item : storedList) {
            if (item.getAddress()==null || item.getLocation()==null) {
                return null;
            }
            if (locationId==null) {
                locationId = item.getLocation().getId();
            } else if (!locationId.equals(item.getLocation().getId())) {
                return null;
            }
            addresses.put(item.getBarcode(), item.getAddress());
        }
        return addresses;
    }
}
