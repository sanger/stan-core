package uk.ac.sanger.sccp.stan.service.releasefile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
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
        Map<SlotSample, SlotSample> ancestry = findAncestry(entries);
        loadOriginalBarcodes(entries, ancestry);
        loadSectionThickness(entries, ancestry);
        return entries;
    }

    public Map<SlotSample, SlotSample> findAncestry(Collection<ReleaseEntry> entries) {
        Set<SlotSample> entrySlotSamples = entries.stream()
                .map(entry -> new SlotSample(entry.getSlot(), entry.getSample()))
                .collect(toSet());
        return ancestoriser.findAncestry(entrySlotSamples);
    }

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

    public Stream<ReleaseEntry> toReleaseEntries(final Release release, Map<Integer, Sample> sampleIdMap) {
        final Labware labware = release.getLabware();
        final Map<Integer, Slot> slotIdMap = labware.getSlots().stream()
                .collect(toMap(Slot::getId, slot -> slot));
        return release.getDetails().stream()
                .map(rd -> new ReleaseEntry(release.getLabware(), slotIdMap.get(rd.getSlotId()), sampleIdMap.get(rd.getSampleId())));
    }

    public List<Release> getReleases(Collection<Integer> releaseIds) {
        return releaseRepo.getAllByIdIn(releaseIds);
    }

    public void loadOriginalBarcodes(Collection<ReleaseEntry> entries, Map<SlotSample, SlotSample> ancestry) {
        Map<Integer, String> labwareIdBarcode = new HashMap<>();
        for (ReleaseEntry entry : entries) {
            Labware lw = entry.getLabware();
            labwareIdBarcode.put(lw.getId(), lw.getBarcode());
        }
        for (ReleaseEntry entry : entries) {
            SlotSample slotSample = new SlotSample(entry.getSlot(), entry.getSample());
            SlotSample firstSlotSample = slotSample;
            SlotSample nextSlotSample = ancestry.get(firstSlotSample);
            while (nextSlotSample!=null) {
                firstSlotSample = nextSlotSample;
                nextSlotSample = ancestry.get(firstSlotSample);
            }
            Integer lwId = slotSample.getSlot().getLabwareId();
            String bc = labwareIdBarcode.get(lwId);
            if (bc==null) {
                bc = labwareRepo.getById(lwId).getBarcode();
                labwareIdBarcode.put(lwId, bc);
            }
            entry.setOriginalBarcode(bc);
        }
    }

    public void loadSectionThickness(Collection<ReleaseEntry> entries, Map<SlotSample, SlotSample> ancestry) {
        Set<Integer> slotIds = Stream.concat(ancestry.keySet().stream(), ancestry.values().stream())
                .map(ss -> ss.getSlot().getId())
                .collect(toSet());
        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(slotIds);
        Map<Integer, List<Measurement>> slotIdToMeasurement = new HashMap<>();
        for (Measurement measurement : measurements) {
            if (measurement.getOperationId()!=null && measurement.getName().equalsIgnoreCase("Thickness")) {
                List<Measurement> slotIdMeasurements = slotIdToMeasurement.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
                slotIdMeasurements.add(measurement);
            }
        }
        for (ReleaseEntry entry : entries) {
            Measurement measurement = getMeasurement(entry, slotIdToMeasurement, ancestry);
            if (measurement!=null) {
                entry.setSectionThickness(measurement.getValue());
            }
        }
    }

    private Measurement getMeasurement(ReleaseEntry entry, Map<Integer, List<Measurement>> slotIdToMeasurement,
                                       Map<SlotSample, SlotSample> ancestry) {
        SlotSample slotSample = new SlotSample(entry.getSlot(), entry.getSample());
        while (slotSample!=null) {
            List<Measurement> measurements = slotIdToMeasurement.get(slotSample.getSlot().getId());
            if (measurements!=null && !measurements.isEmpty()) {
                final Integer sampleId = slotSample.getSample().getId();
                var optMeasurement = measurements.stream()
                        .filter(m -> m.getSampleId().equals(sampleId))
                        .findAny();
                if (optMeasurement.isPresent()) {
                    return optMeasurement.get();
                }
            }
            slotSample = ancestry.get(slotSample);
        }
        return null;
    }
}
