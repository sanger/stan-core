package uk.ac.sanger.sccp.stan.service.extract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Service for performing extraction.
 * @author dr6
 */
@Service
public class ExtractServiceImp implements ExtractService {
    private final Transactor transactor;
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final LabwareService labwareService;
    private final OperationService opService;
    private final StoreService storeService;
    private final LabwareRepo labwareRepo;
    private final LabwareTypeRepo lwTypeRepo;
    private final OperationTypeRepo opTypeRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;

    @Autowired
    public ExtractServiceImp(Transactor transactor, LabwareValidatorFactory labwareValidatorFactory,
                             LabwareService labwareService, OperationService opService,
                             StoreService storeService, LabwareRepo labwareRepo, LabwareTypeRepo lwTypeRepo, OperationTypeRepo opTypeRepo,
                             SampleRepo sampleRepo, SlotRepo slotRepo) {
        this.transactor = transactor;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.labwareService = labwareService;
        this.opService = opService;
        this.storeService = storeService;
        this.labwareRepo = labwareRepo;
        this.lwTypeRepo = lwTypeRepo;
        this.opTypeRepo = opTypeRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
    }

    @Override
    public OperationResult extractAndUnstore(User user, ExtractRequest request) {
        OperationResult result = transactExtract(user, request);
        storeService.discardStorage(user, request.getBarcodes());
        return result;
    }

    public OperationResult transactExtract(User user, ExtractRequest request) {
        return transactor.transact("Extract transaction", () -> extract(user, request));
    }

    /**
     * Performs the extraction, creating and update labware.
     * @param user the user responsible for the operation
     * @param request the specification of the extraction
     * @return the result of the extraction
     */
    public OperationResult extract(User user, ExtractRequest request) {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");
        if (request.getBarcodes()==null || request.getBarcodes().isEmpty()) {
            throw new IllegalArgumentException("No barcodes specified.");
        }
        if (request.getLabwareType()==null || request.getLabwareType().isEmpty()) {
            throw new IllegalArgumentException("No labware type specified.");
        }
        LabwareType labwareType = lwTypeRepo.getByName(request.getLabwareType());
        OperationType opType = opTypeRepo.getByName("Extract");
        BioState bioState = opType.getNewBioState();
        List<Labware> sources = loadAndValidateLabware(request.getBarcodes());
        if (opType.discardSource()) {
            sources = discardSources(sources);
        }
        Map<Labware, Labware> labwareMap = createNewLabware(labwareType, sources);
        createSamples(labwareMap, bioState);
        List<Operation> ops = createOperations(user, opType, labwareMap);
        return new OperationResult(ops, labwareMap.values());
    }

    /**
     * Loads and validates the source barcodes using {@link LabwareValidator}.
     * @param barcodes barcodes to load
     * @return the loaded labware
     * @exception IllegalArgumentException if there is a problem with the specified labware
     */
    public List<Labware> loadAndValidateLabware(Collection<String> barcodes) {
        LabwareValidator labwareValidator = labwareValidatorFactory.getValidator();
        labwareValidator.setUniqueRequired(true);
        labwareValidator.setSingleSample(true);
        List<Labware> sources = labwareValidator.loadLabware(labwareRepo, barcodes);
        labwareValidator.validateSources();
        labwareValidator.throwError(IllegalArgumentException::new);
        return sources;
    }

    /**
     * Creates new labware as destination for the extractions.
     * Returns a map of existing source labware to new destination labware.
     * @param lwType the type of the new labware
     * @param sources the source labware
     * @return a map of source to destination labware
     */
    public Map<Labware, Labware> createNewLabware(LabwareType lwType, List<Labware> sources) {
        Map<Labware, Labware> map = new HashMap<>(sources.size());
        for (Labware source : sources) {
            Labware dest = labwareService.create(lwType);
            map.put(source, dest);
        }
        return map;
    }

    /**
     * Updates the given labware as discarded (and saves them). Returns a list of updated labware.
     * @param sources the labware to update
     * @return the updated labware
     */
    public List<Labware> discardSources(List<Labware> sources) {
        return sources.stream()
                .map(lw -> {
                    lw.setDiscarded(true);
                    return labwareRepo.save(lw);
                }).collect(Collectors.toList());
    }

    /**
     * Creates samples, inserting them into the destination labware.
     * The samples will have bio state RNA. (If the source samples have that bio state, they will be reused.)
     * @param labwareMap map of source to destination labware
     * @param bioState the bio state for the new samples
     */
    public void createSamples(Map<Labware, Labware> labwareMap, BioState bioState) {
        for (var entry : labwareMap.entrySet()) {
            Slot slot = sourceSlot(entry.getKey());
            Sample oldSample = slot.getSamples().get(0);
            Sample newSample;
            if (bioState==null || oldSample.getBioState().equals(bioState)) {
                newSample = oldSample;
            } else {
                newSample = sampleRepo.save(new Sample(null, oldSample.getSection(), oldSample.getTissue(), bioState));
            }
            Labware destLw = entry.getValue();
            Slot destSlot = destLw.getFirstSlot();
            destSlot.getSamples().add(newSample);
            destSlot = slotRepo.save(destSlot);
            destLw.getSlots().set(0, destSlot);
        }
    }

    /**
     * Creates extract operations.
     * Uses {@link OperationService}.
     * @param user the user responsible for the operations
     * @param opType the type of operation
     * @param labwareMap the map of source to destination labware
     * @return a list of newly created operations.
     */
    public List<Operation> createOperations(User user, OperationType opType, Map<Labware, Labware> labwareMap) {
        List<Operation> ops = new ArrayList<>(labwareMap.size());
        for (var entry : labwareMap.entrySet()) {
            Slot src = sourceSlot(entry.getKey());
            Slot dst = entry.getValue().getFirstSlot();
            Sample srcSample = src.getSamples().get(0);
            Sample dstSample = dst.getSamples().get(0);
            Action action = new Action(null, null, src, dst, dstSample, srcSample);
            Operation op = opService.createOperation(opType, user, List.of(action), null);
            ops.add(op);
        }
        return ops;
    }

    /**
     * Gets the populated slot from the given labware
     * @param lw an item of labware
     * @return the populated slot from the given labware
     */
    private static Slot sourceSlot(Labware lw) {
        return lw.getSlots().stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .findAny()
                .orElseThrow();
    }
}
