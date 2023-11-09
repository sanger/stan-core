package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FFPEProcessingRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class FFPEProcessingServiceImp implements FFPEProcessingService {
    static final String MEDIUM_NAME = "Paraffin";

    private final LabwareRepo lwRepo;
    private final OperationCommentRepo opComRepo;
    private final OperationTypeRepo opTypeRepo;
    private final MediumRepo mediumRepo;
    private final TissueRepo tissueRepo;
    private final WorkService workService;
    private final OperationService opService;
    private final CommentValidationService commentValidationService;
    private final LabwareValidatorFactory lwValFactory;

    @Autowired
    public FFPEProcessingServiceImp(LabwareRepo lwRepo, OperationCommentRepo opComRepo, OperationTypeRepo opTypeRepo,
                                    MediumRepo mediumRepo, TissueRepo tissueRepo,
                                    WorkService workService, OperationService opService,
                                    CommentValidationService commentValidationService,
                                    LabwareValidatorFactory lwValFactory) {
        this.lwRepo = lwRepo;
        this.opComRepo = opComRepo;
        this.opTypeRepo = opTypeRepo;
        this.mediumRepo = mediumRepo;
        this.tissueRepo = tissueRepo;
        this.workService = workService;
        this.opService = opService;
        this.commentValidationService = commentValidationService;
        this.lwValFactory = lwValFactory;
    }

    @Override
    public OperationResult perform(User user, FFPEProcessingRequest request) throws ValidationException {
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
        return (comments.isEmpty() ? null : comments.get(0));
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
        problems.addAll(val.getErrors());
        return labware;
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
     * Records FFPE processing
     * @param user the user responsible
     * @param labware the labware for the operations
     * @param work the work to link to the operations
     * @param comment the comment for the operations
     * @param medium the medium to assign to the tissue
     * @return the labware and operations
     */
    public OperationResult record(User user, Collection<Labware> labware, Work work, Comment comment, Medium medium) {
        updateMedium(labware, medium);
        List<Operation> ops = createOps(user, labware);
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
                .collect(toList());
        if (tissuesToUpdate.isEmpty()) {
            return;
        }
        tissuesToUpdate.forEach(tis -> tis.setMedium(medium));
        tissueRepo.saveAll(new HashSet<>(tissuesToUpdate));
    }

    /**
     * Creates operations on the given labware
     * @param user user responsible
     * @param labware the labware
     * @return the operations created
     */
    public List<Operation> createOps(User user, Collection<Labware> labware) {
        OperationType opType = opTypeRepo.getByName("FFPE processing");
        return labware.stream()
                .map(lw -> opService.createOperationInPlace(opType, user, lw, null, null))
                .collect(toList());
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
}
