package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
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
    private final LabwareRepo lwRepo;
    private final OperationCommentRepo opComRepo;
    private final OperationTypeRepo opTypeRepo;
    private final WorkService workService;
    private final OperationService opService;
    private final CommentValidationService commentValidationService;
    private final LabwareValidatorFactory lwValFactory;

    @Autowired
    public FFPEProcessingServiceImp(LabwareRepo lwRepo, OperationCommentRepo opComRepo, OperationTypeRepo opTypeRepo,
                                    WorkService workService, OperationService opService,
                                    CommentValidationService commentValidationService,
                                    LabwareValidatorFactory lwValFactory) {
        this.lwRepo = lwRepo;
        this.opComRepo = opComRepo;
        this.opTypeRepo = opTypeRepo;
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
        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }
        return record(user, labware, work, comment);
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

    /**
     * Records FFPE processing
     * @param user the user responsible
     * @param labware the labware for the operations
     * @param work the work to link to the operations
     * @param comment the comment for the operations
     * @return the labware and operations
     */
    public OperationResult record(User user, Collection<Labware> labware, Work work, Comment comment) {
        List<Operation> ops = createOps(user, labware);
        workService.link(work, ops);
        recordComments(comment, ops);
        return new OperationResult(ops, labware);
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
