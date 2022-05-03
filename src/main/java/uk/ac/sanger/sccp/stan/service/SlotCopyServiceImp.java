package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Service to perform an operation copying the contents of slots to new labware
 * @author dr6
 */
@Service
public class SlotCopyServiceImp implements SlotCopyService {
    private final OperationTypeRepo opTypeRepo;
    private final LabwareTypeRepo lwTypeRepo;
    private final LabwareRepo lwRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final LabwareService lwService;
    private final OperationService opService;
    private final StoreService storeService;
    private final WorkService workService;
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final EntityManager entityManager;
    private final Transactor transactor;

    @Autowired
    public SlotCopyServiceImp(OperationTypeRepo opTypeRepo, LabwareTypeRepo lwTypeRepo, LabwareRepo lwRepo,
                              SampleRepo sampleRepo, SlotRepo slotRepo,
                              LabwareService lwService, OperationService opService, StoreService storeService,
                              WorkService workService,
                              LabwareValidatorFactory labwareValidatorFactory, EntityManager entityManager,
                              Transactor transactor) {
        this.opTypeRepo = opTypeRepo;
        this.lwTypeRepo = lwTypeRepo;
        this.lwRepo = lwRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.lwService = lwService;
        this.opService = opService;
        this.storeService = storeService;
        this.workService = workService;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.entityManager = entityManager;
        this.transactor = transactor;
    }

    @Override
    public OperationResult perform(User user, SlotCopyRequest request) {
        OperationResult result = transactor.transact("SlotCopy", () -> performInsideTransaction(user, request));
        if (result.getOperations().size() > 0 && result.getOperations().get(0).getOperationType().discardSource()) {
            unstoreSources(user, request);
        }
        return result;
    }

    public OperationResult performInsideTransaction(User user, SlotCopyRequest request) {
        Set<String> problems = new LinkedHashSet<>();
        OperationType opType = loadEntity(problems, request.getOperationType(), "operation type", opTypeRepo::findByName);
        LabwareType lwType = loadEntity(problems, request.getLabwareType(), "labware type", lwTypeRepo::findByName);
        UCMap<Labware> labwareMap = loadLabware(problems, request.getContents());
        validateLabware(problems, labwareMap.values());
        validateContents(problems, lwType, labwareMap, request.getContents());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        if (!problems.isEmpty()) {
            throw new ValidationException("The operation could not be validated.", problems);
        }
        return execute(user, request.getContents(), opType, lwType, labwareMap, work);
    }

    public void unstoreSources(User user, SlotCopyRequest request) {
        Set<String> sourceBarcodes = request.getContents().stream()
                .map(c -> c.getSourceBarcode().toUpperCase())
                .collect(toSet());
        storeService.discardStorage(user, sourceBarcodes);
    }

    // region Loading and validating

    /**
     * Helper to load an entity using a function that returns an optional.
     * If it cannot be loaded, a problem is added to {@code problems}, and this method returns null
     * @param problems the receptacle for problems
     * @param name the name of the thing to load
     * @param desc the name of the type of thing
     * @param loadFn the function that returns an {@code Optional<E>}
     * @param <E> the type of thing being loaded
     * @return the thing loaded, or null if it could not be loaded
     */
    public <E> E loadEntity(Collection<String> problems, String name, String desc, Function<String, Optional<E>> loadFn) {
        if (name==null || name.isEmpty()) {
            problems.add("No "+desc+" specified.");
            return null;
        }
        var opt = loadFn.apply(name);
        if (opt.isEmpty()) {
            problems.add("Unknown "+desc+": "+repr(name));
            return null;
        }
        return opt.get();
    }

    /**
     * Loads the specified source labware into a {@code UCMap} from labware barcode.
     * @param problems collection to receive any problems
     * @param contents the description of what is being copied where
     * @return a {@code UCMap} of labware from its barcode
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, Collection<SlotCopyContent> contents) {
        Set<String> lwBarcodes = new HashSet<>();
        for (SlotCopyContent content : contents) {
            String barcode = content.getSourceBarcode();
            if (barcode==null || barcode.isEmpty()) {
                problems.add("Missing source barcode.");
                continue;
            }
            lwBarcodes.add(barcode.toUpperCase());
        }
        if (lwBarcodes.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<Labware> lwMap = UCMap.from(lwRepo.findByBarcodeIn(lwBarcodes), Labware::getBarcode);
        List<String> unknownBarcodes = lwBarcodes.stream()
                .filter(bc -> lwMap.get(bc)==null)
                .collect(toList());
        if (!unknownBarcodes.isEmpty()) {
            problems.add(pluralise("Unknown source barcode{s}: ", unknownBarcodes.size())
                    + reprCollection(unknownBarcodes));
        }
        return lwMap;
    }

    /**
     * Validates that the source labware is usable using {@link LabwareValidator}.
     * @param problems the receptacle for problems
     * @param labware the source labware being validated
     */
    public void validateLabware(Collection<String> problems, Collection<Labware> labware) {
        LabwareValidator validator = labwareValidatorFactory.getValidator(labware);
        validator.setUniqueRequired(false);
        validator.setSingleSample(false);
        validator.validateSources();
        if (!validator.getErrors().isEmpty()) {
            problems.addAll(validator.getErrors());
        }
    }

    /**
     * Validates that the instructions of what to copy are valid for the source labware and new labware type
     * @param problems the receptacle for problems
     * @param destLwType the type of the destination labware (may be null if a valid labware type was not specified)
     * @param lwMap the map of source barcode to labware (some labware may be missing if there were invalid/missing barcodes)
     * @param contents the specification mapping locations
     */
    public void validateContents(Collection<String> problems, LabwareType destLwType, UCMap<Labware> lwMap,
                                 Collection<SlotCopyContent> contents) {
        if (contents.isEmpty()) {
            problems.add("No contents specified.");
            return;
        }
        Set<Address> destAddressSet = new HashSet<>();
        for (var content : contents) {
            Address destAddress = content.getDestinationAddress();
            if (destAddress==null) {
                problems.add("No destination address specified.");
            } else if (!destAddressSet.add(destAddress)) {
                problems.add("Repeated destination address: "+destAddress);
            } else if (destLwType!=null && destLwType.indexOf(destAddress) < 0) {
                problems.add("Invalid address "+destAddress+" for labware type "+destLwType.getName()+".");
            }

            Address sourceAddress = content.getSourceAddress();
            Labware lw = lwMap.get(content.getSourceBarcode());
            if (sourceAddress==null) {
                problems.add("No source address specified.");
            } else if (lw!=null && lw.getLabwareType().indexOf(sourceAddress) < 0) {
                problems.add("Invalid address "+sourceAddress+" for source labware "+lw.getBarcode());
            } else if (lw!=null && lw.getSlot(sourceAddress).getSamples().isEmpty()) {
                problems.add(String.format("Slot %s in labware %s is empty.", sourceAddress, lw.getBarcode()));
            }
        }
    }
    // endregion

    // region Executing

    /**
     * Executes the request. Creates the new labware, records the operation, populates the labware,
     * creates new samples if necessary, discards sources if necessary.
     * This is called after successful validation, so it is expected to succeed.
     * @param user the user responsible for the operation
     * @param contents the description of what locations are copied to where
     * @param opType the type of operation being performed
     * @param lwType the type of labware to create
     * @param labwareMap a map of the source labware from their barcodes
     * @param work the work (optional)
     * @return the result of the operation
     */
    public OperationResult execute(User user, Collection<SlotCopyContent> contents,
                                   OperationType opType, LabwareType lwType, UCMap<Labware> labwareMap,
                                   Work work) {
        Labware emptyLabware = lwService.create(lwType);
        Map<Integer, Sample> oldSampleIdToNewSample = createSamples(contents, labwareMap, opType.getNewBioState());
        Labware filledLabware = fillLabware(emptyLabware, contents, labwareMap, oldSampleIdToNewSample);
        if (opType.discardSource()) {
            discardSources(labwareMap.values());
        }
        if (opType.markSourceUsed()) {
            markSourcesUsed(labwareMap.values());
        }
        Operation op = createOperation(user, contents, opType, labwareMap, filledLabware, oldSampleIdToNewSample);
        List<Operation> ops = List.of(op);
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, List.of(filledLabware));
    }

    /**
     * Creates new samples as required for the operation.
     * Links each existing sample (via its id) to the sample that will represent it in the new labware.
     * If a bio state change is required, then the destination samples will be newly created.
     * Otherwise, the destination samples will be the source samples.
     * In either case, they will be entered into the map.
     * @param contents the description of what locations are copied to where
     * @param labwareMap the source labware, mapped from its barcode
     * @param newBioState the new bio state, or null if no new bio state is specified
     * @return a map of source sample id to destination sample (which may or may not be new)
     */
    public Map<Integer, Sample> createSamples(Collection<SlotCopyContent> contents, UCMap<Labware> labwareMap,
                                              BioState newBioState) {
        Map<Integer, Sample> map = new HashMap<>();
        for (var content: contents) {
            Labware lw = labwareMap.get(content.getSourceBarcode());
            Slot slot = lw.getSlot(content.getSourceAddress());
            for (Sample sample : slot.getSamples()) {
                Integer sampleId = sample.getId();
                if (map.get(sampleId)==null) {
                    if (newBioState==null || newBioState.equals(sample.getBioState())) {
                        map.put(sampleId, sample);
                    } else {
                        Sample newSample = sampleRepo.save(new Sample(null, sample.getSection(), sample.getTissue(), newBioState));
                        map.put(sampleId, newSample);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Puts samples into the new empty labware
     * @param destLabware the new empty labware
     * @param contents the description of what locations are copied to where
     * @param labwareMap the source labware, mapped from its barcode
     * @param oldSampleIdToNewSample the map of source sample id to destination sample
     * @return the populated labware
     */
    public Labware fillLabware(Labware destLabware, Collection<SlotCopyContent> contents, UCMap<Labware> labwareMap,
                               Map<Integer, Sample> oldSampleIdToNewSample) {
        for (var content: contents) {
            Slot src = labwareMap.get(content.getSourceBarcode()).getSlot(content.getSourceAddress());
            Slot dst = destLabware.getSlot(content.getDestinationAddress());
            for (Sample oldSample : src.getSamples()) {
                Sample newSample = oldSampleIdToNewSample.get(oldSample.getId());
                if (!dst.getSamples().contains(newSample)) {
                    dst.getSamples().add(newSample);
                }
            }
        }
        List<Slot> slotsToSave = destLabware.getSlots().stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .collect(toList());
        slotRepo.saveAll(slotsToSave);
        entityManager.refresh(destLabware);
        return destLabware;
    }

    /**
     * Records the operation as requested
     * @param user the user responsible for the operation
     * @param contents the description of what locations are copied to where
     * @param opType the type of operation to record
     * @param labwareMap the source labware, mapped from its barcode
     * @param destLabware the new labware (containing the destination samples)
     * @param oldSampleIdToNewSample the map of source sample id to destination sample
     * @return the new operation
     */
    public Operation createOperation(User user, Collection<SlotCopyContent> contents, OperationType opType,
                                     UCMap<Labware> labwareMap, Labware destLabware, Map<Integer, Sample> oldSampleIdToNewSample) {
        List<Action> actions = new ArrayList<>();
        for (var content: contents) {
            Labware sourceLw = labwareMap.get(content.getSourceBarcode());
            Slot src = sourceLw.getSlot(content.getSourceAddress());
            Slot dest = destLabware.getSlot(content.getDestinationAddress());
            for (Sample sourceSample: src.getSamples()) {
                Sample destSample = oldSampleIdToNewSample.get(sourceSample.getId());
                Action action = new Action(null, null, src, dest, destSample, sourceSample);
                actions.add(action);
            }
        }
        return opService.createOperation(opType, user, actions, null);
    }

    /**
     * Discards (and saves) the given labware.
     * @param labware the labware to discard
     */
    public void discardSources(Collection<Labware> labware) {
        for (Labware lw : labware) {
            lw.setDiscarded(true);
        }
        lwRepo.saveAll(labware);
    }

    public void markSourcesUsed(Collection<Labware> labware) {
        for (Labware lw : labware) {
            lw.setUsed(true);
        }
        lwRepo.saveAll(labware);
    }
    // endregion
}
