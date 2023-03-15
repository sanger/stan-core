package uk.ac.sanger.sccp.stan.service.history;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.History;
import uk.ac.sanger.sccp.stan.request.HistoryEntry;
import uk.ac.sanger.sccp.stan.service.history.ReagentActionDetailService.ReagentActionDetail;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * @author dr6
 */
@Service
public class HistoryServiceImp implements HistoryService {
    private final OperationRepo opRepo;
    private final LabwareRepo lwRepo;
    private final SampleRepo sampleRepo;
    private final TissueRepo tissueRepo;
    private final DonorRepo donorRepo;
    private final ReleaseRepo releaseRepo;
    private final DestructionRepo destructionRepo;
    private final OperationCommentRepo opCommentRepo;
    private final SnapshotRepo snapshotRepo;
    private final WorkRepo workRepo;
    private final MeasurementRepo measurementRepo;
    private final LabwareNoteRepo labwareNoteRepo;
    private final ResultOpRepo resultOpRepo;
    private final StainTypeRepo stainTypeRepo;
    private final ReagentActionDetailService reagentActionDetailService;

    @Autowired
    public HistoryServiceImp(OperationRepo opRepo, LabwareRepo lwRepo, SampleRepo sampleRepo, TissueRepo tissueRepo,
                             DonorRepo donorRepo, ReleaseRepo releaseRepo,
                             DestructionRepo destructionRepo, OperationCommentRepo opCommentRepo,
                             SnapshotRepo snapshotRepo, WorkRepo workRepo, MeasurementRepo measurementRepo,
                             LabwareNoteRepo labwareNoteRepo, ResultOpRepo resultOpRepo,
                             StainTypeRepo stainTypeRepo,
                             ReagentActionDetailService reagentActionDetailService) {
        this.opRepo = opRepo;
        this.lwRepo = lwRepo;
        this.sampleRepo = sampleRepo;
        this.tissueRepo = tissueRepo;
        this.donorRepo = donorRepo;
        this.releaseRepo = releaseRepo;
        this.destructionRepo = destructionRepo;
        this.opCommentRepo = opCommentRepo;
        this.snapshotRepo = snapshotRepo;
        this.workRepo = workRepo;
        this.measurementRepo = measurementRepo;
        this.labwareNoteRepo = labwareNoteRepo;
        this.resultOpRepo = resultOpRepo;
        this.stainTypeRepo = stainTypeRepo;
        this.reagentActionDetailService = reagentActionDetailService;
    }

    @Override
    public History getHistoryForSampleId(int sampleId) {
        Sample sample = sampleRepo.findById(sampleId).orElseThrow(() -> new EntityNotFoundException("Sample id "+ sampleId +" not found."));
        Tissue tissue = sample.getTissue();
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(List.of(tissue.getId()));
        return getHistoryForSamples(samples);
    }

    @Override
    public History getHistoryForExternalName(String externalName) {
        List<Tissue> tissues;
        if (externalName!=null && externalName.indexOf('*') >= 0) {
            tissues = tissueRepo.findAllByExternalNameLike(BasicUtils.wildcardToLikeSql(externalName));
        } else {
            tissues = tissueRepo.getAllByExternalName(externalName);
        }
        List<Integer> tissueIds = tissues.stream().map(Tissue::getId).collect(toList());
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(tissueIds);
        return getHistoryForSamples(samples);
    }

    @Override
    public History getHistoryForDonorName(String donorName) {
        Donor donor = donorRepo.getByDonorName(donorName);
        List<Tissue> tissues = tissueRepo.findByDonorId(donor.getId());
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(tissues.stream().map(Tissue::getId).collect(toList()));
        return getHistoryForSamples(samples);
    }

    @Override
    public History getHistoryForLabwareBarcode(String barcode) {
        Labware lw = lwRepo.getByBarcode(barcode);
        Set<Integer> tissueIds = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .map(sam -> sam.getTissue().getId())
                .collect(toSet());
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(tissueIds);
        return getHistoryForSamples(samples);
    }

    @Override
    public History getHistoryForWorkNumber(String workNumber) {
        Work work = workRepo.getByWorkNumber(workNumber);
        List<Integer> opIds = work.getOperationIds();
        List<Integer> releaseIds = work.getReleaseIds();
        if (opIds.isEmpty() && releaseIds.isEmpty()) {
            return new History(List.of(), List.of(), List.of());
        }
        Collection<Operation> ops = opIds.isEmpty() ? List.of() : BasicUtils.asCollection(opRepo.findAllById(opIds));
        List<Release> releases = releaseIds.isEmpty() ? List.of() : releaseRepo.findAllByIdIn(releaseIds);
        Set<Integer> labwareIds = labwareIdsFromOps(ops);
        List<Labware> opLabware = lwRepo.findAllByIdIn(labwareIds);
        List<HistoryEntry> opEntries = createEntriesForOps(ops, null, opLabware, null, work.getWorkNumber());
        final List<HistoryEntry> releaseEntries = createEntriesForReleases(releases, null, null, work.getWorkNumber());
        final List<HistoryEntry> entries = BasicUtils.concat(opEntries, releaseEntries);
        List<Labware> allLabware = new ArrayList<>(opLabware);
        if (!releases.isEmpty()) {
            labwareIds = new HashSet<>(labwareIds);
            for (Release rel : releases) {
                Labware lw = rel.getLabware();
                if (labwareIds.add(lw.getId())) {
                    allLabware.add(lw);
                }
            }
        }
        List<Sample> samples = referencedSamples(entries, allLabware);
        entries.sort(Comparator.comparing(HistoryEntry::getTime));
        return new History(entries, samples, allLabware);
    }

    /**
     * Gets all labware ids referenced in the given operations
     * @param ops operations
     * @return a set of all labware ids from the ops
     */
    public Set<Integer> labwareIdsFromOps(Collection<Operation> ops) {
        return ops.stream()
                .flatMap(op -> op.getActions().stream())
                .flatMap(ac -> Stream.of(ac.getSource().getLabwareId(), ac.getDestination().getLabwareId()))
                .collect(toSet());
    }

    /**
     * Gets all samples referenced in the given history entries.
     * Any that are present in the given labware will be taken from there instead of
     * looked up again the database
     * @param entries history entries
     * @param labware labware
     * @return a list of all distinct samples references in the history entries
     */
    public List<Sample> referencedSamples(Collection<HistoryEntry> entries, Collection<Labware> labware) {
        Map<Integer, Sample> sampleCache = new HashMap<>();
        for (Labware lw : labware) {
            for (Slot slot : lw.getSlots()) {
                for (Sample sample : slot.getSamples()) {
                    sampleCache.put(sample.getId(), sample);
                }
            }
        }
        Set<Integer> entrySampleIds = entries.stream()
                .map(HistoryEntry::getSampleId)
                .filter(Objects::nonNull)
                .collect(toSet());
        List<Sample> samples = new ArrayList<>(entrySampleIds.size());
        Set<Integer> toLookUp = new HashSet<>();
        for (Integer sampleId : entrySampleIds) {
            Sample sample = sampleCache.get(sampleId);
            if (sample!=null) {
                samples.add(sample);
            } else {
                toLookUp.add(sampleId);
            }
        }
        if (!toLookUp.isEmpty()) {
            samples.addAll(sampleRepo.findAllByIdIn(toLookUp));
        }
        return samples;
    }

    /**
     * Gets the history for the specifically supplied samples (which are commonly all related).
     * @param samples the samples to get the history for
     * @return the history involving those samples
     */
    public History getHistoryForSamples(List<Sample> samples) {
        Set<Integer> sampleIds = samples.stream().map(Sample::getId).collect(toSet());
        List<Operation> ops = opRepo.findAllBySampleIdIn(sampleIds);
        Set<Integer> labwareIds = loadLabwareIdsForOpsAndSampleIds(ops, sampleIds);
        List<Destruction> destructions = destructionRepo.findAllByLabwareIdIn(labwareIds);
        List<Release> releases = releaseRepo.findAllByLabwareIdIn(labwareIds);
        List<Labware> labware = lwRepo.findAllByIdIn(labwareIds);

        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());
        Map<Integer, Set<String>> opWork = workRepo.findWorkNumbersForOpIds(opIds);
        List<Integer> releaseIds = releases.stream().map(Release::getId).collect(toList());
        Map<Integer, String> releaseWork = workRepo.findWorkNumbersForReleaseIds(releaseIds);

        List<HistoryEntry> opEntries = createEntriesForOps(ops, sampleIds, labware, opWork, null);
        List<HistoryEntry> releaseEntries = createEntriesForReleases(releases, sampleIds, releaseWork, null);
        List<HistoryEntry> destructionEntries = createEntriesForDestructions(destructions, sampleIds);

        List<HistoryEntry> entries = assembleEntries(List.of(opEntries, releaseEntries, destructionEntries));
        return new History(entries, samples, labware);
    }


    /**
     * Gets labware ids from the given operations that are linked to the given sample ids.
     * @param ops the operations
     * @param sampleIds the ids of relevant samples
     * @return the labware ids referenced in the operations related to the sample ids
     */
    public Set<Integer> loadLabwareIdsForOpsAndSampleIds(Collection<Operation> ops, Set<Integer> sampleIds) {
        Set<Integer> labwareIds = new HashSet<>();
        for (Operation op : ops) {
            for (Action action : op.getActions()) {
                if (action.getSample()!=null && sampleIds.contains(action.getSample().getId())
                        || action.getSourceSample()!=null && sampleIds.contains(action.getSourceSample().getId())) {
                    labwareIds.add(action.getDestination().getLabwareId());
                    labwareIds.add(action.getSource().getLabwareId());
                }
            }
        }
        return labwareIds;
    }

    /**
     * Gets all comments on specified operations
     * @param opIds ids of operations
     * @return a map of operation id to a list of operation comments
     */
    public Map<Integer, List<OperationComment>> loadOpComments(Collection<Integer> opIds) {
        List<OperationComment> opComments = opCommentRepo.findAllByOperationIdIn(opIds);
        if (opComments.isEmpty()) {
            return Map.of();
        }
        return opComments.stream().collect(Collectors.groupingBy(OperationComment::getOperationId));
    }

    /**
     * Should the given operation-comment be added as a detail to the history entry under construction?
     * It should, unless it has a field that indicates it is not relevant.
     * @param com the operation comment to be checked
     * @param sampleId the id of the sample for the history entry
     * @param labwareId the id of the destination labware for the history entry
     * @param slotIdMap a map to look up slots from their id
     * @return true if the comment is applicable; false if it is not
     */
    public boolean doesCommentApply(OperationComment com, int sampleId, int labwareId, Map<Integer, Slot> slotIdMap) {
        if (com.getSampleId()!=null && com.getSampleId()!=sampleId) {
            return false;
        }
        if (com.getLabwareId()!=null) {
            return (com.getLabwareId()==labwareId);
        }
        if (com.getSlotId()!=null) {
            Slot slot = slotIdMap.get(com.getSlotId());
            return (slot!=null && slot.getLabwareId() == labwareId);
        }
        return true;
    }

    /**
     * Should the given measurement be added as a detail to the history entry under construction?
     * It should, unless it has a field that indicates it is not relevant.
     * @param measurement the measurement to be checked
     * @param sampleId the id of the sample for the history entry
     * @param labwareId the id of the destination labware for the history entry
     * @param slotIdMap a map to look up slots from their id
     * @return true if the measurement is applicable; false if it is not
     */
    public boolean doesMeasurementApply(Measurement measurement, int sampleId, int labwareId, Map<Integer, Slot> slotIdMap) {
        if (measurement.getSampleId()!=null && measurement.getSampleId()!=sampleId) {
            return false;
        }
        if (measurement.getSlotId()!=null) {
            Slot slot = slotIdMap.get(measurement.getSlotId());
            return (slot!=null && slot.getLabwareId()==labwareId);
        }
        return true;
    }

    /**
     * Should the given result be added as a detail to the history entry under construction?
     * It should unless its sample or slot labware indicate that it is not relevant.
     * @param result the result to be checked
     * @param sampleId the id of the sample for the history entry
     * @param labwareId the id of the destination labware for the history entry
     * @param slotIdMap a map to look up slots from their id
     * @return true if the result is applicable; false if it is not
     */
    public boolean doesResultApply(ResultOp result, int sampleId, int labwareId, Map<Integer, Slot> slotIdMap) {
        if (result.getSampleId()!=null && result.getSampleId()!=sampleId) {
            return false;
        }
        if (result.getSlotId()!=null) {
            Slot slot = slotIdMap.get(result.getSlotId());
            return (slot != null && slot.getLabwareId()==labwareId);
        }
        return true;
    }

    /**
     * Loads measurements for the given operations
     * @param opIds the id of operations
     * @return a map of operation id to list of measurements
     */
    public Map<Integer, List<Measurement>> loadOpMeasurements(Collection<Integer> opIds) {
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(opIds);
        if (measurements.isEmpty()) {
            return Map.of();
        }
        return measurements.stream().collect(Collectors.groupingBy(Measurement::getOperationId));
    }

    /**
     * Loads labware notes for the specified operations
     * @param opIds the ids of operations
     * @return a map of op id to list of labware notes
     */
    public Map<Integer, List<LabwareNote>> loadOpLabwareNotes(Collection<Integer> opIds) {
        List<LabwareNote> notes = labwareNoteRepo.findAllByOperationIdIn(opIds);
        if (notes.isEmpty()) {
            return Map.of();
        }
        return notes.stream().collect(Collectors.groupingBy(LabwareNote::getOperationId));
    }

    public String describeSeconds(String value) {
        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            return value;
        }
        int minutes = seconds/60;
        if (minutes==0) {
            return seconds+"\u00a0sec";
        }
        seconds %= 60;
        int hours = minutes/60;
        if (hours==0) {
            if (seconds==0) {
                return minutes + "\u00a0min";
            }
            return minutes + "\u00a0min " + seconds + "\u00a0sec";
        }
        minutes %= 60;
        if (minutes==0 && seconds==0) {
            return hours + "\u00a0hour";
        }
        if (seconds==0) {
            return hours + "\u00a0hour " + minutes + "\u00a0min";
        }
        return String.format("%d\u00a0hour %d\u00a0min %d\u00a0sec", hours, minutes, seconds);
    }

    /**
     * Converts a measurement into a string to go into the details of a history entry
     * @param measurement a measurement
     * @param slotIdMap a map to look up slot ids
     * @return a string describing the measurement
     */
    public String measurementDetail(Measurement measurement, Map<Integer, Slot> slotIdMap) {
        MeasurementType mt = MeasurementType.forName(measurement.getName());
        if (mt==null && BasicUtils.startsWithIgnoreCase(measurement.getName(), "DV200")) {
            mt = MeasurementType.DV200;
        }
        MeasurementValueType vt = (mt==null ? null : mt.getValueType());
        String detail = measurement.getName() + ": ";
        if (vt==MeasurementValueType.TIME) {
            detail += describeSeconds(measurement.getValue());
        } else {
            detail += measurement.getValue();
            if (mt!=null && mt.getUnit()!=null) {
                detail += "\u00a0" + mt.getUnit();
            }
        }
        if (measurement.getSlotId()!=null) {
            assert slotIdMap != null;
            detail = slotIdMap.get(measurement.getSlotId()).getAddress()+": "+detail;
        }
        return detail;
    }

    /**
     * Converts a labware note into a string to go into the details of a history entry.
     * Since entries are specific to an operation and labware, the only information that needs
     * to be included is the note's key and value.
     * @param lwNote the labware note to detail
     * @return a string describing the note
     */
    public String labwareNoteDetail(LabwareNote lwNote) {
        return lwNote.getName()+": "+lwNote.getValue();
    }

    /**
     * Loads the results recorded against any result operations in the given collection of operations
     * @param ops some operations, some of which might be result ops
     * @return a map of op id to list of applicable results
     */
    public Map<Integer, List<ResultOp>> loadOpResults(Collection<Operation> ops) {
        List<Integer> resultOpIds = ops.stream()
                .filter(op -> op.getOperationType().has(OperationTypeFlag.RESULT))
                .map(Operation::getId)
                .collect(toList());
        if (resultOpIds.isEmpty()) {
            return Map.of();
        }
        Iterable<ResultOp> results = resultOpRepo.findAllByOperationIdIn(resultOpIds);
        return BasicUtils.stream(results).collect(Collectors.groupingBy(ResultOp::getOperationId));
    }

    /**
     * Describes a result for inclusion in the details of a history entry
     * @param result the result to describe
     * @param slotIdMap a map to look up slots by their id
     * @return a string describing the result
     */
    public String resultDetail(ResultOp result, Map<Integer, Slot> slotIdMap) {
        String detail = result.getResult().name();
        if (result.getSlotId()!=null) {
            Slot slot = slotIdMap.get(result.getSlotId());
            if (slot!=null) {
                detail = slot.getAddress()+": "+detail;
            }
        }
        return detail;
    }

    /**
     * Creates history entries for the given operations, where relevant to the specified samples
     * @param operations the operations to represent in the history
     * @param sampleIds the ids of relevant samples, or null to not filter by sample
     * @param labware the relevant labware
     * @param opWork a map of op id to work numbers
     * @param singleWorkNumber a single work number applicable to all operations
     * @return a list of history entries for the given operations
     */
    public List<HistoryEntry> createEntriesForOps(Collection<Operation> operations, Set<Integer> sampleIds,
                                                  Collection<Labware> labware, Map<Integer, Set<String>> opWork,
                                                  String singleWorkNumber) {
        Set<Integer> opIds = operations.stream().map(Operation::getId).collect(toSet());
        var opComments = loadOpComments(opIds);
        var opMeasurements = loadOpMeasurements(opIds);
        var opLabwareNotes = loadOpLabwareNotes(opIds);
        var opStainTypes = stainTypeRepo.loadOperationStainTypes(opIds);
        var opReagentActions = reagentActionDetailService.loadReagentTransfers(opIds);
        var opResults = loadOpResults(operations);
        final Map<Integer, Slot> slotIdMap;
        if (!opComments.isEmpty() || !opMeasurements.isEmpty()) {
            slotIdMap = labware.stream()
                    .flatMap(lw -> lw.getSlots().stream())
                    .collect(toMap(Slot::getId, Function.identity()));
        } else {
            slotIdMap = null; // not needed
        }
        List<HistoryEntry> entries = new ArrayList<>();
        for (Operation op : operations) {
            String stainDetail;
            if (op.getOperationType().has(OperationTypeFlag.STAIN)) {
                List<StainType> sts = opStainTypes.get(op.getId());
                if (sts!=null && !sts.isEmpty()) {
                    stainDetail = "Stain type: "+sts.stream()
                            .map(StainType::getName)
                            .collect(Collectors.joining(", "));
                } else {
                    stainDetail = null;
                }
            } else {
                stainDetail = null;
            }
            List<ResultOp> results = opResults.get(op.getId());
            Equipment equipment = op.getEquipment();
            Set<SampleAndLabwareIds> items = new LinkedHashSet<>();
            List<OperationComment> comments = opComments.getOrDefault(op.getId(), List.of());
            List<Measurement> measurements = opMeasurements.getOrDefault(op.getId(), List.of());
            List<LabwareNote> lwNotes = opLabwareNotes.getOrDefault(op.getId(), List.of());
            List<ReagentActionDetail> reagentActions = opReagentActions.getOrDefault(op.getId(), List.of());
            String workNumber;
            if (opWork!=null) {
                Set<String> workNumbers = opWork.get(op.getId());
                if (workNumbers != null && !workNumbers.isEmpty()) {
                    workNumber = String.join(", ", workNumbers);
                } else {
                    workNumber = null;
                }
            } else {
                workNumber = singleWorkNumber;
            }

            for (Action action : op.getActions()) {
                final Integer sampleId = action.getSample().getId();
                if (sampleIds==null || sampleIds.contains(sampleId)) {
                    final Integer sourceId = action.getSource().getLabwareId();
                    final Integer destId = action.getDestination().getLabwareId();
                    items.add(new SampleAndLabwareIds(sampleId, sourceId, destId));
                }
            }
            String username = op.getUser().getUsername();
            for (var item : items) {
                HistoryEntry entry = new HistoryEntry(op.getId(), op.getOperationType().getName(),
                        op.getPerformed(), item.sourceId, item.destId, item.sampleId, username, workNumber);
                if (stainDetail!=null) {
                    entry.addDetail(stainDetail);
                }
                for (ReagentActionDetail rad : reagentActions) {
                    if (rad.destinationLabwareId==item.destId) {
                        entry.addDetail(rad.reagentPlateBarcode+" : "+rad.reagentSlotAddress + " -> "+rad.destSlotAddress);
                    }
                }
                for (LabwareNote lwNote : lwNotes) {
                    if (lwNote.getLabwareId() == item.destId) {
                        String detail = labwareNoteDetail(lwNote);
                        entry.addDetail(detail);
                    }
                }
                if (equipment!=null) {
                    entry.addDetail("Equipment: "+equipment.getName());
                }
                if (results!=null) {
                    results.forEach(result -> {
                        if (doesResultApply(result, item.sampleId, item.destId, slotIdMap)) {
                            entry.addDetail(resultDetail(result, slotIdMap));
                        }
                    });
                }
                comments.forEach(com -> {
                    if (doesCommentApply(com, item.sampleId, item.destId, slotIdMap)) {
                        String detail = com.getComment().getText();
                        if (com.getSlotId()!=null) {
                            assert slotIdMap != null;
                            detail = slotIdMap.get(com.getSlotId()).getAddress()+": "+detail;
                        }
                        entry.addDetail(detail);
                    }
                });
                measurements.forEach(measurement -> {
                    if (doesMeasurementApply(measurement, item.sampleId, item.destId, slotIdMap)) {
                        String detail = measurementDetail(measurement, slotIdMap);
                        entry.addDetail(detail);
                    }
                });

                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * Creates history entries representing releases, where they are applicable to the specified sample
     * @param releases the releases to represent
     * @param sampleIds the ids of relevant samples
     * @param releaseWorkNumbers optional map of release id to work number
     * @param singleWorkNumber optional single work number for all releases
     * @return a list of history entries for the given releases
     */
    public List<HistoryEntry> createEntriesForReleases(List<Release> releases, Set<Integer> sampleIds,
                                                       Map<Integer, String> releaseWorkNumbers, String singleWorkNumber) {
        Set<Integer> snapshotIds = releases.stream().map(Release::getSnapshotId).filter(Objects::nonNull).collect(toSet());
        Map<Integer, Snapshot> snapshotMap = snapshotRepo.findAllByIdIn(snapshotIds).stream()
                .collect(BasicUtils.toMap(Snapshot::getId, HashMap::new));
        List<HistoryEntry> entries = new ArrayList<>();
        for (Release release : releases) {
            String workNum;
            if (releaseWorkNumbers!=null) {
                workNum = releaseWorkNumbers.get(release.getId());
            } else {
                workNum = singleWorkNumber;
            }

            int labwareId = release.getLabware().getId();
            List<String> details = List.of("Destination: "+release.getDestination().getName(),
                    "Recipient: "+release.getRecipient().getUsername());
            String username = release.getUser().getUsername();
            if (release.getSnapshotId()==null) {
                entries.add(new HistoryEntry(release.getId(), "Release", release.getReleased(),
                        labwareId, labwareId,null, username, workNum, details));
            } else {
                Snapshot snap = snapshotMap.get(release.getSnapshotId());

                Stream<Integer> sampleIdStream = snap.getElements().stream()
                        .map(SnapshotElement::getSampleId);
                if (sampleIds!=null) {
                    sampleIdStream = sampleIdStream.filter(sampleIds::contains);
                }
                Set<Integer> releaseSampleIds = sampleIdStream
                        .collect(BasicUtils.toLinkedHashSet());
                for (Integer sampleId : releaseSampleIds) {
                    entries.add(new HistoryEntry(release.getId(), "Release", release.getReleased(),
                            labwareId, labwareId, sampleId, username, workNum, details));
                }
            }
        }
        return entries;
    }

    /**
     * Creates history entries representing destructions, where they are applicable to the specified sample
     * @param destructions the destructions to represent
     * @param sampleIds the ids of relevant samples
     * @return a list of history entries for the given destructions
     */
    public List<HistoryEntry> createEntriesForDestructions(Collection<Destruction> destructions, Set<Integer> sampleIds) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (Destruction destruction : destructions) {
            final Labware labware = destruction.getLabware();
            Set<Integer> destructionSampleIds = labware.getSlots().stream()
                    .flatMap(slot -> slot.getSamples().stream().map(Sample::getId))
                    .filter(sampleIds::contains)
                    .collect(BasicUtils.toLinkedHashSet());
            if (!destructionSampleIds.isEmpty()) {
                String username = destruction.getUser().getUsername();
                int labwareId = labware.getId();
                List<String> details = List.of("Reason: "+destruction.getReason().getText());
                for (Integer sampleId : destructionSampleIds) {
                    entries.add(new HistoryEntry(destruction.getId(), "Destruction", destruction.getDestroyed(),
                            labwareId, labwareId, sampleId, username, null, details));
                }
            }
        }
        return entries;
    }

    /**
     * Assembles various collections of history entries into a single list, sorted by time
     * @param entryCollections the collections of entries to include in the combined list
     * @return a list containing the entries from all the given collections, sorted
     */
    public List<HistoryEntry> assembleEntries(List<Collection<HistoryEntry>> entryCollections) {
        int num = entryCollections.stream().mapToInt(Collection::size).sum();
        List<HistoryEntry> entries = new ArrayList<>(num);
        for (var entryList : entryCollections) {
            entries.addAll(entryList);
        }
        entries.sort(Comparator.comparing(HistoryEntry::getTime));
        return entries;
    }

    // region support class
    private static class SampleAndLabwareIds {
        final int sampleId, sourceId, destId;

        public SampleAndLabwareIds(int sampleId, int sourceId, int destId) {
            this.sampleId = sampleId;
            this.sourceId = sourceId;
            this.destId = destId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SampleAndLabwareIds that = (SampleAndLabwareIds) o;
            return (this.sampleId == that.sampleId
                    && this.sourceId == that.sourceId
                    && this.destId == that.destId);
        }

        @Override
        public int hashCode() {
            return sampleId + 31 * (sourceId + 31 * destId);
        }
    }
    // endregion
}
