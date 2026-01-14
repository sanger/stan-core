package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ParaffinProcessingRequest;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class ParaffinProcessingServiceImp implements ParaffinProcessingService {
    static final String MEDIUM_NAME = "Paraffin";

    private final LabwareRepo lwRepo;
    private final OperationCommentRepo opComRepo;
    private final OperationTypeRepo opTypeRepo;
    private final MediumRepo mediumRepo;
    private final TissueRepo tissueRepo;
    private final SlotRepo slotRepo;
    private final SampleRepo sampleRepo;
    private final WorkService workService;
    private final OperationService opService;
    private final CommentValidationService commentValidationService;
    private final LabwareValidatorFactory lwValFactory;

    @Autowired
    public ParaffinProcessingServiceImp(LabwareRepo lwRepo, OperationCommentRepo opComRepo, OperationTypeRepo opTypeRepo,
                                        MediumRepo mediumRepo, TissueRepo tissueRepo, SlotRepo slotRepo, SampleRepo sampleRepo,
                                        WorkService workService, OperationService opService,
                                        CommentValidationService commentValidationService,
                                        LabwareValidatorFactory lwValFactory) {
        this.lwRepo = lwRepo;
        this.opComRepo = opComRepo;
        this.opTypeRepo = opTypeRepo;
        this.mediumRepo = mediumRepo;
        this.tissueRepo = tissueRepo;
        this.slotRepo = slotRepo;
        this.sampleRepo = sampleRepo;
        this.workService = workService;
        this.opService = opService;
        this.commentValidationService = commentValidationService;
        this.lwValFactory = lwValFactory;
    }

    @Override
    public OperationResult perform(User user, ParaffinProcessingRequest request) throws ValidationException {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");
        Collection<String> problems = new LinkedHashSet<>();
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        Comment comment = loadComment(problems, request.getCommentId());
        List<Labware> labware = loadLabware(problems, request.getBarcodes());
        Medium medium = loadMedium(problems, MEDIUM_NAME);
        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }
        return record(user, labware, work, comment, medium);
    }

    /**
     * Loads the comment from its id.
     * @param problems receptacle for problems
     * @param commentId the id of the comment
     * @return the comment, or null if none could be loaded
     */
    public Comment loadComment(Collection<String> problems, Integer commentId) {
        if (commentId==null) {
            problems.add("No comment ID specified.");
            return null;
        }
        var comments = commentValidationService.validateCommentIds(problems, Stream.of(commentId));
        return (comments.isEmpty() ? null : comments.getFirst());
    }

    /**
     * Loads the labware from its barcodes
     * @param problems receptacle for problems
     * @param barcodes the labware barcodes
     * @return the labware loaded
     */
    public List<Labware> loadLabware(Collection<String> problems, Collection<String> barcodes) {
        if (barcodes==null || barcodes.isEmpty()) {
            problems.add("No labware barcodes specified.");
            return List.of();
        }
        LabwareValidator val = lwValFactory.getValidator();
        List<Labware> labware = val.loadLabware(lwRepo, barcodes);
        val.validateSources();
        final Collection<String> valErrors = val.getErrors();
        problems.addAll(valErrors);
        if (valErrors.isEmpty()) {
            checkLabwareIsBlockish(problems, labware);
        }
        return labware;
    }

    /**
     * Check: labware must have one sample, which must be in the first slot only,
     * and must not have a section number.
     * @param problems receptacle for problems
     * @param labware the labware to check
     */
    public void checkLabwareIsBlockish(Collection<String> problems, Collection<Labware> labware) {
        Set<String> badBarcodes = new LinkedHashSet<>();
        final Address A1 = new Address(1,1);
        for (Labware lw : labware) {
            for (Slot slot : lw.getSlots()) {
                if (slot.getAddress().equals(A1)) {
                    if (slot.getSamples().size()!=1 || slot.getSamples().getFirst().getSection()!=null) {
                        badBarcodes.add(lw.getBarcode());
                    }
                } else {
                    if (!slot.getSamples().isEmpty()) {
                        badBarcodes.add(lw.getBarcode());
                    }
                }
            }
        }
        if (!badBarcodes.isEmpty()) {
            problems.add("Labware must contain one unsectioned sample, and it must be in the first slot. " +
                    "The following labware cannot be used in this operation: "+badBarcodes);
        }
    }

    public Medium loadMedium(Collection<String> problems, String mediumName) {
        var opt = mediumRepo.findByName(mediumName);
        if (opt.isPresent()) {
            return opt.get();
        }
        problems.add("Medium \""+mediumName+"\" not found in database.");
        return null;
    }

    /**
     * Records paraffin processing
     * @param user the user responsible
     * @param labware the labware for the operations
     * @param work the work to link to the operations
     * @param comment the comment for the operations
     * @param medium the medium to assign to the tissue
     * @return the labware and operations
     */
    public OperationResult record(User user, Collection<Labware> labware, Work work, Comment comment, Medium medium) {
        updateMedium(labware, medium);
        List<BlockChange> changes = createBlocks(labware);
        List<Operation> ops = createOps(user, changes);
        workService.link(work, ops);
        recordComments(comment, ops);
        return new OperationResult(ops, labware);
    }

    /**
     * Updates all the tissues of the samples in the labware to have the given medium
     * @param labware the labware involved
     * @param medium the required medium
     */
    public void updateMedium(Collection<Labware> labware, Medium medium) {
        List<Tissue> tissuesToUpdate = labware.stream()
                .flatMap(lw -> lw.getSlots().stream())
                .flatMap(slot -> slot.getSamples().stream())
                .map(Sample::getTissue)
                .filter(tis -> !medium.equals(tis.getMedium()))
                .toList();
        if (tissuesToUpdate.isEmpty()) {
            return;
        }
        tissuesToUpdate.forEach(tis -> tis.setMedium(medium));
        tissueRepo.saveAll(new HashSet<>(tissuesToUpdate));
    }

    /**
     * Converts labware to blocks, if they aren't already
     * @param labware the labware to make into blocks, if they aren't already
     */
    List<BlockChange> createBlocks(Collection<Labware> labware) {
        List<Sample> samplesToSave = new ArrayList<>();
        List<Slot> slotsToSave = new ArrayList<>();
        List<BlockChange> changes = new ArrayList<>(labware.size());
        for (Labware lw : labware) {
            Map<Sample, Sample> newSampleMap = new HashMap<>();
            for (Slot slot : lw.getSlots()) {
                if (slot.getSamples().isEmpty()) {
                    continue;
                }
                boolean changed = false;
                List<Sample> newSamples = new ArrayList<>(slot.getSamples().size());
                for (Sample sam : slot.getSamples()) {
                    Sample blockSample;
                    if (!sam.isBlock()) {
                        blockSample = newSampleMap.get(sam);
                        if (blockSample==null) {
                            blockSample = Sample.newBlock(null, sam.getTissue(), sam.getBioState(), 0);
                            newSampleMap.put(sam, blockSample);
                        }
                        changed = true;
                    } else {
                        blockSample = sam;
                    }
                    newSamples.add(blockSample);
                    changes.add(new BlockChange(slot, sam, blockSample));
                }
                if (changed) {
                    slot.setSamples(newSamples);
                    slotsToSave.add(slot);
                }
            }
            samplesToSave.addAll(newSampleMap.values());
        }
        if (!samplesToSave.isEmpty()) {
            // This should update the sample objects with ids
            sampleRepo.saveAll(samplesToSave);
        }
        if (!slotsToSave.isEmpty()) {
            slotRepo.saveAll(slotsToSave);
        }
        return changes;
    }

    /**
     * Creates operations
     * @param user user responsible
     * @param changes the slots and affected samples
     * @return the operations created
     */
    List<Operation> createOps(User user, Collection<BlockChange> changes) {
        OperationType opType = opTypeRepo.getByName("Paraffin processing");
        Map<Integer, List<BlockChange>> lwChanges = new LinkedHashMap<>();
        for (BlockChange change : changes) {
            Integer lwId = change.slot.getLabwareId();
            lwChanges.computeIfAbsent(lwId, id -> new ArrayList<>()).add(change);
        }
        return lwChanges.values().stream()
                .map(cs -> createOp(user, opType, cs))
                .toList();
    }

    /**
     * Creates an operation describing the given changes
     * @param user the user responsible
     * @param opType the operation type
     * @param changes slots and samples
     * @return the created operation
     */
    Operation createOp(User user, OperationType opType, Collection<BlockChange> changes) {
        List<Action> actions = changes.stream()
                .map(c -> new Action(null, null, c.slot, c.slot, c.blockSample, c.originalSample))
                .toList();
        return opService.createOperation(opType, user, actions, null);
    }

    /**
     * Records the given comment against the given operations.
     * @param comment the comment to record
     * @param ops the operations
     */
    public void recordComments(Comment comment, Collection<Operation> ops) {
        List<OperationComment> opComs = ops.stream()
                .flatMap(op -> op.getActions().stream())
                .map(ac -> new OperationComment(null, comment, ac.getOperationId(), ac.getSample().getId(), ac.getDestination().getId(), null))
                .collect(toList());
        opComRepo.saveAll(opComs);
    }

    /** A slot and the samples involved in its action */
    record BlockChange(Slot slot, Sample originalSample, Sample blockSample) {}
}
