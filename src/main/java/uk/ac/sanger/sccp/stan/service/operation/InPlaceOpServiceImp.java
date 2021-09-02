package uk.ac.sanger.sccp.stan.service.operation;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.InPlaceOpRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class InPlaceOpServiceImp implements InPlaceOpService {
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final OperationService opService;
    private final WorkService workService;

    private final EquipmentRepo equipmentRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;

    public InPlaceOpServiceImp(LabwareValidatorFactory labwareValidatorFactory,
                               OperationService opService, WorkService workService,
                               EquipmentRepo equipmentRepo, OperationTypeRepo opTypeRepo, LabwareRepo lwRepo) {
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.opService = opService;
        this.workService = workService;
        this.equipmentRepo = equipmentRepo;
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
    }

    @Override
    public OperationResult record(User user, InPlaceOpRequest request) {
        final Set<String> problems = new LinkedHashSet<>();
        Collection<Labware> labware = validateLabware(problems, request.getBarcodes());
        OperationType opType = validateOpType(problems, request.getOperationType(), labware);
        Equipment eq = validateEquipment(problems, request.getEquipmentId());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return createOperations(user, labware, opType, eq, work);
    }

    /**
     * Looks up the labware barcodes and checks for problems.
     * @param problems the receptacle for problems found
     * @param barcodes the barcodes to look up
     * @return the labware loaded, if any
     */
    public Collection<Labware> validateLabware(Collection<String> problems, Collection<String> barcodes) {
        LabwareValidator lv = labwareValidatorFactory.getValidator();
        lv.setUniqueRequired(true);
        lv.loadLabware(lwRepo, barcodes);
        lv.validateSources();
        problems.addAll(lv.getErrors());
        return lv.getLabware();
    }

    /**
     * Looks up the op type and checks for problems
     * @param problems the receptacle for problems found
     * @param opTypeName the name of the operation type to look up
     * @return the operation type loaded, if any
     */
    public OperationType validateOpType(Collection<String> problems, String opTypeName, Collection<Labware> labware) {
        if (opTypeName==null || opTypeName.isEmpty()) {
            problems.add("No operation type specified.");
            return null;
        }
        Optional<OperationType> opt = opTypeRepo.findByName(opTypeName);
        if (opt.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opTypeName));
            return null;
        }
        OperationType opType = opt.get();
        if (!opType.inPlace()) {
            problems.add("Operation type "+opType.getName()+" cannot be recorded in place.");
        }
        if (opType.sourceMustBeBlock() && labware!=null && !labware.stream().allMatch(lw -> lw.getFirstSlot().isBlock())) {
            problems.add("Operation type "+opType.getName()+" can only be recorded on a block.");
        }
        return opt.get();
    }

    /**
     * Looks up the equipment and checks for problems
     * @param problems the receptacle for problems found
     * @param equipmentId the id of the equipment to look up, if any
     * @return the equipment found, if any
     */
    public Equipment validateEquipment(Collection<String> problems, Integer equipmentId) {
        if (equipmentId==null) {
            return null;
        }
        Optional<Equipment> opt = equipmentRepo.findById(equipmentId);
        if (opt.isEmpty()) {
            problems.add("Unknown equipment id: "+equipmentId);
            return null;
        }
        return opt.get();
    }

    /**
     * Gets actions appropriate for an in-place operation on the given labware
     * @param lw an item of labware
     * @return a list of new unsaved actions
     */
    public List<Action> makeActions(Labware lw) {
        return lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream()
                        .map(sam -> new Action(null, null, slot, slot, sam, sam))
                ).collect(toList());
    }

    /**
     * Creates an in place operation for one item of labware
     * @param user the user responsible for the operation
     * @param lw the item of labware
     * @param opType the type of operation to record
     * @param opModifier a function to run on the operation as it is being created
     * @return the newly created operation
     */
    public Operation createOperation(User user, Labware lw, OperationType opType, Consumer<Operation> opModifier) {
        List<Action> actions = makeActions(lw);
        return opService.createOperation(opType, user, actions, null, opModifier);
    }

    /**
     * Creates the indicated operations, linking them to the specified equipment and work
     * @param user the user responsible for the operations
     * @param labware the labware involved in the operations
     * @param opType the type of operation being recorded
     * @param equipment the equipment (if any) to link to the operations
     * @param work the work (if any) to link to the operations
     * @return the operations and labware
     */
    public OperationResult createOperations(User user, Collection<Labware> labware, OperationType opType,
                                           Equipment equipment, Work work) {
        Consumer<Operation> opModifier = (equipment==null ? null : (op -> op.setEquipment(equipment)));
        List<Operation> ops = labware.stream().map(lw -> createOperation(user, lw, opType, opModifier))
                .collect(toList());
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, labware);
    }

}
