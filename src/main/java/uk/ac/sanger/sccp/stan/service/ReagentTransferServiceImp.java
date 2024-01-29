package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.service.operation.BioStateReplacer;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Implementation of reagent transfer service.
 * @author dr6
 */
@Service
public class ReagentTransferServiceImp implements ReagentTransferService {
    private final OperationTypeRepo opTypeRepo;
    private final ReagentActionRepo reagentActionRepo;
    private final LabwareRepo lwRepo;

    private final ReagentTransferValidatorService rtValidatorService;
    private final LabwareValidatorFactory lwValFactory;

    private final OperationService opService;
    private final ReagentPlateService reagentPlateService;
    private final WorkService workService;
    private final BioStateReplacer bioStateReplacer;

    @Autowired
    public ReagentTransferServiceImp(OperationTypeRepo opTypeRepo, ReagentActionRepo reagentActionRepo,
                                     LabwareRepo lwRepo,
                                     ReagentTransferValidatorService rtValidatorService,
                                     LabwareValidatorFactory lwValFactory,
                                     OperationService opService, ReagentPlateService reagentPlateService,
                                     WorkService workService, BioStateReplacer bioStateReplacer) {
        this.opTypeRepo = opTypeRepo;
        this.reagentActionRepo = reagentActionRepo;
        this.lwRepo = lwRepo;
        this.rtValidatorService = rtValidatorService;
        this.lwValFactory = lwValFactory;
        this.opService = opService;
        this.reagentPlateService = reagentPlateService;
        this.workService = workService;
        this.bioStateReplacer = bioStateReplacer;
    }

    @Override
    public OperationResult perform(User user, ReagentTransferRequest request) throws ValidationException {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");
        final Set<String> problems = new LinkedHashSet<>();
        OperationType opType = loadOpType(problems, request.getOperationType());
        Labware lw = loadLabware(problems, request.getDestinationBarcode());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        UCMap<ReagentPlate> reagentPlates = loadReagentPlates(request.getTransfers());
        String plateType = checkPlateType(problems, reagentPlates.values(), request.getPlateType());
        validateTransfers(problems, request.getTransfers(), reagentPlates, lw);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return record(user, opType, work, request.getTransfers(), reagentPlates, lw, plateType);
    }

    @Override
    public OperationType loadOpType(Collection<String> problems, String opName) {
        if (opName==null || opName.isEmpty()) {
            problems.add("No operation type specified.");
            return null;
        }
        var optOpType = opTypeRepo.findByName(opName);
        if (optOpType.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opName));
            return null;
        }
        OperationType opType = optOpType.get();
        if (!opType.transfersReagent() || !opType.inPlace() || opType.has(OperationTypeFlag.RESULT)
                || opType.has(OperationTypeFlag.ANALYSIS) || opType.sourceMustBeBlock()) {
            problems.add("Operation type "+opType.getName()+" cannot be used in this request.");
        }
        return opType;
    }

    /**
     * Loads and checks the labware
     * @param problems receptacle for problems
     * @param barcode the barcode
     * @return the labware loaded, if any
     */
    public Labware loadLabware(Collection<String> problems, String barcode) {
        var val = lwValFactory.getValidator();
        List<Labware> lws = val.loadLabware(lwRepo, List.of(barcode));
        val.validateSources();
        problems.addAll(val.getErrors());
        return (lws.isEmpty() ? null : lws.getFirst());
    }

    @Override
    public UCMap<ReagentPlate> loadReagentPlates(Collection<ReagentTransfer> transfers) {
        Set<String> barcodes = transfers.stream()
                .map(ReagentTransfer::getReagentPlateBarcode)
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(toSet());
        return reagentPlateService.loadPlates(barcodes);
    }

    @Override
    public String checkPlateType(Collection<String> problems, Collection<ReagentPlate> existingPlates, String plateTypeArg) {
        String plateType = ReagentPlate.canonicalPlateType(plateTypeArg);
        if (plateType==null) {
            problems.add("Unknown plate type: "+repr(plateTypeArg));
            return null;
        }
        List<String> nonMatching = existingPlates.stream()
                .filter(rp -> !plateType.equalsIgnoreCase(rp.getPlateType()))
                .map(ReagentPlate::getBarcode)
                .toList();
        if (!nonMatching.isEmpty()) {
            problems.add("The given plate type "+plateType+" does not match the existing plate "+nonMatching+".");
        }
        return plateType;
    }

    /**
     * Checks for problems with the specified transfers. Problems include:<ul>
     *     <li>invalid or missing reagent plate barcodes</li>
     *     <li>invalid or missing reagent slot addresses</li>
     *     <li>invalid or missing destination slot addresses</li>
     *     <li>reagent slot addresses already used in a previous operation</li>
     *     <li>reagent slot addresses given multiple times in this request</li>
     * </ul>
     * @param problems receptacle for problems
     * @param transfers the transfers to validate
     * @param reagentPlates the existing reagent plates
     * @param lw the destination labware
     */
    public void validateTransfers(Collection<String> problems, Collection<ReagentTransfer> transfers,
                                  UCMap<ReagentPlate> reagentPlates, Labware lw) {
        rtValidatorService.validateTransfers(problems, transfers, reagentPlates, lw==null ? null : lw.getLabwareType());
    }

    @Override
    public OperationResult record(User user, OperationType opType, Work work, Collection<ReagentTransfer> transfers,
                                  UCMap<ReagentPlate> reagentPlates, Labware lw, String plateType) {
        createReagentPlates(transfers, reagentPlates, plateType);
        List<Action> actions = bioStateReplacer.updateBioStateInPlace(opType.getNewBioState(), lw);
        Operation op = createOperation(user, opType, work, lw, actions);
        recordTransfers(transfers, reagentPlates, lw, op.getId());
        return new OperationResult(List.of(op), List.of(lw));
    }

    /**
     * Creates reagent plates for the barcodes specified in the transfers if they are not already in the given map.
     * @param transfers the transfers requested
     * @param reagentPlates the cache and receptacle for reagent plates, mapped from their barcodes
     * @param plateType the plate type for new plates
     */
    public void createReagentPlates(Collection<ReagentTransfer> transfers,
                                    UCMap<ReagentPlate> reagentPlates, String plateType) {
        for (ReagentTransfer transfer : transfers) {
            String barcode = transfer.getReagentPlateBarcode();
            if (reagentPlates.get(barcode)==null) {
                reagentPlates.put(barcode, reagentPlateService.createReagentPlate(barcode, plateType));
            }
        }
    }

    /**
     * Creates an operation in place
     * @param user the user responsible for the operation
     * @param opType the type of operation
     * @param work the work (if any) to link
     * @param lw the labware to record the operation on
     * @param actions the specific actions to record (optional)
     * @return the newly recorded operation
     */
    public Operation createOperation(User user, OperationType opType, Work work, Labware lw, List<Action> actions) {
        Operation op;
        if (actions==null) {
            op = opService.createOperationInPlace(opType, user, lw, null, null);
        } else {
            op = opService.createOperation(opType, user, actions, null);
        }
        if (work!=null) {
            workService.link(work, List.of(op));
        }
        return op;
    }

    /**
     * Records reagent actions describing the given transfers
     * @param transfers the transfers to record
     * @param reagentPlates the map to look up reagent plates from their barcodes
     * @param lw the destination labware
     * @param opId the operation id to link with the reagent actions
     */
    public void recordTransfers(Collection<ReagentTransfer> transfers, UCMap<ReagentPlate> reagentPlates,
                                Labware lw, Integer opId) {
        List<ReagentAction> ras = transfers.stream()
                .map(ra -> {
                    ReagentPlate rp = reagentPlates.get(ra.getReagentPlateBarcode());
                    ReagentSlot rs = rp.getSlot(ra.getReagentSlotAddress());
                    Slot ds = lw.getSlot(ra.getDestinationAddress());
                    return new ReagentAction(null, opId, rs, ds);
                })
                .collect(toList());
        reagentActionRepo.saveAll(ras);
    }

    /** A helper class used for deduping and tracking errors in specified slots. */
    record RSlot(Address address, String barcode) {
        RSlot(Address address, String barcode) {
            this.address = address;
            this.barcode = barcode.toUpperCase();
        }

        @Override
        public String toString() {
            return "slot "+address+" in reagent plate "+barcode;
        }
    }

}
