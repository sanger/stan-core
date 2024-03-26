package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.*;
import uk.ac.sanger.sccp.stan.service.SlotCopyValidationService.Data;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Service to perform an operation copying the contents of slots to new labware
 * @author dr6
 */
@Service
public class SlotCopyServiceImp implements SlotCopyService {
    static final String CYTASSIST_OP = "CytAssist";
    static final String CYTASSIST_SLIDE = "Visium LP CytAssist", CYTASSIST_SLIDE_XL = "Visium LP CytAssist XL",
            CYTASSIST_SLIDE_HD = "Visium LP CytAssist HD";

    static final String BS_PROBES = "Probes", BS_CDNA = "cDNA", BS_LIBRARY = "Library",
            BS_LIB_PRE_CLEAN = "Library pre-clean", BS_LIB_POST_CLEAN = "Library post-clean",
            BS_PROBES_PRE_CLEAN = "Probes pre-clean", BS_PROBES_POST_CLEAN = "Probes post-clean";

    static final Set<String> VALID_BS_UPPER = Stream.of(
                    BS_PROBES, BS_CDNA, BS_LIBRARY, BS_LIB_PRE_CLEAN, BS_LIB_POST_CLEAN,
                    BS_PROBES_PRE_CLEAN, BS_PROBES_POST_CLEAN
            ).map(String::toUpperCase)
            .collect(toSet());

    private final LabwareRepo lwRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final LabwareNoteRepo lwNoteRepo;
    private final SlotCopyValidationService valService;
    private final LabwareService lwService;
    private final OperationService opService;
    private final StoreService storeService;
    private final WorkService workService;
    private final EntityManager entityManager;
    private final Transactor transactor;

    @Autowired
    public SlotCopyServiceImp(LabwareRepo lwRepo, SampleRepo sampleRepo, SlotRepo slotRepo, LabwareNoteRepo lwNoteRepo,
                              SlotCopyValidationService valService, LabwareService lwService,
                              OperationService opService, StoreService storeService, WorkService workService,
                              EntityManager entityManager, Transactor transactor) {
        this.lwRepo = lwRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.valService = valService;
        this.lwService = lwService;
        this.opService = opService;
        this.storeService = storeService;
        this.workService = workService;
        this.entityManager = entityManager;
        this.transactor = transactor;
    }

    @Override
    public OperationResult perform(User user, SlotCopyRequest request) throws ValidationException {
        Set<String> barcodesToUnstore = new HashSet<>();
        OperationResult result = transactor.transact("SlotCopy",
                () -> performInsideTransaction(user, request, barcodesToUnstore));
        if (!result.getOperations().isEmpty() && !barcodesToUnstore.isEmpty()) {
            storeService.discardStorage(user, barcodesToUnstore);
        }
        return result;
    }

    /**
     * This method is called inside a transaction to validate and execute the given request.
     * @param user the user responsible for the request
     * @param request the request to perform
     * @param barcodesToUnstore receptacle for labware barcodes that need to be removed from storage
     * @return the labware and operations created
     * @exception ValidationException validation fails
     */
    public OperationResult performInsideTransaction(User user, SlotCopyRequest request, final Set<String> barcodesToUnstore)
            throws ValidationException {
        Data data = valService.validateRequest(user, request);
        if (!data.problems.isEmpty()) {
            throw new ValidationException("The operation could not be validated.", data.problems);
        }
        return record(user, data, barcodesToUnstore);
    }

    @Override
    public OperationResult record(User user, Data data, final Set<String> barcodesToUnstore) {
        OperationResult opres = executeOps(user, data.request.getDestinations(), data.opType, data.lwTypes, data.bioStates,
                data.sourceLabware, data.work, data.destLabware);
        final Labware.State newSourceState = (data.opType.discardSource() ? Labware.State.discarded
                : data.opType.markSourceUsed() ? Labware.State.used : null);
        updateSources(data.request.getSources(), data.sourceLabware.values(), newSourceState, barcodesToUnstore);
        return opres;
    }
    // region Executing

    public OperationResult executeOps(User user, Collection<SlotCopyDestination> dests,
                                      OperationType opType, UCMap<LabwareType> lwTypes, UCMap<BioState> bioStates,
                                      UCMap<Labware> sources, Work work, UCMap<Labware> existingDests) {
        List<Operation> ops = new ArrayList<>(dests.size());
        List<Labware> destLabware = new ArrayList<>(dests.size());
        for (SlotCopyDestination dest : dests) {
            OperationResult opres = executeOp(user, dest.getContents(), opType, lwTypes.get(dest.getLabwareType()),
                    dest.getPreBarcode(), sources, dest.getCosting(), dest.getLotNumber(), dest.getProbeLotNumber(),
                    bioStates.get(dest.getBioState()), existingDests.get(dest.getBarcode()));
            ops.addAll(opres.getOperations());
            destLabware.addAll(opres.getLabware());
        }
        if (work != null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, destLabware);
    }

    /**
     * Executes the request. Creates the new labware, records the operation, populates the labware,
     * creates new samples if necessary, discards sources if necessary.
     * This is called after successful validation, so it is expected to succeed.
     * @param user the user responsible for the operation
     * @param contents the description of what locations are copied to where
     * @param opType the type of operation being performed
     * @param lwType the type of labware to create
     * @param preBarcode the prebarcode of the new labware, if it has one
     * @param labwareMap a map of the source labware from their barcodes
     * @param costing the costing of new labware, if specified
     * @param lotNumber the lot number of the new labware, if specified
     * @param probeLotNumber the transcriptome probe lot number, if specified
     * @param bioState the new bio state of the labware, if given
     * @param destLw existing destination labware, if applicable
     * @return the result of the operation
     */
    public OperationResult executeOp(User user, Collection<SlotCopyContent> contents,
                                     OperationType opType, LabwareType lwType, String preBarcode,
                                     UCMap<Labware> labwareMap, SlideCosting costing, String lotNumber,
                                     String probeLotNumber, BioState bioState, Labware destLw) {
        if (destLw==null) {
            destLw = lwService.create(lwType, preBarcode, preBarcode);
        } else if (bioState==null) {
            bioState = findBioStateInLabware(destLw);
        }
        Map<Integer, Sample> oldSampleIdToNewSample = createSamples(contents, labwareMap,
                coalesce(bioState, opType.getNewBioState()));
        Labware filledLabware = fillLabware(destLw, contents, labwareMap, oldSampleIdToNewSample);
        Operation op = createOperation(user, contents, opType, labwareMap, filledLabware, oldSampleIdToNewSample);
        if (costing != null) {
            lwNoteRepo.save(new LabwareNote(null, filledLabware.getId(), op.getId(), "costing", costing.name()));
        }
        if (!nullOrEmpty(lotNumber)) {
            lwNoteRepo.save(new LabwareNote(null, filledLabware.getId(), op.getId(), "lot", lotNumber.toUpperCase()));
        }
        if (!nullOrEmpty(probeLotNumber)) {
            lwNoteRepo.save(new LabwareNote(null, filledLabware.getId(), op.getId(), "probe lot", probeLotNumber.toUpperCase()));
        }
        return new OperationResult(List.of(op), List.of(filledLabware));
    }

    /**
     * Gets the bio state for samples in the given labware, if there are any and they are all the same bio state.
     * Otherwise, returns null.
     * @param destLw the labware to get the bio state from
     * @return the identified bio state, or null
     */
    public BioState findBioStateInLabware(Labware destLw) {
        BioState found = null;
        for (Slot slot : destLw.getSlots()) {
            for (Sample sample : slot.getSamples()) {
                if (found==null) {
                    found = sample.getBioState();
                } else if (!found.equals(sample.getBioState())) {
                    return null;
                }
            }
        }
        return found;
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
     * Puts samples into the destination labware
     * @param destLabware the destination labware
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
                    dst.addSample(newSample);
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
     * Update the lw state of source labware.
     * @param scss links of source barcode to explicit states
     * @param sources source labware
     * @param defaultState default new state (if any)
     * @param barcodesToUnstore receptacle to put barcodes that need to be unstored
     */
    public void updateSources(List<SlotCopySource> scss, Collection<Labware> sources,
                              Labware.State defaultState, final Set<String> barcodesToUnstore) {
        UCMap<Labware.State> stateMap = new UCMap<>(scss.size());
        scss.forEach(scs -> stateMap.put(scs.getBarcode(), scs.getLabwareState()));
        final List<Labware> toUpdate = new ArrayList<>(sources.size());
        for (Labware lw : sources) {
            Labware.State newState = stateMap.getOrDefault(lw.getBarcode(), defaultState);
            if (newState==Labware.State.discarded && !lw.isDiscarded()) {
                lw.setDiscarded(true);
                barcodesToUnstore.add(lw.getBarcode());
                toUpdate.add(lw);
            } else if (newState==Labware.State.used && !lw.isUsed()) {
                lw.setUsed(true);
                toUpdate.add(lw);
            }
        }
        if (!toUpdate.isEmpty()) {
            lwRepo.saveAll(toUpdate);
        }
    }
    // endregion
}
