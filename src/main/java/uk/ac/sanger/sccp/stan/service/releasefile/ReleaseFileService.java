package uk.ac.sanger.sccp.stan.service.releasefile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.ComplexStainServiceImp;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * Service loading data for release files
 * @author dr6
 */
@Service
public class ReleaseFileService {
    Logger log = LoggerFactory.getLogger(ReleaseFileService.class);

    private final ReleaseRepo releaseRepo;
    private final SampleRepo sampleRepo;
    private final LabwareRepo labwareRepo;
    private final MeasurementRepo measurementRepo;
    private final SnapshotRepo snapshotRepo;
    private final Ancestoriser ancestoriser;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;
    private final LabwareNoteRepo lwNoteRepo;
    private final StainTypeRepo stainTypeRepo;
    private final SamplePositionRepo samplePositionRepo;
    private final ReagentActionDetailService reagentActionDetailService;

    @Autowired
    public ReleaseFileService(Ancestoriser ancestoriser,
                              SampleRepo sampleRepo, LabwareRepo labwareRepo, MeasurementRepo measurementRepo,
                              SnapshotRepo snapshotRepo, ReleaseRepo releaseRepo, OperationTypeRepo opTypeRepo,
                              OperationRepo opRepo, LabwareNoteRepo lwNoteRepo,
                              StainTypeRepo stainTypeRepo, SamplePositionRepo samplePositionRepo,
                              ReagentActionDetailService reagentActionDetailService) {
        this.releaseRepo = releaseRepo;
        this.sampleRepo = sampleRepo;
        this.labwareRepo = labwareRepo;
        this.measurementRepo = measurementRepo;
        this.snapshotRepo = snapshotRepo;
        this.ancestoriser = ancestoriser;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.stainTypeRepo = stainTypeRepo;
        this.samplePositionRepo = samplePositionRepo;
        this.reagentActionDetailService = reagentActionDetailService;
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

        final boolean includeStorageAddress = shouldIncludeStorageAddress(releases);

        List<ReleaseEntry> entries = releases.stream()
                .flatMap(r -> toReleaseEntries(r, samples, snapshots, includeStorageAddress))
                .collect(toList());

        loadLastSection(entries);
        Ancestry ancestry = findAncestry(entries);
        loadSources(entries, ancestry, mode);
        loadMeasurements(entries, ancestry);
        loadSectionDate(entries, ancestry);
        loadStains(entries, ancestry);
        loadReagentSources(entries);
        loadSamplePositions(entries);
        return new ReleaseFileContent(mode, entries);
    }

    public boolean shouldIncludeStorageAddress(Collection<Release> releases) {
        String consistentLocationBarcode = null;
        for (Release release : releases) {
            String locBarcode = release.getLocationBarcode();
            if (locBarcode==null || release.getStorageAddress()==null) {
                return false;
            }
            if (consistentLocationBarcode==null) {
                consistentLocationBarcode = locBarcode;
            } else if (!consistentLocationBarcode.equalsIgnoreCase(locBarcode)) {
                return false;
            }
        }
        return (consistentLocationBarcode!=null);
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
            sampleMap.putAll(sampleRepo.getMapByIdIn(sampleIds));
        }
        return sampleMap;
    }

    /**
     * Loads and returns a map of snapshot id to shapshot.
     * Errors if any snapshots cannot be found.
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
     * @param includeStorageAddress whether or not to fill in the storage address field
     * @return a stream of release entries
     */
    public Stream<ReleaseEntry> toReleaseEntries(final Release release, Map<Integer, Sample> sampleIdMap,
                                                 Map<Integer, Snapshot> snapshots, boolean includeStorageAddress) {
        final Labware labware = release.getLabware();
        final Map<Integer, Slot> slotIdMap = labware.getSlots().stream()
                .collect(toMap(Slot::getId, slot -> slot));
        final Address storageAddress = (includeStorageAddress ? release.getStorageAddress() : null);
        return snapshots.get(release.getSnapshotId()).getElements().stream()
                .map(el -> new ReleaseEntry(release.getLabware(), slotIdMap.get(el.getSlotId()),
                        sampleIdMap.get(el.getSampleId()), storageAddress));
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



    /**
     * The source for cdna is the first sample found in the ancestry that does not have biostate cDNA.
     * @param entry the release entry to find the source for
     * @param ancestry the ancestry of the slot/sample
     * @return the most recent (in terms of generation) slot/sample from the ancestry that is not cdna,
     *         or null if no such element is found
     */
    public SlotSample selectSourceForCDNA(ReleaseEntry entry, Ancestry ancestry) {
        for (SlotSample ss : ancestry.ancestors(new SlotSample(entry.getSlot(), entry.getSample()))) {
            if (!ss.getSample().getBioState().getName().equalsIgnoreCase("cDNA")) {
                return ss;
            }
        }
        return null;
    }

    /**
     * Converts the given operations into a map from (destination) labware id to operation,
     * using the latest operation for each labware (see {@link #opSupplants}).
     * @param ops the operations
     * @return a map from labware id to operation
     */
    public Map<Integer, Operation> labwareIdToOp(Collection<Operation> ops) {
        Map<Integer, Operation> labwareOps = new HashMap<>(); // Map of labware id to the latest stain op on that labware
        for (Operation op : ops) {
            Set<Integer> labwareIds = op.getActions().stream().map(a -> a.getDestination().getLabwareId()).collect(toSet());
            for (Integer labwareId : labwareIds) {
                if (opSupplants(op, labwareOps.get(labwareId))) {
                    labwareOps.put(labwareId, op);
                }
            }
        }
        return labwareOps;
    }

    /**
     * Selects the appropriate operation for each release entry, looking through the ancestry for each entry
     * and using the given map of labware id to operations.
     * @param entries the entries
     * @param labwareIdOps a map of labware id to operation on that labware id
     * @param ancestry the ancestry of slots/samples
     * @return a map giving the selected operation for each release entry
     */
    public Map<ReleaseEntry, Operation> findEntryOps(Collection<ReleaseEntry> entries,
                                                     Map<Integer, Operation> labwareIdOps,
                                                     Ancestry ancestry) {
        Map<ReleaseEntry, Operation> entryOps = new HashMap<>();
        for (ReleaseEntry entry : entries) {
            Operation op = selectOp(entry, labwareIdOps, ancestry);
            if (op!=null) {
                entryOps.put(entry, op);
            }
        }
        return entryOps;
    }

    /**
     * Finds the appropriate operation in the entry's ancestry
     * @param entry the entry
     * @param labwareOps a map of labware id to the relevant operation
     * @param ancestry the ancestry of the slots and samples
     * @return the appropriate operation for the given entry
     */
    public Operation selectOp(ReleaseEntry entry, Map<Integer, Operation> labwareOps, Ancestry ancestry) {
        for (SlotSample ss : ancestry.ancestors(new SlotSample(entry.getSlot(), entry.getSample()))) {
            Operation op = labwareOps.get(ss.getSlot().getLabwareId());
            if (op!=null) {
                return op;
            }
        }
        return null;
    }

    /**
     * Loads the section date for samples that have been sectioned
     * @param entries the release entries
     * @param ancestry the ancestry map
     */
    public void loadSectionDate(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Set<Integer> slotIds = ancestry.keySet().stream().map(ss -> ss.getSlot().getId()).collect(toSet());
        OperationType opType = opTypeRepo.getByName("Section");
        List<Operation> sectionOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        if (!sectionOps.isEmpty()) {
            Map<Integer, Operation> labwareSectionOp = labwareIdToOp(sectionOps);
            Map<ReleaseEntry, Operation> entrySectionOp = findEntryOps(entries, labwareSectionOp, ancestry);
            entrySectionOp.forEach((entry, op) -> {
                if (op != null) {
                    entry.setSectionDate(op.getPerformed().toLocalDate());
                }
            });
        }
    }

    /**
     * Loads info about stains on the labware or its antecedents.
     * @param entries the release entries
     * @param ancestry the ancestry map
     */
    public void loadStains(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Set<Integer> slotIds = ancestry.keySet().stream().map(ss -> ss.getSlot().getId()).collect(toSet());
        OperationType opType = opTypeRepo.getByName("Stain");
        List<Operation> stainOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        if (stainOps.isEmpty()) {
            return;
        }
        Map<Integer, Operation> labwareStainOp = labwareIdToOp(stainOps);
        Map<ReleaseEntry, Operation> entryStainOp = findEntryOps(entries, labwareStainOp, ancestry);
        if (entryStainOp.isEmpty()) {
            return;
        }
        Set<Integer> opIds = entryStainOp.values().stream().map(Operation::getId).collect(toSet());

        Map<Integer, String> stainOpTypes = stainTypeRepo.loadOperationStainTypes(opIds).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().stream().map(StainType::getName).collect(joining(", "))));

        Map<Integer, String> opBondBarcodes = new HashMap<>();
        Map<Integer, Integer> opRnaPlex = new HashMap<>();
        Map<Integer, Integer> opIhcPlex = new HashMap<>();

        for (var note : lwNoteRepo.findAllByOperationIdIn(opIds)) {
            final String name = note.getName();
            final Integer opId = note.getOperationId();
            final String value = note.getValue();
            if (name.equalsIgnoreCase(ComplexStainServiceImp.LW_NOTE_BOND_BARCODE)) {
                opBondBarcodes.put(opId, value);
            } else if (name.equalsIgnoreCase(ComplexStainServiceImp.LW_NOTE_PLEX_RNASCOPE)) {
                try {
                    opRnaPlex.put(opId, Integer.valueOf(value));
                } catch (NumberFormatException e) {
                    log.error("Should be an integer: "+note);
                }
            } else if (name.equalsIgnoreCase(ComplexStainServiceImp.LW_NOTE_PLEX_IHC)) {
                try {
                    opIhcPlex.put(opId, Integer.valueOf(value));
                } catch (NumberFormatException e) {
                    log.error("Should be an integer: "+note);
                }
            }
        }

        for (ReleaseEntry entry : entries) {
            Operation op = entryStainOp.get(entry);
            if (op!=null) {
                entry.setStainType(stainOpTypes.get(op.getId()));
                entry.setBondBarcode(opBondBarcodes.get(op.getId()));
                entry.setRnascopePlex(opRnaPlex.get(op.getId()));
                entry.setIhcPlex(opIhcPlex.get(op.getId()));
            }
        }
    }

    /**
     * Sets various measurements for the release entries.
     * The measurements may be recorded on the specified slot, or any ancestral slot
     * found through the given ancestry map.
     * @param entries the release entries
     * @param ancestry the ancestry map
     */
    public void loadMeasurements(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Set<Integer> slotIds = ancestry.keySet().stream().map(ss -> ss.getSlot().getId()).collect(toSet());
        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(slotIds);
        Map<Integer, List<Measurement>> slotIdToThickness = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToCoverage = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToCq = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToVisiumConc = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToPermTimes = new HashMap<>();
        final String THICKNESS = MeasurementType.Thickness.friendlyName();
        final String COVERAGE = MeasurementType.Tissue_coverage.friendlyName();
        final String CQ = MeasurementType.Cq_value.friendlyName();
        final String CDNA_CONC = MeasurementType.cDNA_concentration.friendlyName();
        final String LIBRARY_CONC = MeasurementType.Library_concentration.friendlyName();
        final String VISIUM_CONCENTRATION = "Visium Concentration";
        final String PERM_TIME= MeasurementType.Permeabilisation_time.friendlyName();
        final String VISIUM_TO = "Visium TO", VISIUM_LP = "Visium LP", PLATE_96 = "96 well plate";
        Map<Integer, OperationType> opTypeCache = new HashMap<>();

        for (Measurement measurement : measurements) {
            if (measurement.getOperationId()==null) {
                continue;
            }
            if (measurement.getName().equalsIgnoreCase(THICKNESS)) {
                List<Measurement> slotIdMeasurements = slotIdToThickness.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
                slotIdMeasurements.add(measurement);
            } else if (measurement.getName().equalsIgnoreCase(COVERAGE)) {
                List<Measurement> slotIdMeasurements = slotIdToCoverage.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
                slotIdMeasurements.add(measurement);
            } else if (measurement.getName().equalsIgnoreCase(CQ)) {
                List<Measurement> slotIdMeasurements = slotIdToCq.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
                slotIdMeasurements.add(measurement);
            } else if (measurement.getName().equalsIgnoreCase(CDNA_CONC) || measurement.getName().equalsIgnoreCase(LIBRARY_CONC)) {
                final Integer opId = measurement.getOperationId();
                OperationType opType = opTypeCache.get(opId);
                if (opType==null) {
                    Operation op = opRepo.findById(opId).orElseThrow();
                    opType = op.getOperationType();
                    opTypeCache.put(opId, opType);
                }
                if (opType.getName().equalsIgnoreCase(VISIUM_CONCENTRATION)) {
                    List<Measurement> slotIdMeasurements = slotIdToVisiumConc.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
                    slotIdMeasurements.add(measurement);
                }
            } else if (measurement.getName().equalsIgnoreCase(PERM_TIME)) {
                List<Measurement> slotIdMeasurements = slotIdToPermTimes.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
                slotIdMeasurements.add(measurement);
            }
        }
        for (ReleaseEntry entry : entries) {
            Measurement thicknessMeasurement = selectMeasurement(entry, slotIdToThickness, ancestry);
            if (thicknessMeasurement!=null) {
                entry.setSectionThickness(thicknessMeasurement.getValue());
            }
            Measurement coverageMeasurement = selectMeasurement(entry, slotIdToCoverage, ancestry);
            if (coverageMeasurement!=null) {
                try {
                    entry.setCoverage(Integer.valueOf(coverageMeasurement.getValue()));
                } catch (NumberFormatException e) {
                    log.error("Coverage measurement is not an integer: {}", coverageMeasurement);
                }
            }
            Measurement cqMeasurement = selectMeasurement(entry, slotIdToCq, ancestry);
            if (cqMeasurement != null) {
                try {
                    entry.setCq(Integer.valueOf(cqMeasurement.getValue()));
                } catch (NumberFormatException e) {
                    log.error("Cq measurement is not an integer: {}", cqMeasurement);
                }
            }
            Measurement concMeasurement = selectMeasurement(entry, slotIdToVisiumConc, ancestry);
            if (concMeasurement != null) {
                entry.setVisiumConcentration(concMeasurement.getValue());
                if (concMeasurement.getName().equalsIgnoreCase(CDNA_CONC)) {
                    entry.setVisiumConcentrationType("cDNA");
                } else if (concMeasurement.getName().equalsIgnoreCase(LIBRARY_CONC)) {
                    entry.setVisiumConcentrationType("Library");
                }
            }

            String labwareTypeName = entry.getLabware().getLabwareType().getName();

            if (labwareTypeName.equalsIgnoreCase(VISIUM_TO) || labwareTypeName.equalsIgnoreCase(VISIUM_LP)) {
                //Retrieving measurements only from released labware (not from ancestors)
                List<Measurement> permMeasurements = slotIdToPermTimes.get(entry.getSlot().getId());
                if (permMeasurements!=null && !permMeasurements.isEmpty()) {
                    var optMeasurement = permMeasurements.stream()
                            .filter(m -> m.getSampleId().equals(entry.getSample().getId()))
                            .findAny();
                    if (optMeasurement.isPresent()) {
                        Measurement permMeasurementResult = optMeasurement.get();
                        try {
                            entry.setPermTime(toMinutes(permMeasurementResult.getValue()));
                        } catch (NumberFormatException e) {
                            log.error("Permeabilisation time measurement is not an integer: {}", permMeasurementResult.getValue());
                        }
                    }
                }
            } else if (labwareTypeName.equalsIgnoreCase(PLATE_96)) {
                Measurement permMeasurement = selectMeasurement(entry, slotIdToPermTimes, ancestry);
                if (permMeasurement != null) {
                    try {
                        entry.setPermTime(toMinutes(permMeasurement.getValue()));
                    } catch (NumberFormatException e) {
                        log.error("Permeabilisation time measurement is not an integer: {}", permMeasurement);
                    }
                }
            }
        }
    }

    public static String toMinutes(String secondsValue) {
        int sec = Integer.parseInt(secondsValue);
        if (sec==0) {
            return "0 min";
        }
        int min = sec / 60;
        sec %= 60;
        if (min==0) {
            return sec+" sec";
        }
        if (sec==0) {
            return min+" min";
        }
        return String.format("%d min, %d sec", min, sec);
    }

    public void loadReagentSources(Collection<ReleaseEntry> entries) {
        Set<Integer> slotIds = entries.stream()
                .map(e -> e.getSlot().getId())
                .collect(toSet());
        var radMap = reagentActionDetailService.loadReagentTransfersForSlotIds(slotIds);
        if (!radMap.isEmpty()) {
            for (var entry : entries) {
                var rads = radMap.get(entry.getSlot().getId());
                if (rads!=null && !rads.isEmpty()) {
                    String radString = rads.stream()
                            .map(rad -> rad.reagentPlateBarcode+" : "+rad.reagentSlotAddress)
                            .distinct()
                            .collect(Collectors.joining(", "));
                    entry.setReagentSource(radString);
                    String radTypeString = rads.stream()
                            .map(rad -> rad.reagentPlateType)
                            .distinct()
                            .collect(joining(", "));
                    entry.setReagentPlateType(radTypeString);
                }
            }
        }
    }

    /**
     * Loads sample positions for the given entries
     */
    public void loadSamplePositions(Collection<ReleaseEntry> entries) {
        Set<Integer> slotIds = entries.stream()
                .map(e -> e.getSlot().getId())
                .collect(toSet());
        Map<SlotIdSampleId, String> positionMap = samplePositionRepo.findAllBySlotIdIn(slotIds).stream()
                .collect(toMap(sp -> new SlotIdSampleId(sp.getSlotId(), sp.getSampleId()), sp -> sp.getSlotRegion().getName()));
        for (ReleaseEntry entry : entries) {
            entry.setSamplePosition(positionMap.get(new SlotIdSampleId(entry.getSlot().getId(), entry.getSample().getId())));
        }
    }

    /**
     * Finds a particular measurement from the given set of information
     * @param entry the release entry we want the measurement for
     * @param slotIdToMeasurement a map of each slot id to all its relevant measurements
     * @param ancestry the ancestry of that tells us the slot sample history
     * @return the appropriate measurement, or null if none was found
     */
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
     * Gets the latest op of the given type on each specified labware (as a destination)
     * @param opType the type of op we are looking for
     * @param labwareIds the ids of the labware we are interested in
     * @return a map of labware id to the latest op of that type (if any)
     */
    public Map<Integer, Operation> loadLastOpMap(OperationType opType, Set<Integer> labwareIds) {
        Map<Integer, Operation> labwareStainOp = new HashMap<>(labwareIds.size());
        for (Operation op : opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, labwareIds)) {
            for (Action ac : op.getActions()) {
                int labwareId = ac.getDestination().getLabwareId();
                if (opSupplants(op, labwareStainOp.get(labwareId))) {
                    labwareStainOp.put(labwareId, op);
                }
            }
        }
        return labwareStainOp;
    }

    /**
     * Does op <tt>newOp</tt> supplant op <tt>savedOp</tt>?
     * It does if <tt>savedOp</tt> is null, or <tt>newOp</tt> is later than <tt>savedOp</tt>,
     * or if they have the same timestamp but <tt>newOp</tt> has a higher id.
     * @param newOp new op
     * @param savedOp previously saved op, if any
     * @return true if <tt>newOp</tt> takes precedence over <tt>savedOp</tt>>
     */
    static boolean opSupplants(Operation newOp, Operation savedOp) {
        if (savedOp==null) {
            return true;
        }
        int d = newOp.getPerformed().compareTo(savedOp.getPerformed());
        return (d > 0 || d==0 && newOp.getId() > savedOp.getId());
    }

    /**
     * Loads the stain types for the given operations.
     * @param lwOps map to operations
     * @return a map from operation id to stain types
     */
    public Map<Integer, List<StainType>> loadStainTypes(Map<Integer, Operation> lwOps) {
        Set<Integer> opIds = lwOps.values().stream().map(Operation::getId).collect(toSet());
        return stainTypeRepo.loadOperationStainTypes(opIds);
    }
}
