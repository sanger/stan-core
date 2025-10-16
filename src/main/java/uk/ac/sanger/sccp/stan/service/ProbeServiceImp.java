package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.ProbePanel.ProbeType;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest.ProbeLot;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest.ProbeOperationLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class ProbeServiceImp implements ProbeService {
    public static final String
            PROBE_HYB_CYT = "Probe hybridisation Cytassist",
            PROBE_HYB_XEN = "Probe hybridisation Xenium";
    public static final String KIT_COSTING_NAME = "kit costing";
    public static final String REAGENT_LOT_NAME = "reagent lot";

    private final LabwareValidatorFactory lwValFac;
    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;
    private final ProbePanelRepo probePanelRepo;
    private final LabwareProbeRepo lwProbeRepo;
    private final LabwareNoteRepo noteRepo;
    private final OperationService opService;
    private final WorkService workService;
    private final Validator<String> probeLotValidator;
    private final Validator<String> reagentLotValidator;
    private final Clock clock;

    @Autowired
    public ProbeServiceImp(LabwareValidatorFactory lwValFac,
                           LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo,
                           ProbePanelRepo probePanelRepo, LabwareProbeRepo lwProbeRepo, LabwareNoteRepo noteRepo,
                           OperationService opService, WorkService workService,
                           @Qualifier("probeLotNumberValidator") Validator<String> probeLotValidator,
                           @Qualifier("reagentLotValidator") Validator<String> reagentLotValidator,
                           Clock clock) {
        this.lwValFac = lwValFac;
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.probePanelRepo = probePanelRepo;
        this.lwProbeRepo = lwProbeRepo;
        this.noteRepo = noteRepo;
        this.opService = opService;
        this.workService = workService;
        this.probeLotValidator = probeLotValidator;
        this.reagentLotValidator = reagentLotValidator;
        this.clock = clock;
    }

    /**
     * Gets the probe type expected for the given op type.
     * @param opType operation type in progress
     * @return the appropriate probe type, or null if none is appropriate
     */
    public ProbeType opProbeType(OperationType opType) {
        if (opType != null) {
            if (opType.getName().equalsIgnoreCase(PROBE_HYB_CYT)) {
                return ProbeType.cytassist;
            }
            if (opType.getName().equalsIgnoreCase(PROBE_HYB_XEN)) {
                return ProbeType.xenium;
            }
        }
        return null;
    }

    @Override
    public OperationResult recordProbeOperation(User user, ProbeOperationRequest request) throws ValidationException {
        final Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            throw new ValidationException("The request could not be validated.", problems);
        }
        if (nullOrEmpty(request.getLabware())) {
            problems.add("No labware specified.");
            throw new ValidationException("The request could not be validated.", problems);
        }
        UCMap<Labware> labware = validateLabware(problems, request.getLabware().stream()
                .map(ProbeOperationLabware::getBarcode));
        checkAllAddresses(problems, request.getLabware(), labware);
        OperationType opType = validateOpType(problems, request.getOperationType());
        ProbeType probeType = opProbeType(opType);
        if (probeType == null && opType != null && problems.isEmpty()) {
            problems.add("Probe type unknown for operation "+opType+".");
        }
        List<String> workNumbers = request.getLabware().stream()
                .map(ProbeOperationLabware::getWorkNumber)
                .toList();
        UCMap<Work> work = workService.validateUsableWorks(problems, workNumbers);
        checkReagentLots(problems, request.getLabware());
        UCMap<ProbePanel> probes = validateProbes(problems, probeType, request.getLabware());
        UCMap<ProbePanel> spikes = checkSpikes(problems, request.getLabware());
        if (request.getPerformed()!=null) {
            validateTimestamp(problems, request.getPerformed(), labware);
        }

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return perform(user, request.getLabware(), opType, request.getPerformed(), labware, probes, spikes, work);
    }

    /**
     * Loads and validates the labware in the request.
     * Labware specified should exist, be distinct, and be usable.
     * @param problems receptacle for problems
     * @param barcodeStream the barcodes to load
     * @return the loaded labware in a map from their barcode
     */
    public UCMap<Labware> validateLabware(Collection<String> problems, Stream<String> barcodeStream) {
        final List<String> barcodes = new ArrayList<>();
        final boolean[] anyMissing = {false};
        barcodeStream.forEach(bc -> {
            if (nullOrEmpty(bc)) {
                anyMissing[0] = true;
            } else {
                barcodes.add(bc);
            }
        });
        if (anyMissing[0]) {
            if (barcodes.isEmpty()) {
                problems.add("No labware barcodes supplied.");
            } else {
                problems.add("Labware barcode missing.");
            }
        }
        if (barcodes.isEmpty()) {
            return new UCMap<>(0);
        }
        LabwareValidator val = lwValFac.getValidator();
        val.loadLabware(lwRepo, barcodes);
        val.setUniqueRequired(true);
        val.validateSources();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Checks for problems with specified addresses in all labware.
     * Where no addresses are specified, they are not checked.
     * @param problems receptacle for problems
     * @param pols details of labware in request
     * @param lwMap labware looked up by barcode
     */
    public void checkAllAddresses(Collection<String> problems, List<ProbeOperationLabware> pols, UCMap<Labware> lwMap) {
        for (ProbeOperationLabware pol : pols) {
            Labware lw = lwMap.get(pol.getBarcode());
            if (lw != null && !nullOrEmpty(pol.getAddresses())) {
                checkAddresses(problems, pol.getAddresses(), lw);
            }
        }
    }

    /**
     * Checks for problems with addresses in specified item of labware
     * @param problems receptacle for problems
     * @param addresses the addresses to check
     * @param lw the labware to check the slots in
     */
    public void checkAddresses(Collection<String> problems, List<Address> addresses, Labware lw) {
        Set<Address> invalidAddresses = new LinkedHashSet<>();
        Set<Address> emptyAddresses = new LinkedHashSet<>();
        boolean anyNull = false;
        for (Address address : addresses) {
            if (address == null) {
                anyNull = true;
            } else {
                var optSlot = lw.optSlot(address);
                if (optSlot.isEmpty()) {
                    invalidAddresses.add(address);
                } else if (optSlot.get().getSamples().isEmpty()) {
                    emptyAddresses.add(address);
                }
            }
        }
        if (anyNull) {
            problems.add(String.format("Null address given for labware %s.", lw.getBarcode()));
        }
        if (!invalidAddresses.isEmpty()) {
            problems.add(String.format("Slot not present in labware %s: %s", lw.getBarcode(), invalidAddresses));
        }
        if (!emptyAddresses.isEmpty()) {
            problems.add(String.format("Slot contains no samples in labware %s: %s", lw.getBarcode(), emptyAddresses));
        }
    }

    /**
     * Loads the specified operation type and checks it seems to be appropriate
     * @param problems receptacle for problems
     * @param opName name of the operation type to load
     * @return the operation type loaded, or null
     */
    public OperationType validateOpType(Collection<String> problems, String opName) {
        if (nullOrEmpty(opName)) {
            problems.add("No operation type specified.");
            return null;
        }
        var opt = opTypeRepo.findByName(opName);
        if (opt.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opName));
            return null;
        }
        OperationType opType = opt.get();
        if (!(opType.inPlace() && opType.usesProbes())) {
            problems.add("Operation type "+opType.getName()+" cannot be used in this request.");
        }
        return opType;
    }

    /**
     * Checks reagent lots.
     * Lots are trimmed; empty lots are nulled; missing lots are skipped.
     * @param problems receptacle for problems
     * @param pols details of the request
     */
    public void checkReagentLots(Collection<String> problems, Collection<ProbeOperationLabware> pols) {
        if (pols != null) {
            final Consumer<String> addProblem = problems::add;
            for (ProbeOperationLabware pol : pols) {
                String lot = pol.getReagentLot();
                if (lot != null) {
                    lot = emptyToNull(lot.trim());
                    pol.setReagentLot(lot);
                    if (lot != null) {
                        reagentLotValidator.validate(lot, addProblem);
                    }
                }
            }
        }
    }

    /**
     * Loads and checks the probes indicated in the request, their lot numbers and plexes.
     * @param problems receptacle for problems
     * @param probeType probe type of expected probe panels
     * @param pols the relevant parts of the request
     * @return the probe panels mapped from their names
     */
    public UCMap<ProbePanel> validateProbes(Collection<String> problems, ProbeType probeType, Collection<ProbeOperationLabware> pols) {
        Set<String> probeNames = new LinkedHashSet<>();
        for (ProbeOperationLabware pol : pols) {
            if (nullOrEmpty(pol.getProbes())) {
                problems.add("No probes specified for labware.");
            } else {
                for (ProbeLot pl : pol.getProbes()) {
                    if (nullOrEmpty(pl.getName())) {
                        problems.add("Probe panel name missing.");
                    } else {
                        probeNames.add(pl.getName());
                    }
                    if (!nullOrEmpty(pl.getLot())) {
                        probeLotValidator.validate(pl.getLot(), problems::add);
                    }
                    if (pl.getPlex() != null && pl.getPlex() < 1) {
                        problems.add("Probe plex should be a positive number.");
                    }
                    if (pl.getCosting() == null) {
                        problems.add("Probe costing is missing.");
                    }
                }
            }
        }
        if (probeNames.isEmpty() || probeType==null) {
            return new UCMap<>(0);
        }
        UCMap<ProbePanel> probes = UCMap.from(probePanelRepo.findAllByTypeAndNameIn(probeType, probeNames), ProbePanel::getName);
        List<String> missingProbeNames = probeNames.stream()
                .filter(name -> probes.get(name)==null)
                .toList();
        if (!missingProbeNames.isEmpty()) {
            problems.add("Unknown "+probeType+" probe panels: "+reprCollection(missingProbeNames));
        }
        return probes;
    }

    /**
     * Loads spikes indicated in the request.
     * @param problems receptacle for problems
     * @param pols details of request
     * @return map of spikes from their names
     */
    public UCMap<ProbePanel> checkSpikes(Collection<String> problems, Collection<ProbeOperationLabware> pols) {
        Set<String> spikeNames = pols.stream()
                .map(ProbeOperationLabware::getSpike)
                .filter(name -> !nullOrEmpty(name))
                .collect(toLinkedHashSet());
        if (spikeNames.isEmpty()) {
            return new UCMap<>(0);
        }
        UCMap<ProbePanel> spikes = UCMap.from(probePanelRepo.findAllByTypeAndNameIn(ProbeType.spike, spikeNames), ProbePanel::getName);
        Set<String> unknown = spikeNames.stream().filter(name -> !spikes.containsKey(name)).collect(toLinkedHashSet());
        if (!unknown.isEmpty()) {
            problems.add("Unknown spike names: "+reprCollection(unknown));
        }
        return spikes;
    }

    /**
     * Checks the given timestamp seems to be sensible.
     * A timestamp should not be after the current day.
     * A timestamp should not be before the creation of the indicated labware.
     * @param problems receptacle for problems
     * @param performed the given timestamp
     * @param labware the labware referenced in the request
     */
    public void validateTimestamp(Collection<String> problems, LocalDateTime performed, UCMap<Labware> labware) {
        if (performed.toLocalDate().isAfter(LocalDate.now(clock))) {
            problems.add("The given date is in the future.");
        } else if (labware.values().stream().anyMatch(lw -> lw.getCreated().isAfter(performed))) {
            problems.add("The given date is too early to be valid for the specified labware.");
        }
    }

    /**
     * Records operations as indicated in the given information, which has already been validated.
     * Also links the operations to the indicated works and probes.
     * @param user the user responsible
     * @param pols the requests specifying labware and panels
     * @param opType the type of operation to record
     * @param time the time (if specified) for the operations
     * @param lwMap the indicated labware, mapped from their barcodes
     * @param probeMap the probes indicated in the request, mapped from their names
     * @param spikeMap the spikes indicated in the request, mapped from their names
     * @param workMap the works indicated, mapped from work numbers
     * @return the labware and operations recorded
     */
    public OperationResult perform(User user, Collection<ProbeOperationLabware> pols, OperationType opType,
                                   LocalDateTime time, UCMap<Labware> lwMap, UCMap<ProbePanel> probeMap,
                                   UCMap<ProbePanel> spikeMap, UCMap<Work> workMap) {
        UCMap<Operation> lwOps = makeOps(user, opType, pols, lwMap, time);
        saveKitCostings(pols, lwMap, lwOps);
        saveReagentLots(pols, lwMap, lwOps);
        linkWork(pols, lwOps, workMap);
        saveProbes(pols, lwOps, lwMap, probeMap, spikeMap);
        return assembleResult(pols, lwMap, lwOps);
    }

    /**
     * Creates the requested operations, one for each labware
     * @param user the user responsible
     * @param opType the type of operation
     * @param pols the specification of labware and probes
     * @param lwMap map to look up the labware from its barcode
     * @param time the time (if specified) for the operations
     * @return a map of operation from relevant labware barcode
     */
    public UCMap<Operation> makeOps(User user, OperationType opType, Collection<ProbeOperationLabware> pols,
                                    UCMap<Labware> lwMap, LocalDateTime time) {
        UCMap<Operation> opMap = new UCMap<>(pols.size());
        for (ProbeOperationLabware pol : pols) {
            Labware lw = lwMap.get(pol.getBarcode());
            Operation op = createOp(opType, user, lw, pol.getAddresses());
            opMap.put(lw.getBarcode(), op);
        }
        if (time!=null) {
            for (Operation op : opMap.values()) {
                op.setPerformed(time);
            }
            opRepo.saveAll(opMap.values());
        }
        return opMap;
    }

    /**
     * Creates the operation in place.
     * If addresses is null or empty, all nonempty slots of the labware are included
     * @param opType the type of operation
     * @param user the user responsible
     * @param lw the labware
     * @param addresses the addresses of the slots in the labware
     * @return the created operation
     */
    public Operation createOp(OperationType opType, User user, Labware lw, List<Address> addresses) {
        if (nullOrEmpty(addresses)) {
            return opService.createOperationInPlace(opType, user, lw, null, null);
        }
        Set<Address> addressSet = new HashSet<>(addresses);
        List<Slot> slots = lw.getSlots().stream()
                .filter(slot -> addressSet.contains(slot.getAddress()))
                .toList();
        List<Action> actions = slots.stream()
                .flatMap(slot -> slot.getSamples().stream()
                        .map(sam -> new Action(null, null, slot, slot, sam, sam)))
                .toList();
        return opService.createOperation(opType, user, actions, null);
    }

    /**
     * Saves the kit costings specified as labware notes
     * @param pols the request details
     * @param lwMap the labware mapped from barcode
     * @param lwOps the operations recorded for each labware
     */
    public void saveKitCostings(Collection<ProbeOperationLabware> pols, UCMap<Labware> lwMap, UCMap<Operation> lwOps) {
        List<LabwareNote> notes = pols.stream()
                .filter(pol -> pol.getKitCosting()!=null)
                .map(pol -> {
                    String barcode = pol.getBarcode();
                    return new LabwareNote(null, lwMap.get(barcode).getId(), lwOps.get(barcode).getId(),
                            KIT_COSTING_NAME, pol.getKitCosting().name());
                }).toList();
        if (!notes.isEmpty()) {
            noteRepo.saveAll(notes);
        }
    }

    /**
     * Links the newly created operations to the indicated work
     * @param pols parts of the request linking labware to work
     * @param lwOps map from labware barcode to operation
     * @param workMap works mapped from work number
     */
    public void linkWork(Collection<ProbeOperationLabware> pols, UCMap<Operation> lwOps, UCMap<Work> workMap) {
        UCMap<List<Operation>> workNumberOps = new UCMap<>(workMap.size());
        for (ProbeOperationLabware pol : pols) {
            workNumberOps.computeIfAbsent(pol.getWorkNumber(), k -> new ArrayList<>()).add(lwOps.get(pol.getBarcode()));
        }
        for (var entry : workNumberOps.entrySet()) {
            workService.link(workMap.get(entry.getKey()), entry.getValue());
        }
    }

    /**
     * Records the probes used in operations
     * @param pols specification of probes used in different labware
     * @param lwOps map from labware barcode to newly created operation
     * @param lwMap map from labware barcode to labware
     * @param probeMap map of probe panels from their names
     * @param spikeMap map of spikes from their names
     */
    public void saveProbes(Collection<ProbeOperationLabware> pols, UCMap<Operation> lwOps, UCMap<Labware> lwMap,
                           UCMap<ProbePanel> probeMap, UCMap<ProbePanel> spikeMap) {
        List<LabwareProbe> lwProbes = new ArrayList<>();
        for (ProbeOperationLabware pol : pols) {
            Operation op = lwOps.get(pol.getBarcode());
            Labware lw = lwMap.get(pol.getBarcode());
            for (ProbeLot pl : pol.getProbes()) {
                ProbePanel pp = probeMap.get(pl.getName());
                String lot = nullOrEmpty(pl.getLot()) ? null : pl.getLot().toUpperCase();
                lwProbes.add(new LabwareProbe(null, pp, op.getId(), lw.getId(), lot, pl.getPlex(), pl.getCosting()));
            }
            ProbePanel spike = spikeMap.get(pol.getSpike());
            if (spike != null) {
                lwProbes.add(new LabwareProbe(null, spike, op.getId(), lw.getId(), null, null, null));
            }
        }
        lwProbeRepo.saveAll(lwProbes);
    }

    /**
     * Saves the indicated reagent lots against the indicated labware and ops
     * @param pols request details
     * @param lwMap map to look up labware by barcode
     * @param lwOps map to look up operation by labware barcode
     */
    public void saveReagentLots(Collection<ProbeOperationLabware> pols,
                                UCMap<Labware> lwMap, UCMap<Operation> lwOps) {
        List<LabwareNote> notes = new ArrayList<>();
        for (ProbeOperationLabware pol : pols) {
            String lot = pol.getReagentLot();
            if (!nullOrEmpty(lot)) {
                String barcode = pol.getBarcode();
                Labware lw = lwMap.get(barcode);
                Operation op = lwOps.get(barcode);
                notes.add(new LabwareNote(null, lw.getId(), op.getId(), REAGENT_LOT_NAME, lot));
            }
        }
        if (!notes.isEmpty()) {
            noteRepo.saveAll(notes);
        }
    }

    /**
     * Assembles an operation result whose order matches the order given in the request.
     * @param pols the request specifying labware barcodes
     * @param lwMap map of labware from their barcodes
     * @param lwOps map of operations from the labware barcodes
     * @return the labware and operations used in performing the given requests
     */
    public OperationResult assembleResult(Collection<ProbeOperationLabware> pols, UCMap<Labware> lwMap, UCMap<Operation> lwOps) {
        OperationResult result = new OperationResult();
        for (ProbeOperationLabware pol : pols) {
            result.getLabware().add(lwMap.get(pol.getBarcode()));
            result.getOperations().add(lwOps.get(pol.getBarcode()));
        }
        return result;
    }

}
