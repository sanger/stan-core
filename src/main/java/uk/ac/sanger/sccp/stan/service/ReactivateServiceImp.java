package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReactivateLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class ReactivateServiceImp implements ReactivateService {

    private final LabwareValidatorFactory lwValFactory;
    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationCommentRepo opComRepo;
    private final CommentValidationService commentValidationService;
    private final WorkService workService;
    private final OperationService opService;

    @Autowired
    public ReactivateServiceImp(LabwareValidatorFactory lwValFactory,
                                LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationCommentRepo opComRepo,
                                CommentValidationService commentValidationService, WorkService workService,
                                OperationService opService) {
        this.lwValFactory = lwValFactory;
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.opComRepo = opComRepo;
        this.commentValidationService = commentValidationService;
        this.workService = workService;
        this.opService = opService;
    }

    @Override
    public OperationResult reactivate(User user, List<ReactivateLabware> items) throws ValidationException {
        final Collection<String> problems = new LinkedHashSet<>();

        if (user==null) {
            problems.add("No user specified.");
        }
        if (nullOrEmpty(items)) {
            problems.add("No labware specified.");
            throw new ValidationException(problems);
        }

        UCMap<Labware> lwMap = loadLabware(problems, items);
        UCMap<Work> workMap = loadWork(problems, items);
        Map<Integer, Comment> commentMap = loadComments(problems, items);
        OperationType opType = loadOpType(problems);

        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return record(opType, user, items, lwMap, workMap, commentMap);
    }

    /**
     * Loads the indicated labware and checks it is suitable for reactivation
     * @param problems receptacle for problems
     * @param items the requested reactivations
     * @return the indicated labware, mapped from its barcode
     */
    UCMap<Labware> loadLabware(Collection<String> problems, List<ReactivateLabware> items) {
        LabwareValidator val = lwValFactory.getValidator();
        List<String> barcodes = items.stream()
                .map(ReactivateLabware::getBarcode)
                .collect(toList());
        val.loadLabware(lwRepo, barcodes);
        val.validateUnique();
        val.validateNonEmpty();
        problems.addAll(val.getErrors());
        Collection<Labware> lwList = val.getLabware();
        List<String> unsuitable = lwList.stream()
                .filter(lw -> !(lw.isDestroyed() || lw.isDiscarded() || lw.isUsed()))
                .map(Labware::getBarcode)
                .toList();
        if (!unsuitable.isEmpty()) {
            problems.add("Labware is not discarded, destroyed or used: "+unsuitable);
        }
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Loads and checks the indicated works
     * @param problems receptacle for problems
     * @param items the requested reactivations
     * @return the indicated works, mapped from work numbers
     */
    UCMap<Work> loadWork(Collection<String> problems, List<ReactivateLabware> items) {
        List<String> workNumbers = items.stream()
                .map(ReactivateLabware::getWorkNumber)
                .collect(toList());
        return workService.validateUsableWorks(problems, workNumbers);
    }

    /**
     * Loads and checks the indicated comments
     * @param problems receptacle for problems
     * @param items the requested reactivations
     * @return the indicated comments, mapped from ids
     */
    Map<Integer, Comment> loadComments(Collection<String> problems, List<ReactivateLabware> items) {
        var commentIdStream = items.stream().map(ReactivateLabware::getCommentId);
        var comments = commentValidationService.validateCommentIds(problems, commentIdStream);
        return comments.stream().collect(inMap(Comment::getId));
    }

    /**
     * Loads the reactivate operation type
     * @param problems receptacle for problems
     * @return the required operation type
     */
    OperationType loadOpType(Collection<String> problems) {
        var optOpType = opTypeRepo.findByName("Reactivate");
        if (optOpType.isPresent()) {
            return optOpType.get();
        }
        problems.add("Operation type \"Reactivate\" not found.");
        return null;
    }

    /**
     * Records the reactivations, reactivates the labware
     * @param opType the operation type to record
     * @param user the responsible user
     * @param items the requested reactivations
     * @param lwMap the specified labware
     * @param workMap the specified work
     * @param commentMap the specified comments
     * @return the operations and reactivated labware
     */
    OperationResult record(OperationType opType, User user, List<ReactivateLabware> items, UCMap<Labware> lwMap,
                           UCMap<Work> workMap, Map<Integer, Comment> commentMap) {
        updateLabware(lwMap.values());
        List<WorkService.WorkOp> workOps = new ArrayList<>(items.size());
        List<Operation> ops = new ArrayList<>(items.size());
        for (ReactivateLabware rl : items) {
            Work work = workMap.get(rl.getWorkNumber());
            Labware labware = lwMap.get(rl.getBarcode());
            Operation op = opService.createOperationInPlace(opType, user, labware, null, null);
            recordComment(op, commentMap.get(rl.getCommentId()));
            ops.add(op);
            workOps.add(new WorkService.WorkOp(work, op));
        }
        List<Labware> lws = items.stream()
                .map(rl -> lwMap.get(rl.getBarcode()))
                .collect(toList());
        workService.linkWorkOps(workOps.stream());
        return new OperationResult(ops, lws);
    }

    /**
     * Marks labware as not destroyed, discarded or used
     * @param labware the labware to update
     */
    void updateLabware(Collection<Labware> labware) {
        for (Labware lw : labware) {
            lw.setDiscarded(false);
            lw.setDestroyed(false);
            lw.setUsed(false);
        }
        lwRepo.saveAll(labware);
    }

    /**
     * Links the given comment to the information in the given operation
     * @param op the operation
     * @param comment the comment
     */
    void recordComment(Operation op, Comment comment) {
        final Integer opId = op.getId();
        List<OperationComment> opcoms = op.getActions().stream()
                .map(ac -> new OperationComment(null, comment, opId, ac.getSample().getId(),
                        ac.getDestination().getId(), null))
                .collect(toList());
        opComRepo.saveAll(opcoms);
    }
}
