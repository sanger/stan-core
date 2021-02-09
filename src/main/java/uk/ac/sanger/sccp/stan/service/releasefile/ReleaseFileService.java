package uk.ac.sanger.sccp.stan.service.releasefile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * Service for helping with release files
 * @author dr6
 */
@Service
public class ReleaseFileService {
    private final ReleaseRepo releaseRepo;
    private final SampleRepo sampleRepo;
    private final LabwareRepo labwareRepo;
    private final MeasurementRepo measurementRepo;
    private final Ancestoriser ancestoriser;

    @Autowired
    public ReleaseFileService(ReleaseRepo releaseRepo, SampleRepo sampleRepo, LabwareRepo labwareRepo,
                              MeasurementRepo measurementRepo, Ancestoriser ancestoriser) {
        this.releaseRepo = releaseRepo;
        this.sampleRepo = sampleRepo;
        this.labwareRepo = labwareRepo;
        this.measurementRepo = measurementRepo;
        this.ancestoriser = ancestoriser;
    }

    /**
     * Gets the entries which become rows in a release file.
     * Each release maps to 1 or more release entries.
     * @param releaseIds the ids of the releases
     * @return the release entries from the specified releases
     */
    public List<ReleaseEntry> getReleaseEntries(Collection<Integer> releaseIds) {
        if (releaseIds.isEmpty()) {
            return List.of();
        }
        List<Release> releases = getReleases(releaseIds);

        Map<Integer, Sample> samples = loadSamples(releases);

        List<ReleaseEntry> entries = releases.stream()
                .flatMap(r -> toReleaseEntries(r, samples))
                .collect(toList());

        loadLastSection(entries);
        Ancestry ancestry = findAncestry(entries);
        loadOriginalBarcodes(entries, ancestry);
        loadSectionThickness(entries, ancestry);
        return entries;
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
     * @return a map of sample id to sample
     */
    public Map<Integer, Sample> loadSamples(Collection<Release> releases) {
        final Map<Integer, Sample> sampleMap = new HashMap<>();
        Consumer<Sample> addSample = sam -> sampleMap.put(sam.getId(), sam);
        releases.stream()
                .flatMap(r -> r.getLabware().getSlots().stream())
                .flatMap(slot -> slot.getSamples().stream())
                .forEach(addSample);
        Set<Integer> sampleIds = releases.stream()
                .flatMap(r -> r.getDetails().stream().map(ReleaseDetail::getSampleId))
                .filter(sid -> !sampleMap.containsKey(sid))
                .collect(toSet());
        if (!sampleIds.isEmpty()) {
            sampleRepo.getAllByIdIn(sampleIds).forEach(addSample);
        }
        return sampleMap;
    }

    /**
     * Figures and sets the last section field for release entries.
     * The last section is only set on entries that specify a block.
     * The last section is the maximum of the {@link Slot#getBlockHighestSection} highest section
     * listed for the slot, and the highest section number created from the tissue
     * (for whichever of those two are non-null).
     * @param entries the release entries under construction
     */
    public void loadLastSection(Collection<ReleaseEntry> entries) {
        Map<Integer, Integer> tissueIdToMaxSection = new HashMap<>();
        for (ReleaseEntry entry : entries) {
            if (!entry.getSlot().isBlock() || !entry.getSlot().getBlockSampleId().equals(entry.getSample().getId())) {
                continue;
            }
            Integer tissueId = entry.getSample().getTissue().getId();
            Integer tissueMaxSection = tissueIdToMaxSection.get(tissueId);
            if (tissueMaxSection==null && !tissueIdToMaxSection.containsKey(tissueId)) {
                OptionalInt opMaxSection = sampleRepo.findMaxSectionForTissueId(tissueId);
                tissueMaxSection = (opMaxSection.isPresent() ? (Integer) opMaxSection.getAsInt() : null);
                tissueIdToMaxSection.put(tissueId, tissueMaxSection);
            }
            Integer maxSection = entry.getSlot().getBlockHighestSection();
            if (maxSection==null || tissueMaxSection!=null && tissueMaxSection > maxSection) {
                maxSection = tissueMaxSection;
            }
            entry.setLastSection(maxSection);
        }
    }

    /**
     * Creates release entries for a given release.
     * One entry per slot/sample in combination.
     * @param release the release
     * @param sampleIdMap a map to look up samples in
     * @return a stream of release entries
     */
    public Stream<ReleaseEntry> toReleaseEntries(final Release release, Map<Integer, Sample> sampleIdMap) {
        final Labware labware = release.getLabware();
        final Map<Integer, Slot> slotIdMap = labware.getSlots().stream()
                .collect(toMap(Slot::getId, slot -> slot));
        return release.getDetails().stream()
                .map(rd -> new ReleaseEntry(release.getLabware(), slotIdMap.get(rd.getSlotId()), sampleIdMap.get(rd.getSampleId())));
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
            entry.setOriginalBarcode(bc);
        }
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
}
