package uk.ac.sanger.sccp.stan.service.operation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AliquotRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class AliquotServiceImp implements AliquotService {
    private final LabwareRepo lwRepo;
    private final LabwareTypeRepo lwTypeRepo;
    private final SlotRepo slotRepo;
    private final OperationTypeRepo opTypeRepo;
    private final SampleRepo sampleRepo;
    private final LabwareValidatorFactory lwValFactory;
    private final WorkService workService;
    private final LabwareService lwService;
    private final OperationService opService;

    @Autowired
    public AliquotServiceImp(LabwareRepo lwRepo, LabwareTypeRepo lwTypeRepo, SlotRepo slotRepo,
                             OperationTypeRepo opTypeRepo, SampleRepo sampleRepo,
                             LabwareValidatorFactory lwValFactory,
                             WorkService workService, LabwareService lwService, OperationService opService) {
        this.lwRepo = lwRepo;
        this.lwTypeRepo = lwTypeRepo;
        this.slotRepo = slotRepo;
        this.opTypeRepo = opTypeRepo;
        this.sampleRepo = sampleRepo;
        this.lwValFactory = lwValFactory;
        this.workService = workService;
        this.lwService = lwService;
        this.opService = opService;
    }

    @Override
    public OperationResult perform(User user, AliquotRequest request) throws ValidationException {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");
        final Set<String> problems = new LinkedHashSet<>();
        OperationType opType = loadOpType(problems, request.getOperationType());
        Labware sourceLw = loadSourceLabware(problems, request.getBarcode());
        LabwareType destLwType = loadDestLabwareType(problems, request.getLabwareType());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        validateRequest(problems, request.getNumLabware(), opType, sourceLw);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return execute(user, request.getNumLabware(), opType, sourceLw, destLwType, work);
    }

    /**
     * Loads the op type
     * @param problems receptacle for problems
     * @param opTypeName name of the op type
     * @return the op type loaded, if found
     */
    public OperationType loadOpType(Collection<String> problems, String opTypeName) {
        if (opTypeName==null || opTypeName.isEmpty()) {
            problems.add("No operation type specified.");
            return null;
        }
        Optional<OperationType> optOpType = opTypeRepo.findByName(opTypeName);
        if (optOpType.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opTypeName));
            return null;
        }
        return optOpType.get();
    }

    /**
     * Loads the source labware
     * @param problems receptacle for problems
     * @param barcode barcode of the labware
     * @return the labware loaded, if found
     */
    public Labware loadSourceLabware(Collection<String> problems, String barcode) {
        if (barcode==null || barcode.isEmpty()) {
            problems.add("No source barcode specified.");
            return null;
        }
        LabwareValidator val = lwValFactory.getValidator();
        List<Labware> labwareList = val.loadLabware(lwRepo, List.of(barcode));
        val.setSingleSample(true);
        val.validateSources();
        problems.addAll(val.getErrors());
        return (labwareList.isEmpty() ? null : labwareList.get(0));
    }

    /**
     * Loads the destination labware type
     * @param problems receptacle for problems
     * @param labwareTypeName name of the labware type
     * @return the labware type loaded, if found
     */
    public LabwareType loadDestLabwareType(Collection<String> problems, String labwareTypeName) {
        if (labwareTypeName==null || labwareTypeName.isEmpty()) {
            problems.add("No destination labware type specified.");
            return null;
        }
        Optional<LabwareType> optLwType = lwTypeRepo.findByName(labwareTypeName);
        if (optLwType.isEmpty()) {
            problems.add("Unknown labware type: "+repr(labwareTypeName));
            return null;
        }
        return optLwType.get();
    }

    /**
     * Checks that the request makes sense with the specified entities.
     * @param problems receptacle for problems
     * @param numLabware the number of new labware requested
     * @param opType the loaded operation type (if found)
     * @param sourceLw the loaded source labware (if found)
     */
    public void validateRequest(Collection<String> problems,
                                Integer numLabware, OperationType opType, Labware sourceLw) {
        if (numLabware==null) {
            problems.add("Number of labware not specified.");
        } else if (numLabware <= 0) {
            problems.add("Number of labware must be greater than zero.");
        }
        if (opType!=null) {
            if (sourceLw != null && opType.sourceMustBeBlock() && !sourceLw.getFirstSlot().isBlock()) {
                problems.add("The source must be a block for operation type " + opType.getName() + ".");
            }
            if (opType.inPlace() || Stream.of(OperationTypeFlag.STAIN, OperationTypeFlag.RESULT, OperationTypeFlag.ANALYSIS)
                    .anyMatch(opType::has)) {
                problems.add("Operation type "+ opType.getName()+" cannot be used for aliquoting.");
            }
        }
    }

    /**
     * Creates labware, populates it, records operations, links work.
     * Discards the source labware if necessary.
     * @param user the user responsible for the request
     * @param numLabware the number of new labware to create
     * @param opType the type of operation to record
     * @param sourceLw the source labware
     * @param work the work to link to the operations (optional)
     * @return the new labware and operations
     */
    public OperationResult execute(User user, int numLabware, OperationType opType,
                                   Labware sourceLw, LabwareType destLwType, Work work) {
        List<Labware> destLabware = lwService.create(destLwType, numLabware);
        if (opType.discardSource()) {
            sourceLw.setDiscarded(true);
            sourceLw = lwRepo.save(sourceLw);
        }
        if (opType.markSourceUsed()) {
            sourceLw.setUsed(true);
            sourceLw = lwRepo.save(sourceLw);
        }
        final String sourceBarcode = sourceLw.getBarcode();
        // We previously verified that the labware contained a single sample in a single slot
        Slot sourceSlot = sourceLw.getSlots().stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Sample not found in labware "+sourceBarcode));
        Sample sourceSample = sourceSlot.getSamples().get(0);
        Sample destSample;
        if (opType.getNewBioState()!=null && !opType.getNewBioState().equals(sourceSample.getBioState())) {
            destSample = sampleRepo.save(new Sample(null, sourceSample.getSection(), sourceSample.getTissue(),
                    opType.getNewBioState()));
        } else {
            destSample = sourceSample;
        }
        updateDestinations(destLabware, destSample);
        List<Operation> ops = destLabware.stream()
                .map(lw -> createOperation(user, opType, sourceSample, sourceSlot, destSample, lw.getFirstSlot()))
                .collect(toList());
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, destLabware);
    }

    /**
     * Puts the sample into the first slot of each item of labware
     * @param labware the labware to put the sample in
     * @param sample the sample
     */
    public void updateDestinations(Collection<Labware> labware, Sample sample) {
        List<Slot> slotsToSave = new ArrayList<>(labware.size());
        for (Labware lw : labware) {
            Slot slot = lw.getFirstSlot();
            slot.getSamples().add(sample);
            slotsToSave.add(slot);
        }
        slotRepo.saveAll(slotsToSave);
    }

    /**
     * Adds the sample to the destination slot. Creates an operation with one action between the specified slots.
     * @param user the user responsible for the operation
     * @param opType the type of operation
     * @param sourceSample the source sample for the action
     * @param sourceSlot the source slot
     * @param destSample the sample for the action
     * @param destSlot the destination slot
     * @return the newly created operation
     */
    public Operation createOperation(User user, OperationType opType, Sample sourceSample, Slot sourceSlot,
                                     Sample destSample, Slot destSlot) {
        Action action = new Action(null, null, sourceSlot, destSlot, destSample, sourceSample);
        return opService.createOperation(opType, user, List.of(action), null);
    }
}
