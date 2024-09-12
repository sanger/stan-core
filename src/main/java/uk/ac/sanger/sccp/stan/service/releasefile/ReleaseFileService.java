package uk.ac.sanger.sccp.stan.service.releasefile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FlagDetail;
import uk.ac.sanger.sccp.stan.service.ComplexStainServiceImp;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupService;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService;
import uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Service loading data for release files
 * @author dr6
 */
@Service
public class ReleaseFileService {
    public enum StorageDetail {
        NONE, NAME, BARCODE
    }

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
    private final OperationCommentRepo opComRepo;
    private final LabwareProbeRepo lwProbeRepo;
    private final RoiRepo roiRepo;
    private final ReagentActionDetailService reagentActionDetailService;
    private final SolutionRepo solutionRepo;
    private final OperationSolutionRepo opSolRepo;
    private final ResultOpRepo roRepo;
    private final FlagLookupService flagLookupService;

    @Autowired
    public ReleaseFileService(Ancestoriser ancestoriser,
                              SampleRepo sampleRepo, LabwareRepo labwareRepo, MeasurementRepo measurementRepo,
                              SnapshotRepo snapshotRepo, ReleaseRepo releaseRepo, OperationTypeRepo opTypeRepo,
                              OperationRepo opRepo, LabwareNoteRepo lwNoteRepo,
                              StainTypeRepo stainTypeRepo, SamplePositionRepo samplePositionRepo,
                              OperationCommentRepo opComRepo,
                              LabwareProbeRepo lwProbeRepo, RoiRepo roiRepo,
                              ReagentActionDetailService reagentActionDetailService,
                              SolutionRepo solutionRepo, OperationSolutionRepo opSolRepo, ResultOpRepo roRepo,
                              FlagLookupService flagLookupService) {
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
        this.opComRepo = opComRepo;
        this.lwProbeRepo = lwProbeRepo;
        this.roiRepo = roiRepo;
        this.reagentActionDetailService = reagentActionDetailService;
        this.solutionRepo = solutionRepo;
        this.opSolRepo = opSolRepo;
        this.roRepo = roRepo;
        this.flagLookupService = flagLookupService;
    }

    /**
     * Gets the entries which become rows in a release file.
     * Each release maps to 1 or more release entries.
     * @param releaseIds the ids of the releases
     * @return the release entries from the specified releases
     */
    public ReleaseFileContent getReleaseFileContent(Collection<Integer> releaseIds, Set<ReleaseFileOption> options) {
        if (releaseIds.isEmpty()) {
            return new ReleaseFileContent(EnumSet.of(ReleaseFileMode.NORMAL), List.of(), options);
        }
        List<Release> releases = getReleases(releaseIds);
        Map<Integer, Snapshot> snapshots = loadSnapshots(releases);
        Map<Integer, Sample> samples = loadSamples(releases, snapshots);
        Set<ReleaseFileMode> modes = checkModes(samples.values());

        final StorageDetail detail = storageDetail(releases);

        List<ReleaseEntry> entries = releases.stream()
                .flatMap(r -> toReleaseEntries(r, samples, snapshots, detail))
                .collect(toList());

        loadLastSection(entries);
        Ancestry ancestry = findAncestry(entries);
        Set<Integer> slotIds = entries.stream()
                .map(ReleaseEntry::getSlot)
                .map(s -> s==null ? null : s.getId())
                .filter(Objects::nonNull)
                .collect(toSet());
        loadSources(entries, ancestry, modes);
        loadMeasurements(entries, ancestry);
        loadSectionDate(entries, ancestry);
        loadStains(entries, ancestry);
        loadReagentSources(entries);
        loadSamplePositions(entries);
        loadSectionComments(entries);
        loadSolutions(entries);
        if (options.contains(ReleaseFileOption.Visium)) {
            loadVisiumBarcodes(entries, ancestry);
        }

        loadXeniumFields(entries, slotIds);
        loadFlags(entries);
        return new ReleaseFileContent(modes, entries, options);
    }

    /**
     * What level of detail should be included about storage locations?
     * <ul>
     *   <li>If none of the labware has a storage address, {@link StorageDetail#NONE NONE}.
     *   <li>If the labware with addresses can have their locations identified by name, {@link StorageDetail#NAME NAME}.
     *   <li>If the location names are inadequate to distinguish them, {@link StorageDetail#BARCODE BARCODE}
     * </ul>
     * @param releases the info about the releases
     * @return the level of storage detail to include
     */
    public StorageDetail storageDetail(Collection<Release> releases) {
        boolean anyAddress = false;
        UCMap<String> nameToBarcode = new UCMap<>();
        for (Release release : releases) {
            if (nullOrEmpty(release.getStorageAddress())) {
                continue;
            }
            anyAddress = true;
            String name = release.getLocationName();
            if (nullOrEmpty(name)) {
                return StorageDetail.BARCODE;
            }
            String bc = release.getLocationBarcode();
            String previousBc = nameToBarcode.get(name);
            if (previousBc==null) {
                nameToBarcode.put(name, bc);
            } else if (!previousBc.equalsIgnoreCase(bc)) {
                return StorageDetail.BARCODE;
            }
        }
        return anyAddress ? StorageDetail.NAME : StorageDetail.NONE;
    }

    /**
     * Checks whether the release contains cdna and/or normal samples.
     * @param samples the samples
     * @return the modes for the samples
     */
    public Set<ReleaseFileMode> checkModes(Collection<Sample> samples) {
        EnumSet<ReleaseFileMode> modes = samples.stream()
                .map(this::mode)
                .collect(toEnumSet(ReleaseFileMode.class));
        if (modes.isEmpty()) {
            modes.add(ReleaseFileMode.NORMAL);
        }
        return modes;
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
                    .toList();
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
     * @param detail level of detail to include about storage
     * @return a stream of release entries
     */
    public Stream<ReleaseEntry> toReleaseEntries(final Release release, Map<Integer, Sample> sampleIdMap,
                                                 Map<Integer, Snapshot> snapshots, StorageDetail detail) {
        final Labware labware = release.getLabware();
        final Map<Integer, Slot> slotIdMap = labware.getSlots().stream()
                .collect(toMap(Slot::getId, slot -> slot));
        final String storageAddress = detail==StorageDetail.NONE ? null : release.getStorageAddress();
        final String locationName;
        if (nullOrEmpty(storageAddress)) {
            locationName = null;
        } else {
            locationName = switch (detail) {
                //noinspection DataFlowIssue
                case NONE -> null;
                case NAME -> release.getLocationName();
                case BARCODE -> joinNullStrings(release.getLocationName(), release.getLocationBarcode(), " ");
            };
        }
        return snapshots.get(release.getSnapshotId()).getElements().stream()
                .map(el -> new ReleaseEntry(release.getLabware(), slotIdMap.get(el.getSlotId()),
                        sampleIdMap.get(el.getSampleId()), storageAddress, locationName));
    }

    /**
     * Joins two strings, omitting null or empty
     * @param a the first string
     * @param b the second string
     * @param joint the string to use to join the two strings
     * @return a combined string
     */
    private static String joinNullStrings(String a, String b, String joint) {
        return (nullOrEmpty(a) ? b : nullOrEmpty(b) ? a : (a + joint + b));
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
     * @param modes the release file mode
     */
    public void loadSources(Collection<ReleaseEntry> entries, Ancestry ancestry, Set<ReleaseFileMode> modes) {
        if (modes.contains(ReleaseFileMode.CDNA)) {
            loadSourcesForCDNA(entries, ancestry);
        }
        if (modes.contains(ReleaseFileMode.NORMAL)) {
            loadOriginalBarcodes(entries, ancestry);
        }
    }

    /**
     * Loads the most recent visium barcode for each entry
     * @param entries the release entries
     * @param ancestry the ancestry map
     */
    public void loadVisiumBarcodes(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Set<Integer> labwareIds = ancestry.keySet().stream()
                .map(ss -> ss.slot().getLabwareId())
                .collect(toSet());
        Map<Integer, Labware> idToLabware = labwareRepo.findAllByIdIn(labwareIds).stream().collect(inMap(Labware::getId));
        for (ReleaseEntry entry : entries) {
            entry.setVisiumBarcode(visiumBarcode(entry, ancestry, idToLabware));
        }
    }

    /**
     * Finds the most recent visium barcode for the given entry
     * @param entry the release entry
     * @param ancestry the ancestry map
     * @param idToLabware map to look up labware from its id
     * @return the most recent visium barcode; or null
     */
    public String visiumBarcode(ReleaseEntry entry, Ancestry ancestry, Map<Integer, Labware> idToLabware) {
        if (entry.getSlot() != null && entry.getSample() != null) {
            SlotSample last = new SlotSample(entry.getSlot(), entry.getSample());
            for (SlotSample ss : ancestry.ancestors(last)) {
                Labware lw = idToLabware.get(ss.slot().getLabwareId());
                if (lw != null && lw.getExternalBarcode() != null && lw.getLabwareType().isPrebarcoded()
                        && containsIgnoreCase(lw.getLabwareType().getName(), "visium")) {
                    return lw.getExternalBarcode();
                }
            }
        }
        return null;
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
            if (mode(entry.getSample())==ReleaseFileMode.CDNA) {
                continue;
            }
            SlotSample slotSample = new SlotSample(entry.getSlot(), entry.getSample());
            Set<SlotSample> roots = ancestry.getRoots(slotSample);
            SlotSample root = roots.stream()
                    .min(Comparator.naturalOrder())
                    .orElse(slotSample);
            Integer lwId = root.slot().getLabwareId();
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
            if (mode(entry.getSample()) != ReleaseFileMode.CDNA) {
                continue;
            }
            SlotSample ss = selectSourceForCDNA(entry, ancestry);
            if (ss!=null) {
                Slot slot = ss.slot();
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

    public void loadSolutions(Collection<ReleaseEntry> entries) {
        Set<Integer> lwIds = entries.stream()
                .map(re -> re.getLabware().getId())
                .collect(toSet());
        final List<OperationSolution> allOpSols = opSolRepo.findAllByLabwareIdIn(lwIds);
        if (allOpSols.isEmpty()) {
            return;
        }
        final Set<Integer> solutionIds = allOpSols.stream().map(OperationSolution::getSolutionId).collect(toSet());
        Map<Integer, Solution> idSolutions = stream(solutionRepo.findAllById(solutionIds))
                .collect(inMap(Solution::getId));

        Map<Integer, List<OperationSolution>> lwSols = allOpSols.stream()
                .collect(groupingBy(OperationSolution::getLabwareId));
        for (ReleaseEntry entry : entries) {
            List<OperationSolution> opSols = lwSols.get(entry.getLabware().getId());
            if (nullOrEmpty(opSols)) {
                continue;
            }
            OperationSolution opSol;
            if (entry.getSample()==null) {
                opSol = opSols.getFirst();
            } else {
                Integer sampleId = entry.getSample().getId();
                opSol = opSols.stream()
                        .filter(os -> sampleId.equals(os.getSampleId()))
                        .findFirst()
                        .orElse(null);
            }
            if (opSol != null) {
                entry.setSolution(idSolutions.get(opSol.getSolutionId()).getName());
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
            if (!ss.sample().getBioState().getName().equalsIgnoreCase("cDNA")) {
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
            Set<Integer> labwareIds = op.getActions().stream()
                    .map(a -> a.getDestination().getLabwareId())
                    .collect(toSet());
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
            Operation op = labwareOps.get(ss.slot().getLabwareId());
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
        Set<Integer> slotIds = ancestry.keySet().stream().map(SlotSample::slotId).collect(toSet());
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

        Map<SlotIdSampleId, LocalDate> slotSampleSectionDates = findSlotSampleDates(
                measurementRepo.findAllBySlotIdInAndName(slotIds, "Date sectioned")
        );
        if (!slotSampleSectionDates.isEmpty()) {
            for (ReleaseEntry entry : entries) {
                if (entry.getSlot() != null && entry.getSample() != null && entry.getSectionDate() == null) {
                    entry.setSectionDate(findEntrySectionDate(entry, slotSampleSectionDates, ancestry));
                }
            }
        }
    }

    /**
     * Loads the section date for the given entry from the given map using the given ancestry
     * @param entry the entry to load the date for
     * @param slotSampleSectionDates map of slot/sample ids to section date
     * @param ancestry the ancestry for the slot/samples
     * @return the section date for the given entry
     */
    public LocalDate findEntrySectionDate(ReleaseEntry entry, Map<SlotIdSampleId, LocalDate> slotSampleSectionDates,
                                          Ancestry ancestry) {
        SlotSample entrySs = new SlotSample(entry.getSlot(), entry.getSample());
        for (SlotSample ss : ancestry.ancestors(entrySs)) {
            LocalDate date = slotSampleSectionDates.get(new SlotIdSampleId(ss.slotId(), ss.sampleId()));
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    /**
     * Builds a map from slot/sample ids to the localdate in the given measurements
     * @param measurements the measurements to look through
     * @return a map from slot/sample ids to the relevant date in the measurements
     */
    public Map<SlotIdSampleId, LocalDate> findSlotSampleDates(Collection<Measurement> measurements) {
        if (measurements.isEmpty()) {
            return Map.of();
        }
        Map<SlotIdSampleId, LocalDate> map = new HashMap<>(measurements.size());
        for (Measurement measurement : measurements) {
            if (measurement.getSampleId()==null || measurement.getSlotId()==null || measurement.getValue()==null) {
                continue;
            }
            LocalDate value;
            try {
                value = LocalDate.parse(measurement.getValue());
            } catch (DateTimeParseException e) {
                continue;
            }
            SlotIdSampleId key = new SlotIdSampleId(measurement.getSlotId(), measurement.getSampleId());
            LocalDate oldValue = map.get(key);
            if (oldValue==null || oldValue.isBefore(value)) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Loads the various fields associated with xenium ops
     * @param entries the release entries under construction
     * @param slotIds the ids of slots for these entries
     */
    public void loadXeniumFields(Collection<ReleaseEntry> entries, Set<Integer> slotIds) {
        loadProbeHybridisation(entries, slotIds);
        loadProbeHybridisationQC(entries, slotIds);
        loadXeniumAnalyser(entries, slotIds);
        loadXeniumQC(entries, slotIds);
    }

    /**
     * Loads timestamps and probe info for probe hybridisation ops
     */
    public void loadProbeHybridisation(Collection<ReleaseEntry> entries, Set<Integer> slotIds) {
        OperationType opType = opTypeRepo.getByName("Probe hybridisation Xenium");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        if (ops.isEmpty()) {
            return;
        }
        List<Integer> opIds = ops.stream().map(Operation::getId).collect(toList());
        List<LabwareProbe> lwProbes = lwProbeRepo.findAllByOperationIdIn(opIds);
        Map<Integer, List<LabwareProbe>> opIdProbes = lwProbes.stream()
                .collect(groupingBy(LabwareProbe::getOperationId));
        Map<Integer, Operation> labwareProbeOp = labwareIdToOp(ops);
        for (ReleaseEntry entry : entries) {
            final Integer lwId = entry.getLabware().getId();
            Operation op = labwareProbeOp.get(lwId);
            if (op==null) {
                continue;
            }
            entry.setHybridStart(op.getPerformed());
            List<LabwareProbe> probes = opIdProbes.get(op.getId());
            if (nullOrEmpty(probes)) {
                continue;
            }
            probes = probes.stream().filter(p -> p.getLabwareId().equals(lwId))
                    .toList();
            if (!probes.isEmpty()) {
                String plex = probes.stream()
                        .map(LabwareProbe::getPlex)
                        .distinct()
                        .map(String::valueOf)
                        .collect(joining(", "));
                String probeName = probes.stream()
                        .map(lp -> lp.getProbePanel().getName())
                        .distinct()
                        .collect(joining(", "));
                String lot = probes.stream()
                        .map(LabwareProbe::getLotNumber)
                        .distinct()
                        .collect(joining(", "));
                entry.setXeniumPlex(plex);
                entry.setXeniumProbe(probeName);
                entry.setXeniumProbeLot(lot);
            }
        }
    }

    /**
     * Loads timestamps and comments for probe hybridisation qc
     */
    public void loadProbeHybridisationQC(Collection<ReleaseEntry> entries, Set<Integer> slotIds) {
        OperationType opType = opTypeRepo.getByName("Probe hybridisation QC");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        if (ops.isEmpty()) {
            return;
        }
        List<Integer> opIds = ops.stream().map(Operation::getId).collect(toList());
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(opIds);
        Map<Integer, List<OperationComment>> opIdComs = opcoms.stream()
                .collect(groupingBy(OperationComment::getOperationId));
        Map<Integer, Operation> lwOp = labwareIdToOp(ops);
        for (ReleaseEntry entry : entries) {
            final Integer lwId = entry.getLabware().getId();
            Operation op = lwOp.get(lwId);
            if (op==null) {
                continue;
            }
            entry.setHybridEnd(op.getPerformed());
            if (!nullOrEmpty(opIdComs.get(op.getId()))) {
                String comment = joinComments(opIdComs.get(op.getId()).stream()
                        .filter(oc -> lwId.equals(oc.getLabwareId())));
                if (!nullOrEmpty(comment)) {
                    entry.setHybridComment(comment);
                }
            }
        }
    }

    /**
     * Loads timestamps, ROI and labware notes from Xenium analyser ops.
     */
    public void loadXeniumAnalyser(Collection<ReleaseEntry> entries, Set<Integer> slotIds) {
        OperationType opType = opTypeRepo.getByName("Xenium analyser");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        if (ops.isEmpty()) {
            return;
        }
        List<Integer> opIds = ops.stream().map(Operation::getId).collect(toList());
        Map<Integer, Operation> lwOp = labwareIdToOp(ops);
        Map<Integer, List<LabwareNote>> opIdNotes = lwNoteRepo.findAllByOperationIdIn(opIds).stream()
                .collect(groupingBy(LabwareNote::getOperationId));
        Map<Integer, List<Roi>> opIdRoi = roiRepo.findAllByOperationIdIn(opIds).stream()
                .collect(groupingBy(Roi::getOperationId));
        for (ReleaseEntry entry : entries) {
            final Integer lwId = entry.getLabware().getId();
            Operation op = lwOp.get(lwId);
            if (op==null) {
                continue;
            }
            entry.setXeniumStart(op.getPerformed());
            List<Roi> rois = opIdRoi.get(op.getId());
            if (!nullOrEmpty(rois) && entry.getSlot()!=null && entry.getSample()!=null) {
                rois.stream()
                        .filter(roi -> roi.getSampleId().equals(entry.getSample().getId()) && roi.getSlotId().equals(entry.getSlot().getId()))
                        .findAny()
                        .ifPresent(roi -> entry.setXeniumRoi(roi.getRoi()));
            }
            List<LabwareNote> notes = opIdNotes.get(op.getId());
            if (nullOrEmpty(notes)) {
                continue;
            }
            for (LabwareNote note : notes) {
                if (!lwId.equals(note.getLabwareId())) {
                    continue;
                }
                if (note.getName().equalsIgnoreCase(AnalyserServiceImp.LOT_A_NAME)) {
                    entry.setXeniumReagentALot(note.getValue());
                } else if (note.getName().equalsIgnoreCase(AnalyserServiceImp.LOT_B_NAME)) {
                    entry.setXeniumReagentBLot(note.getValue());
                } else if (note.getName().equalsIgnoreCase(AnalyserServiceImp.POSITION_NAME)) {
                    entry.setXeniumCassettePosition(note.getValue());
                } else if (note.getName().equalsIgnoreCase(AnalyserServiceImp.RUN_NAME)) {
                    entry.setXeniumRun(note.getValue());
                }
            }
            if (op.getEquipment() != null) {
                entry.setEquipment(op.getEquipment().getName());
            }
        }
    }

    /**
     * Loads timestamps and comments from Xenium QC ops.
     */
    public void loadXeniumQC(Collection<ReleaseEntry> entries, Set<Integer> slotIds) {
        OperationType opType = opTypeRepo.getByName("Xenium QC");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        if (ops.isEmpty()) {
            return;
        }
        List<Integer> opIds = ops.stream().map(Operation::getId).collect(toList());
        Map<Integer, List<OperationComment>> opIdComs = opComRepo.findAllByOperationIdIn(opIds).stream()
                .collect(groupingBy(OperationComment::getOperationId));
        Map<Integer, Operation> lwIdOps = labwareIdToOp(ops);
        for (ReleaseEntry entry : entries) {
            Integer lwId = entry.getLabware().getId();
            Operation op = lwIdOps.get(lwId);
            if (op==null) {
                continue;
            }
            entry.setXeniumEnd(op.getPerformed());
            List<OperationComment> opcoms = opIdComs.get(op.getId());
            if (nullOrEmpty(opcoms)) {
                continue;
            }
            Stream<String> commentDescs = opcoms.stream()
                    .map(oc -> commentDescOrNull(oc, entry))
                    .filter(Objects::nonNull);
            entry.setXeniumComment(joinCommentDescs(commentDescs));
        }
    }

    /**
     * Describes the given comment if it is applicable to the given entry. If it is not applicable, returns null.
     * @param opcom the op-com
     * @param entry the release entry
     * @return a string describing the comment, or null
     */
    public String commentDescOrNull(OperationComment opcom, ReleaseEntry entry) {
        if (opcom.getLabwareId()!=null && !opcom.getLabwareId().equals(entry.getLabware().getId())) {
            return null;
        }
        if (opcom.getSampleId()!=null && entry.getSample()!=null && !opcom.getSampleId().equals(entry.getSample().getId())) {
            return null;
        }
        if (opcom.getSlotId()!=null && entry.getSlot()!=null && !opcom.getSlotId().equals(entry.getSlot().getId())) {
            return null;
        }
        if (opcom.getSlotId()!=null && opcom.getSampleId()!=null && (entry.getSlot()==null || entry.getSample()==null)) {
            Slot slot = entry.getSlot();
            if (slot==null) {
                slot = entry.getLabware().getSlots().stream()
                        .filter(s -> s.getId().equals(opcom.getSlotId()))
                        .findAny()
                        .orElse(null);
            }
            if (slot!=null) {
                return String.format("(%s %s): %s", slot.getAddress(), opcom.getSampleId(), opcom.getComment().getText());
            }
        }
        return opcom.getComment().getText();
    }

    /**
     * Join the distinct opcoms texts as sentences, adding a full stop where missing.
     * @param opcoms the operation comments to join
     * @return a string combining the texts of the given comments
     */
    private static String joinComments(Stream<OperationComment> opcoms) {
        return opcoms.map(OperationComment::getComment)
                .distinct()
                .map(Comment::getText)
                .map(s -> s.endsWith(".") ? s : (s + "."))
                .collect(joining(" "));
    }

    /**
     * Join the distinct strings  as sentences, adding a full stop where missing.
     * @param strings the strings to join
     * @return a string combining the given strings
     */
    private static String joinCommentDescs(Stream<String> strings) {
        return strings.distinct().map(s -> s.endsWith(".") ? s : (s + "."))
                .collect(joining(" "));
    }

    /**
     * Loads info about stains on the labware or its antecedents.
     * @param entries the release entries
     * @param ancestry the ancestry map
     */
    public void loadStains(Collection<ReleaseEntry> entries, Ancestry ancestry) {
        Set<Integer> slotIds = ancestry.keySet().stream().map(SlotSample::slotId).collect(toSet());
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
                    log.error("RNAscope plex should be an integer: {}", note);
                }
            } else if (name.equalsIgnoreCase(ComplexStainServiceImp.LW_NOTE_PLEX_IHC)) {
                try {
                    opIhcPlex.put(opId, Integer.valueOf(value));
                } catch (NumberFormatException e) {
                    log.error("IHC plex should be an integer: {}", note);
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
        Set<Integer> stainOpIds = entryStainOp.values().stream().map(Operation::getId).collect(toSet());
        loadStainQcComments(entries, ancestry, stainOpIds);
    }

    /**
     * Loads comments from the result-ops for the given stain op ids. Puts them into the stainQcComment field in the
     * ReleaseEntries. Follows the ancestry to see which operations are relevant to which entries.
     * @param entries the release entries under construction
     * @param ancestry the ancestry of the entities referred to in the entries
     * @param stainOpIds the stain operation ids that we look up the results for
     */
    public void loadStainQcComments(Collection<ReleaseEntry> entries, Ancestry ancestry, Collection<Integer> stainOpIds) {
        List<ResultOp> rops = roRepo.findAllByRefersToOpIdIn(stainOpIds);
        if (rops.isEmpty()) {
            return;
        }
        Set<Integer> resultOpIds = rops.stream().map(ResultOp::getOperationId).collect(toSet());
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(resultOpIds);
        Map<SlotIdSampleId, List<OperationComment>> opComMap = opcoms.stream()
                .collect(groupingBy(oc -> new SlotIdSampleId(oc.getSlotId(), oc.getSampleId())));
        if (opComMap.isEmpty()) {
            return;
        }
        Map<SlotIdSampleId, String> joinedComments = new HashMap<>(opComMap.size());
        for (var e : opComMap.entrySet()) {
            String commentString = joinComments(e.getValue().stream());
            joinedComments.put(e.getKey(), commentString);
        }

        for (ReleaseEntry entry : entries) {
            if (entry.getSample()==null || entry.getSlot()==null) {
                continue;
            }
            SlotSample key = new SlotSample(entry.getSlot(), entry.getSample());
            String commentText = null;
            for (SlotSample ancestor : ancestry.ancestors(key)) {
                commentText = joinedComments.get(new SlotIdSampleId(ancestor.slotId(), ancestor.sampleId()));
                if (commentText != null) {
                    break;
                }
            }
            entry.setStainQcComment(commentText);
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
        Set<Integer> slotIds = ancestry.keySet().stream().map(SlotSample::slotId).collect(toSet());
        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(slotIds);
        Map<Integer, List<Measurement>> slotIdToThickness = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToCoverage = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToCq = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToVisiumConc = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToPermTimes = new HashMap<>();
        Map<Integer, List<Measurement>> slotIdToCycles = new HashMap<>();
        final String THICKNESS = MeasurementType.Thickness.friendlyName();
        final String COVERAGE = MeasurementType.Tissue_coverage.friendlyName();
        final String CQ = MeasurementType.Cq_value.friendlyName();
        final String CDNA_CONC = MeasurementType.cDNA_concentration.friendlyName();
        final String LIBRARY_CONC = MeasurementType.Library_concentration.friendlyName();
        final String CYCLES = MeasurementType.Cycles.friendlyName();
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
            } else if (measurement.getName().equalsIgnoreCase(CYCLES)) {
                List<Measurement> slotIdMeasurements = slotIdToCycles.computeIfAbsent(measurement.getSlotId(), k -> new ArrayList<>());
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
                entry.setCq(cqMeasurement.getValue());
            }
            Measurement cyclesMeasurement = selectMeasurement(entry, slotIdToCycles, ancestry);
            if (cyclesMeasurement!=null) {
                entry.setAmplificationCycles(cyclesMeasurement.getValue());
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
                        log.error("96 well plate permeabilisation time measurement is not an integer: {}", permMeasurement);
                    }
                }
            }
        }
    }

    public static String toMinutes(String secondsValue) {
        int sec = Integer.parseInt(secondsValue);
        int min = sec / 60;
        sec %= 60;
        if (sec==0) {
            return min+" min";
        }
        if (min==0) {
            return sec+" sec";
        }
        return String.format("%d min, %d sec", min, sec);
    }

    public void loadReagentSources(Collection<ReleaseEntry> entries) {
        Set<SlotSample> slotSamples = entries.stream()
                .map(e -> new SlotSample(e.getSlot(), e.getSample()))
                .collect(toSet());

        var radMap = reagentActionDetailService.loadAncestralReagentTransfers(slotSamples);
        if (!radMap.isEmpty()) {
            for (var entry : entries) {
                var rads = radMap.get(entry.getSlot().getId());
                if (rads!=null && !rads.isEmpty()) {
                    String radString = rads.stream()
                            .map(rad -> rad.reagentPlateBarcode+" : "+rad.reagentSlotAddress)
                            .distinct()
                            .collect(joining(", "));
                    entry.setReagentSource(radString);
                    String radTypeString = rads.stream()
                            .map(rad -> rad.reagentPlateType)
                            .distinct()
                            .collect(joining(", "));
                    entry.setReagentPlateType(radTypeString);
                    var datas = rads.stream()
                            .map(ReagentActionDetailService.ReagentActionDetail::getTagData)
                            .filter(td -> !td.isEmpty());
                    entry.setTagData(assembleTagData(datas));
                }
            }
        }
    }

    public Map<String, String> assembleTagData(Stream<Map<String, String>> datas) {
        var iter = datas.iterator();
        if (!iter.hasNext()) {
            return Map.of();
        }
        var data = iter.next();
        if (!iter.hasNext()) {
            return data;
        }
        final Map<String, String> merged = new LinkedHashMap<>(data);
        while (iter.hasNext()) {
            data = iter.next();
            data.forEach((k,v) -> {
                String old = merged.get(k);
                if (old==null) {
                    merged.put(k, v);
                } else if (!old.contains(v)) {
                    merged.put(k, old+","+v);
                }
            });
        }
        return merged;
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

    /** Loads comments from sectioning operations */
    public void loadSectionComments(Collection<ReleaseEntry> entries) {
        Set<Integer> slotIds = entries.stream().map(e -> e.getSlot().getId()).collect(toSet());
        OperationType sectionOpType = opTypeRepo.getByName("Section");
        Map<SlotIdSampleId, List<OperationComment>> commentMap = opComRepo.findAllBySlotAndOpType(slotIds, sectionOpType).stream()
                .collect(groupingBy(oc -> new SlotIdSampleId(oc.getSlotId(), oc.getSampleId())));
        if (commentMap.isEmpty()) {
            return;
        }
        for (ReleaseEntry entry : entries) {
            var ocs = commentMap.get(new SlotIdSampleId(entry.getSlot().getId(), entry.getSample().getId()));
            if (ocs==null || ocs.isEmpty()) {
                continue;
            }
            String sectionComment = ocs.stream()
                    .map(oc -> oc.getComment().getText())
                    .distinct()
                    .collect(joining("; "));
            entry.setSectionComment(sectionComment);
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
            List<Measurement> measurements = slotIdToMeasurement.get(ss.slotId());
            if (measurements!=null && !measurements.isEmpty()) {
                final Integer sampleId = ss.sampleId();
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
     * Loads flags relevant to the specified labware.
     * @param entries the release entries
     */
    public void loadFlags(Collection<ReleaseEntry> entries) {
        UCMap<Labware> labware = new UCMap<>();
        for (ReleaseEntry entry : entries) {
            Labware lw = entry.getLabware();
            labware.put(lw.getBarcode(), lw);
        }
        List<FlagDetail> flagDetails = flagLookupService.lookUpDetails(labware.values());
        if (flagDetails.isEmpty()) {
            return;
        }
        Map<Integer, String> flagMap = flagDetails.stream()
                .collect(toMap(fd -> labware.get(fd.getBarcode()).getId(),
                        fd -> describeFlags(fd.getFlags())));
        for (ReleaseEntry entry : entries) {
            entry.setFlagDescription(flagMap.getOrDefault(entry.getLabware().getId(), ""));
        }
    }

    /**
     * Combines flag summaries into a single string.
     * @param summaries flag summaries
     * @return a string combining the flag summaries
     */
    public String describeFlags(Collection<FlagDetail.FlagSummary> summaries) {
        if (nullOrEmpty(summaries)) {
            return "";
        }
        return summaries.stream()
                .map(summary -> summary.getBarcode()+": "+summary.getDescription())
                .collect(joining(" "));
    }

    public List<? extends TsvColumn<ReleaseEntry>> computeColumns(ReleaseFileContent rfc) {
        List<ReleaseColumn> modeColumns = ReleaseColumn.forModesAndOptions(rfc.getModes(), rfc.getOptions());
        int dualColumnIndex = modeColumns.indexOf(ReleaseColumn.Dual_index_plate_name);
        if (dualColumnIndex < 0 || rfc.getEntries().stream().allMatch(e -> nullOrEmpty(e.getTagData()))) {
            return modeColumns;
        }

        LinkedHashSet<String> tagDataColumnNames = rfc.getEntries().stream()
                .map(ReleaseEntry::getTagData)
                .filter(e -> !nullOrEmpty(e))
                .flatMap(e -> e.keySet().stream())
                .collect(toLinkedHashSet());
        if (tagDataColumnNames.isEmpty()) {
            return modeColumns;
        }

        List<TsvColumn<ReleaseEntry>> combinedList = new ArrayList<>(modeColumns.size() + tagDataColumnNames.size());

        combinedList.addAll(modeColumns.subList(0, dualColumnIndex+1));
        tagDataColumnNames.stream().map(TagDataColumn::new).forEach(combinedList::add);
        combinedList.addAll(modeColumns.subList(dualColumnIndex+1, modeColumns.size()));
        return combinedList;
    }
}
