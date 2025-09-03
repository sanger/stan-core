package uk.ac.sanger.sccp.stan.service.cytassistoverview;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Posterity;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class CytassistOverviewDataCompilerImp implements CytassistOverviewDataCompiler {
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;
    private final LabwareRepo lwRepo;
    private final StainTypeRepo stainTypeRepo;
    private final LabwareProbeRepo lwProbeRepo;
    private final LabwareNoteRepo lwNoteRepo;
    private final MeasurementRepo measurementRepo;
    private final ReagentActionRepo reagentActionRepo;
    private final ReagentPlateRepo reagentPlateRepo;
    private final OperationCommentRepo opComRepo;
    private final ReleaseRepo releaseRepo;
    private final LabwareFlagRepo lwFlagRepo;
    private final WorkRepo workRepo;

    private final Ancestoriser ancestoriser;

    @Autowired
    public CytassistOverviewDataCompilerImp(OperationTypeRepo opTypeRepo, OperationRepo opRepo, LabwareRepo lwRepo,
                                            StainTypeRepo stainTypeRepo, LabwareProbeRepo lwProbeRepo,
                                            LabwareNoteRepo lwNoteRepo, MeasurementRepo measurementRepo,
                                            ReagentActionRepo reagentActionRepo, ReagentPlateRepo reagentPlateRepo,
                                            OperationCommentRepo opComRepo, ReleaseRepo releaseRepo,
                                            LabwareFlagRepo lwFlagRepo, WorkRepo workRepo,
                                            Ancestoriser ancestoriser) {
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.lwRepo = lwRepo;
        this.stainTypeRepo = stainTypeRepo;
        this.lwProbeRepo = lwProbeRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.measurementRepo = measurementRepo;
        this.reagentActionRepo = reagentActionRepo;
        this.reagentPlateRepo = reagentPlateRepo;
        this.opComRepo = opComRepo;
        this.releaseRepo = releaseRepo;
        this.lwFlagRepo = lwFlagRepo;
        this.workRepo = workRepo;
        this.ancestoriser = ancestoriser;
    }

    @Override
    public List<CytassistOverview> execute() {
        OperationType cyt = opTypeRepo.getByName("cytassist");
        List<Operation> cytOps = opRepo.findAllByOperationType(cyt);
        List<CytData> data = cytOps.stream()
                .flatMap(op -> op.getActions().stream()
                        .map(ac -> new CytData(ac, op)))
                .toList();
        Set<Integer> sourceSlotIds = sourceSlotIds(data);
        Posterity posterity = loadPosterity(data);
        Set<Integer> allDestSlotIds = destSlotIds(posterity);
        loadCytLabware(data);
        loadSourceCreation(data, sourceSlotIds);
        fillCytassistData(data);
        loadLp(data);
        loadStains(data, sourceSlotIds);
        loadImages(data, sourceSlotIds);
        loadProbes(data, sourceSlotIds);
        loadProbeQC(data, sourceSlotIds);
        loadTissueCoverage(data, sourceSlotIds);
        loadQPCR(data, posterity, allDestSlotIds);
        loadAmpCq(data, posterity, allDestSlotIds);
        loadDualIndex(data, posterity, allDestSlotIds);
        loadVisiumConcentration(data, posterity, allDestSlotIds);
        loadLatestLabware(data, posterity);
        loadFlags(data, posterity);
        loadWorkNumbers(data);
        setUsers(data);
        return data.stream().map(d -> d.row).toList();
    }

    /** Loads info for all labware (actually slots and samples) descended from the data */
    Posterity loadPosterity(List<CytData> data) {
        Set<SlotSample> ss = data.stream()
                .map(d -> new SlotSample(d.cytAction.getSource(), d.cytAction.getSourceSample()))
                .collect(toSet());
        return ancestoriser.findPosterity(ss);
    }

    /** Loads the labware involved in the cyt ops */
    Map<Integer, Labware> loadCytLabware(List<CytData> data) {
        Set<Integer> lwIds = new HashSet<>();
        for (CytData d : data) {
            lwIds.add(d.cytAction.getSource().getLabwareId());
            lwIds.add(d.cytAction.getDestination().getLabwareId());
        }
        Map<Integer, Labware> lwIdMap = lwRepo.findAllByIdIn(lwIds).stream().collect(inMap(Labware::getId));
        for (CytData d : data) {
            d.sourceLabware = lwIdMap.get(d.cytAction.getSource().getLabwareId());
            d.destLabware = lwIdMap.get(d.cytAction.getDestination().getLabwareId());
        }
        return lwIdMap;
    }

    /** Loads LP numbers (lw notes) for the cyt ops */
    void loadLp(List<CytData> data) {
        Set<Integer> opIds = data.stream().map(d -> d.cytOp.getId()).collect(toSet());
        List<LabwareNote> lwNotes = lwNoteRepo.findAllByOperationIdIn(opIds);
        Map<List<Integer>, String> lwOpLp = new HashMap<>();
        for (LabwareNote lwNote : lwNotes) {
            if (lwNote.getName().equalsIgnoreCase("lp number")) {
                List<Integer> lwOpId = List.of(lwNote.getLabwareId(), lwNote.getOperationId());
                lwOpLp.put(lwOpId, lwNote.getValue());
            }
        }
        for (CytData d : data) {
            List<Integer> lwOpId = List.of(d.destLabware.getId(), d.cytOp.getId());
            d.row.setCytassistLp(lwOpLp.get(lwOpId));
        }
    }

    /** Loads work numbers for the cyt ops */
    void loadWorkNumbers(List<CytData> data) {
        Set<Integer> opIds = data.stream().map(d -> d.cytOp.getId()).collect(toSet());
        Map<Integer, Set<String>> opWorkNumbers = workRepo.findWorkNumbersForOpIds(opIds);
        for (CytData d : data) {
            Set<String> workNumbers = opWorkNumbers.get(d.cytOp.getId());
            if (!nullOrEmpty(workNumbers)) {
                d.row.setWorkNumber(String.join(", ", workNumbers));
            }
        }
    }

    /** Finds the date of the register or sample op that created the sources of the cyt ops */
    void loadSourceCreation(List<CytData> data, Set<Integer> sourceSlotIds) {
        OperationType reg = opTypeRepo.getByName("Register");
        OperationType sec = opTypeRepo.getByName("Section");
        List<Operation> regOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(reg, sourceSlotIds);
        List<Operation> secOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(sec, sourceSlotIds);
        Map<Integer, Operation> creationOps = new HashMap<>(sourceSlotIds.size());
        for (Operation op : chain(regOps, secOps)) {
            for (Action action : op.getActions()) {
                creationOps.put(action.getDestination().getLabwareId(), op);
            }
        }
        for (CytData d : data) {
            Operation op = creationOps.get(d.cytAction.getSource().getLabwareId());
            if (op != null) {
                d.row.setSourceLabwareCreated(op.getPerformed());
                d.users.add(op.getUser());
            }
        }
    }

    /** Fills in a bunch of fields related to the cyt ops */
    void fillCytassistData(List<CytData> data) {
        for (CytData d : data) {
            d.row.setSection(d.cytAction.getSourceSample().getSection());
            d.row.setSourceBarcode(d.sourceLabware.getBarcode());
            d.row.setSourceSlotAddress(d.cytAction.getSource().getAddress().toString());
            d.row.setSampleId(d.cytAction.getSourceSample().getId());
            d.row.setSourceExternalName(d.cytAction.getSourceSample().getTissue().getExternalName());
            d.row.setSourceLabwareType(d.sourceLabware.getLabwareType().getName());
            d.row.setCytassistPerformed(d.cytOp.getPerformed());
            d.row.setCytassistLabwareType(d.destLabware.getLabwareType().getName());
            d.row.setCytassistBarcode(d.destLabware.getBarcode());
            d.row.setCytassistSlotAddress(d.cytAction.getDestination().getAddress().toString());
        }
    }

    /** Loads info for stain ops on the cyt sources */
    void loadStains(List<CytData> data, Set<Integer> sourceSlotIds) {
        OperationType stainOpType = opTypeRepo.getByName("Stain");
        List<Operation> stainOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(stainOpType, sourceSlotIds);
        Map<Integer, Operation> slotOps = new HashMap<>();
        for (Operation op : stainOps) {
            for (Action action : op.getActions()) {
                Integer slotId = action.getDestination().getId();
                Operation savedOp = slotOps.get(slotId);
                if (savedOp == null || savedOp.compareTo(op) < 0) {
                    slotOps.put(slotId, op);
                }
            }
        }
        Set<Integer> stainOpIds = slotOps.values().stream().map(Operation::getId).collect(toSet());
        Map<Integer, List<StainType>> opStains = stainTypeRepo.loadOperationStainTypes(stainOpIds);
        for (CytData d : data) {
            Operation op = slotOps.get(d.cytAction.getSource().getId());
            if (op == null) {
                continue;
            }
            d.users.add(op.getUser());
            d.row.setStainPerformed(op.getPerformed());
            String stainDesc = opStains.get(op.getId()).stream()
                    .map(StainType::getName)
                    .collect(joining(", "));
            d.row.setStainType(stainDesc);
        }
    }

    /** Loads info for Image ops on the cyt sources */
    void loadImages(List<CytData> data, Set<Integer> sourceSlotIds) {
        OperationType imageOpType = opTypeRepo.getByName("Image");
        List<Operation> imageOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(imageOpType, sourceSlotIds);
        Map<Integer, Operation> opMap = latestOps(imageOps);
        for (CytData d : data) {
            Operation op = opMap.get(d.cytAction.getSource().getId());
            if (op != null) {
                d.row.setImagePerformed(op.getPerformed());
                d.users.add(op.getUser());
            }
        }
    }

    /** Loads info for probe hybridisation ops on the cyt sources */
    void loadProbes(List<CytData> data, Set<Integer> sourceSlotIds) {
        OperationType probeOpType = opTypeRepo.getByName("Probe hybridisation Cytassist");
        List<Operation> probeOps = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(probeOpType, sourceSlotIds);
        Map<Integer, Set<Operation>> slotProbeOps = new HashMap<>();
        for (Operation op : probeOps) {
            for (Action a : op.getActions()) {
                Integer slotId = a.getSource().getId();
                slotProbeOps.computeIfAbsent(slotId, k -> new HashSet<>()).add(op);
            }
        }
        List<Integer> opIds = probeOps.stream().map(Operation::getId).toList();
        List<LabwareProbe> probes = lwProbeRepo.findAllByOperationIdIn(opIds);
        Map<Integer, Set<ProbePanel>> lwIdProbePanels = new HashMap<>();
        for (LabwareProbe p : probes) {
            lwIdProbePanels.computeIfAbsent(p.getLabwareId(), k -> new HashSet<>()).add(p.getProbePanel());
        }
        for (CytData d : data) {
            Set<Operation> ops = slotProbeOps.get(d.cytAction.getSource().getId());
            if (!nullOrEmpty(ops)) {
                Operation op = ops.stream()
                        .max(Comparator.naturalOrder())
                        .orElse(null);
                if (op != null) {
                    d.row.setProbeHybStart(op.getPerformed());
                    d.users.add(op.getUser());
                }
            }
            Set<ProbePanel> probePanels = lwIdProbePanels.get(d.cytAction.getSource().getLabwareId());
            if (!nullOrEmpty(probePanels)) {
                String probeNames = probePanels.stream()
                        .map(ProbePanel::getName)
                        .sorted()
                        .collect(joining(", "));
                d.row.setProbePanels(probeNames);
            }
        }
    }

    /** Loads info for probe hyb qc on the cyt sources */
    void loadProbeQC(List<CytData> data, Set<Integer> sourceSlotIds) {
        OperationType qcOpType = opTypeRepo.getByName("Probe hybridisation QC");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(qcOpType, sourceSlotIds);
        Map<Integer, Operation> opMap = latestOps(ops);
        for (CytData d : data) {
            Operation op = opMap.get(d.cytAction.getSource().getId());
            if (op != null) {
                d.row.setProbeHybEnd(op.getPerformed());
                d.users.add(op.getUser());
            }
        }
    }

    /** Loads tissue coverage measurement for the cyt sources */
    void loadTissueCoverage(List<CytData> data, Set<Integer> sourceSlotIds) {
        OperationType opType = opTypeRepo.getByName("Tissue coverage");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, sourceSlotIds);
        Map<Integer, Operation> opMap = ops.stream().collect(inMap(Operation::getId));
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(opMap.keySet());
        Map<SlotIdSampleId, Measurement> ssCoverage = new HashMap<>();
        for (Measurement m : measurements) {
            if (m.getName().equalsIgnoreCase("Tissue coverage")) {
                ssCoverage.put(new SlotIdSampleId(m.getSlotId(), m.getSampleId()), m);
            }
        }
        for (CytData d : data) {
            Measurement m = ssCoverage.get(new SlotIdSampleId(d.cytAction.getSource(), d.cytAction.getSourceSample()));
            if (m != null) {
                d.row.setTissueCoverage(m.getValue());
                Operation op = opMap.get(m.getOperationId());
                if (op != null) {
                    d.users.add(op.getUser());
                }
            }
        }
    }

    /** Loads Cq measurement from qPCR ops on future lw */
    void loadQPCR(List<CytData> data, Posterity posterity, Set<Integer> allDestSlotIds) {
        loadMeasurement(data, posterity, allDestSlotIds, "qPCR", "Cq value", CytassistOverview::setQpcrResult);
    }

    /** Loads Cq measurement from amplification ops on future lw */
    void loadAmpCq(List<CytData> data, Posterity posterity, Set<Integer> allDestSlotIds) {
        loadMeasurement(data, posterity, allDestSlotIds, "Amplification", "Cq value", CytassistOverview::setAmplificationCq);
    }

    /** Loads details of dual index ops on future lw */
    void loadDualIndex(List<CytData> data, Posterity posterity, Set<Integer> allDestSlotIds) {
        OperationType opType = opTypeRepo.getByName("Dual index plate");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, allDestSlotIds);
        Map<Integer, Operation> opMap = ops.stream().collect(inMap(Operation::getId));
        List<ReagentAction> ras = reagentActionRepo.findAllByOperationIdIn(opMap.keySet());
        Set<Integer> reagentPlateIds = ras.stream()
                .map(ra -> ra.getReagentSlot().getPlateId())
                .collect(toSet());
        Map<Integer, ReagentPlate> rpMap = stream(reagentPlateRepo.findAllById(reagentPlateIds))
                .collect(inMap(ReagentPlate::getId));

        Map<Integer, ReagentAction> slotRa = new HashMap<>();
        for (ReagentAction ra : ras) {
            Integer slotId = ra.getDestination().getId();
            ReagentAction prevRa = slotRa.get(slotId);
            if (raSupersedes(ra, prevRa, opMap)) {
                slotRa.put(slotId, ra);
            }
        }
        for (CytData d : data) {
            ReagentAction found = null;
            for (SlotSample ss : posterity.descendents(new SlotSample(d.cytAction.getSource(), d.cytAction.getSourceSample()))) {
                ReagentAction ra = slotRa.get(ss.slotId());
                if (raSupersedes(ra, found, opMap)) {
                    found = ra;
                }
            }
            if (found != null) {
                Operation op = opMap.get(found.getOperationId());
                if (op != null) {
                    d.users.add(op.getUser());
                }
                d.row.setDualIndexPlateWell(found.getReagentSlot().getAddress().toString());
                ReagentPlate rp = rpMap.get(found.getReagentSlot().getPlateId());
                d.row.setDualIndexPlateType(rp==null ? null : rp.getPlateType());
            }
        }
    }

    /** Loads details (including measurements and comments) of Visium concentration ops on future lw */
    void loadVisiumConcentration(List<CytData> data, Posterity posterity, Set<Integer> allDestSlotIds) {
        OperationType opType = opTypeRepo.getByName("Visium concentration");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, allDestSlotIds);
        Set<Integer> opIds = ops.stream().map(Operation::getId).collect(toSet());
        Map<Integer, Set<Measurement>> opMeasurements = compileMapToSet(Measurement::getOperationId,
                measurementRepo.findAllByOperationIdIn(opIds));
        Map<Integer, Set<OperationComment>> opComments = compileMapToSet(OperationComment::getOperationId,
                opComRepo.findAllByOperationIdIn(opIds));

        Map<SlotIdSampleId, Operation> ssIdOps = new HashMap<>();
        for (Operation op : ops) {
            for (Action a : op.getActions()) {
                SlotIdSampleId ssId = new SlotIdSampleId(a.getDestination(), a.getSample());
                Operation prevOp = ssIdOps.get(ssId);
                if (prevOp==null || prevOp.compareTo(op) < 0) {
                    ssIdOps.put(ssId, op);
                }
            }
        }
        for (CytData d : data) {
            Operation found = null;
            Set<SlotIdSampleId> foundSsIds = null;

            for (SlotSample ss : posterity.descendents(new SlotSample(d.cytAction.getSource(), d.cytAction.getSourceSample()))) {
                SlotIdSampleId ssId = new SlotIdSampleId(ss.slotId(), ss.sampleId());
                Operation op = ssIdOps.get(ssId);
                if (op != null) {
                    if (found == null || found.compareTo(op) < 0) {
                        found = op;
                        foundSsIds = new HashSet<>();
                        foundSsIds.add(ssId);
                    } else if (found == op) {
                        foundSsIds.add(ssId);
                    }
                }
            }
            if (found != null) {
                d.users.add(found.getUser());
                Set<Measurement> measurements = opMeasurements.get(found.getId());
                if (!nullOrEmpty(measurements)) {
                    for (Measurement meas : measurements) {
                        if (measurementApplies(meas, foundSsIds)) {
                            String name = meas.getName();
                            if (name.equalsIgnoreCase("cDNA concentration")
                                || name.equalsIgnoreCase("Library concentration")) {
                                d.row.setVisiumConcentrationType(name);
                                d.row.setVisiumConcentrationValue(meas.getValue());
                            } else if (name.equalsIgnoreCase("Average size")) {
                                d.row.setVisiumConcentrationAverageSize(meas.getValue());
                            }
                        }
                    }
                }
                Set<OperationComment> opComs = opComments.get(found.getId());
                if (!nullOrEmpty(opComs)) {
                    for (OperationComment oc : opComs) {
                        if (oc.getComment().getCategory().equalsIgnoreCase("size range")
                                && commentApplies(oc, foundSsIds)) {
                            d.row.setVisiumConcentrationRange(oc.getComment().getText());
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds the latest future labware (judging lw by the earliest op performed into the lw).
     * Looks up releases on those labware, if they are set as released
     **/
    void loadLatestLabware(List<CytData> data, Posterity posterity) {
        Set<Integer> labwareIds = posterity.getLeafs().stream()
                .map(ss -> ss.slot().getLabwareId())
                .collect(toSet());
        Map<Integer, Labware> lwMap = lwRepo.findAllByIdIn(labwareIds).stream().collect(inMap(Labware::getId));
        Map<Integer, LocalDateTime> lwTimes = opRepo.findEarliestPerformedIntoLabware(labwareIds);
        Set<Integer> releasedLwIds = lwTimes.keySet()
                .stream()
                .filter(lwId -> lwMap.get(lwId).isReleased())
                .collect(toSet());
        Map<Integer, LocalDateTime> releaseTime = new HashMap<>(releasedLwIds.size());
        List<Release> releases = releaseRepo.findAllByLabwareIdIn(releasedLwIds);
        for (Release release : releases) {
            LocalDateTime savedTime = releaseTime.get(release.getLabware().getId());
            if (savedTime == null || savedTime.isBefore(release.getReleased())) {
                releaseTime.put(release.getLabware().getId(), release.getReleased());
            }
        }
        for (CytData d : data) {
            Labware foundLw = null;
            LocalDateTime foundTime = null;
            for (SlotSample ss : posterity.descendents(new SlotSample(d.cytAction.getSource(), d.cytAction.getSourceSample()))) {
                LocalDateTime lwTime = lwTimes.get(ss.slot().getLabwareId());
                Labware lw = lwMap.get(ss.slot().getLabwareId());
                if (lw != null && lwTime != null && (foundTime==null || foundTime.isBefore(lwTime))) {
                    foundLw = lw;
                    foundTime = lwTime;
                }
            }
            if (foundLw != null) {
                d.row.setLatestBarcode(foundLw.getBarcode());
                d.row.setLatestState(foundLw.getState().toString());
                LocalDateTime time = releaseTime.get(foundLw.getId());
                d.row.setLatestBarcodeReleased(time);
            }
        }
    }

    /** Loads flags on the involved lw and all future lw */
    void loadFlags(List<CytData> data, Posterity posterity) {
        Set<Integer> labwareIds = new HashSet<>();
        for (CytData d : data) {
            labwareIds.add(d.sourceLabware.getId());
            labwareIds.add(d.destLabware.getId());
        }
        for (SlotSample ss : posterity.keySet()) {
            labwareIds.add(ss.slot().getLabwareId());
        }
        Map<Integer, Set<String>> lwFlags = new HashMap<>();
        for (LabwareFlag lf : lwFlagRepo.findAllByLabwareIdIn(labwareIds)) {
            lwFlags.computeIfAbsent(lf.getLabware().getId(), k -> new HashSet<>()).add(lf.getDescription());
        }
        for (CytData d : data) {
            Set<String> flags = new HashSet<>();
            Set<String> newFlags = lwFlags.get(d.sourceLabware.getId());
            if (!nullOrEmpty(newFlags)) {
                flags.addAll(newFlags);
            }
            newFlags = lwFlags.get(d.destLabware.getId());
            if (!nullOrEmpty(newFlags)) {
                flags.addAll(newFlags);
            }
            for (SlotSample ss : posterity.descendents(new SlotSample(d.cytAction.getSource(), d.cytAction.getSourceSample()))) {
                newFlags = lwFlags.get(ss.slot().getLabwareId());
                if (!nullOrEmpty(newFlags)) {
                    flags.addAll(newFlags);
                }
            }
            if (!flags.isEmpty()) {
                d.row.setFlags(String.join("; ", flags));
            }
        }
    }

    /** Combines info collected on users for each row */
    void setUsers(List<CytData> data) {
        for (CytData d : data) {
            if (!nullOrEmpty(d.users)) {
                d.row.setUsers(d.users.stream().map(User::getUsername).collect(joining(", ")));
            }
        }
    }

    /** Does the given measurement apply to any of the given slot/sample pairs */
    static boolean measurementApplies(Measurement meas, Collection<SlotIdSampleId> ssIds) {
        return ssIds.stream().anyMatch(ssId -> measurementApplies(meas, ssId));
    }
    /** Does the given measurement apply to the given slot/sample? */
    static boolean measurementApplies(Measurement meas, SlotIdSampleId ssId) {
        return (meas.getSampleId() == null || meas.getSampleId().equals(ssId.getSampleId()))
                && (meas.getSlotId() == null || meas.getSlotId().equals(ssId.getSlotId()));
    }
    /** Does the given opcom apply to any of the given slot/sample pairs */
    static boolean commentApplies(OperationComment oc, Collection<SlotIdSampleId> ssIds) {
        return ssIds.stream().anyMatch(ssId -> commentApplies(oc, ssId));
    }
    /** Does the given opcom apply to the given slot/sample? */
    static boolean commentApplies(OperationComment oc, SlotIdSampleId ssId) {
        return (oc.getSampleId() == null || oc.getSampleId().equals(ssId.getSampleId()))
                && (oc.getSlotId() == null || oc.getSlotId().equals(ssId.getSlotId()));
    }

    /**
     * Does the given ReagentAction {@code a} supersede the second ReagentAction {@code b}?
     * Non-null supersedes null.
     * Later supersedes earlier.
     * @param a first reagentaction
     * @param b second reagentaction
     * @param opMap map to look up operations referred to by the actions
     * @return true if a supersedes b
     */
    static boolean raSupersedes(ReagentAction a, ReagentAction b, Map<Integer, Operation> opMap) {
        if (a==null) {
            return false;
        }
        if (b==null) {
            return true;
        }
        Operation aOp = opMap.get(a.getOperationId());
        Operation bOp = opMap.get(b.getOperationId());
        return (aOp != null && bOp != null && aOp.compareTo(bOp) > 0);
    }

    /** Collects the source slot ids of the given cyt data */
    Set<Integer> sourceSlotIds(List<CytData> data) {
        return data.stream()
                .map(d -> d.cytAction.getSource().getId())
                .collect(toSet());
    }

    /** Gets all the slot ids that are keys in the posterity (i.e. all future slot ids) */
    Set<Integer> destSlotIds(Posterity posterity) {
        return posterity.keySet().stream().map(SlotSample::slotId).collect(toSet());
    }

    /** Gets the latest op from each destination slot id */
    Map<Integer, Operation> latestOps(List<Operation> ops) {
        Map<Integer, Operation> opMap = new HashMap<>();
        for (Operation op : ops) {
            for (Action a : op.getActions()) {
                Operation savedOp = opMap.get(a.getDestination().getId());
                if (savedOp == null || savedOp.compareTo(op) < 0) {
                    opMap.put(a.getDestination().getId(), op);
                }
            }
        }
        return opMap;
    }

    /** Collects stuff into a multi-valued map */
    static <K, E> Map<K, Set<E>> compileMapToSet(Function<? super E, ? extends K> keyMapper, Collection<? extends E> items) {
        Map<K, Set<E>> map = new HashMap<>();
        for (E item : items) {
            K key = keyMapper.apply(item);
            map.computeIfAbsent(key, k -> new HashSet<>()).add(item);
        }
        return map;
    }

    /**
     * Loads measurements for a given op name and measurement name from future labware.
     * Later measurements supersede earlier ones.
     * The value of the measurement is set using the given setter function.
     */
    void loadMeasurement(List<CytData> data, Posterity posterity, Set<Integer> destSlotIds,
                         String opName, String measurementName, BiConsumer<CytassistOverview, String> setter) {
        OperationType opType = opTypeRepo.getByName(opName);
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, destSlotIds);
        Map<Integer, Operation> opMap = ops.stream().collect(inMap(Operation::getId));
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(opMap.keySet());
        Map<SlotIdSampleId, String> ssMeas = new HashMap<>();
        Map<SlotIdSampleId, Operation> ssOp = new HashMap<>();
        for (Measurement m : measurements) {
            if (m.getName().equalsIgnoreCase(measurementName)) {
                SlotIdSampleId key = new SlotIdSampleId(m.getSlotId(), m.getSampleId());
                Operation op = opMap.get(m.getOperationId());
                Operation savedOp = ssOp.get(key);
                if (savedOp == null || savedOp.compareTo(op) < 0) {
                    ssMeas.put(key, m.getValue());
                    ssOp.put(key, op);
                }
            }
        }
        for (CytData d : data) {
            SlotSample start = new SlotSample(d.cytAction.getSource(), d.cytAction.getSourceSample());
            Operation foundOp = null;
            String foundValue = null;
            for (SlotSample ss : posterity.descendents(start)) {
                SlotIdSampleId key = new SlotIdSampleId(ss.slotId(), ss.sampleId());
                Operation op = ssOp.get(key);
                if (op != null && (foundOp==null || foundOp.compareTo(op) < 0)) {
                    foundOp = op;
                    foundValue = ssMeas.get(key);
                }
            }
            if (foundOp != null) {
                setter.accept(d.row, foundValue);
                d.users.add(foundOp.getUser());
            }
        }
    }

    /**
     * Intermediate data used to build CytassistOverviews.
     * Each element of this data corresponds to an action in a cytassist op.
     */
    static class CytData {
        CytassistOverview row;
        Action cytAction;
        Operation cytOp;
        Labware sourceLabware;
        Labware destLabware;
        Set<User> users;

        public CytData(Action cytAction, Operation cytOp) {
            this.cytAction = cytAction;
            this.cytOp = cytOp;
            this.row = new CytassistOverview();
            this.users = new HashSet<>();
            if (cytOp != null && cytOp.getUser() != null) {
                this.users.add(cytOp.getUser());
            }
        }
    }
}
