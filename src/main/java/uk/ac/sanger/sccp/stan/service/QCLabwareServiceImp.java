package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.*;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class QCLabwareServiceImp implements QCLabwareService {
    private final Clock clock;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final OperationRepo opRepo;
    private final OperationCommentRepo opComRepo;
    private final LabwareValidatorFactory lwValFactory;
    private final WorkService workService;
    private final OperationService opService;
    private final CommentValidationService commentValidationService;

    @Autowired
    public QCLabwareServiceImp(Clock clock,
                               OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, OperationRepo opRepo,
                               OperationCommentRepo opComRepo,
                               LabwareValidatorFactory lwValFactory,
                               WorkService workService, OperationService opService,
                               CommentValidationService commentValidationService) {
        this.clock = clock;
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.opRepo = opRepo;
        this.opComRepo = opComRepo;
        this.lwValFactory = lwValFactory;
        this.workService = workService;
        this.opService = opService;
        this.commentValidationService = commentValidationService;
    }


    @Override
    public OperationResult perform(User user, QCLabwareRequest request) throws ValidationException {
        final Collection<String> problems = new LinkedHashSet<>();
        OperationType opType = checkOpType(problems, request.getOperationType());
        List<QCLabware> qcls = request.getLabware();
        if (nullOrEmpty(qcls)) {
            problems.add("No labware specified.");
            throw new ValidationException(problems);
        }
        UCMap<Labware> lwMap = checkLabware(problems, qcls);
        UCMap<Work> workMap = checkWork(problems, qcls);
        checkTimestamps(problems, qcls, lwMap, clock);
        Map<Integer, Comment> commentMap = checkComments(problems, qcls);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return record(user, opType, qcls, lwMap, workMap, commentMap);
    }

    /**
     * Checks and returns the specified operation type
     * @param problems receptacle for problems
     * @param opName the name of the op type
     * @return the found op type, if any
     */
    public OperationType checkOpType(Collection<String> problems, String opName) {
        if (nullOrEmpty(opName)) {
            problems.add("No operation type specified.");
            return null;
        }
        OperationType opType = opTypeRepo.findByName(opName).orElse(null);
        if (opType==null) {
            problems.add("Unknown operation type: "+repr(opName));
            return null;
        }
        if (!opType.inPlace()) {
            problems.add("Operation type "+opType.getName()+" cannot be used in this request.");
        }
        return opType;
    }

    /**
     * Loads and checks labware in the request
     * @param problems receptacle for problems
     * @param qcls request
     * @return map of labware from barcode
     */
    public UCMap<Labware> checkLabware(Collection<String> problems, List<QCLabware> qcls) {
        List<String> barcodes = qcls.stream()
                .map(QCLabware::getBarcode)
                .filter(Objects::nonNull)
                .collect(toList());
        if (barcodes.size() < qcls.size()) {
            problems.add("Missing labware barcode.");
        }
        if (barcodes.isEmpty()) {
            return new UCMap<>(0);
        }
        LabwareValidator val = lwValFactory.getValidator();
        val.loadLabware(lwRepo, barcodes);
        val.setUniqueRequired(true);
        val.validateSources();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Loads and checks the work in the request
     * @param problems receptacle for problems
     * @param qcls request
     * @return map of work from work number
     */
    public UCMap<Work> checkWork(Collection<String> problems, List<QCLabware> qcls) {
        List<String> workNumbers = qcls.stream()
                .map(QCLabware::getWorkNumber)
                .collect(toList());
        return workService.validateUsableWorks(problems, workNumbers);
    }

    /**
     * Checks the timestamps in the request
     * @param problems receptacle for problems
     * @param qcls request
     * @param lwMap specified labware mapped from barcode
     * @param clock clock to get current time
     */
    public void checkTimestamps(Collection<String> problems, List<QCLabware> qcls, UCMap<Labware> lwMap, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        qcls.forEach(qcl -> checkTimestamp(problems, qcl.getCompletion(), today, lwMap.get(qcl.getBarcode())));
    }

    /**
     * Checks the given timestamp
     * @param problems receptacle for problems
     * @param time given timestamp
     * @param today today
     * @param lw the labware relevant to the timestamp
     */
    public void checkTimestamp(Collection<String> problems, LocalDateTime time, LocalDate today, Labware lw) {
        if (time==null) {
            return;
        }
        if (time.toLocalDate().isAfter(today)) {
            problems.add("Specified time is in the future.");
        } else if (lw!=null && lw.getCreated().isAfter(time)) {
            problems.add("Specified time is before labware "+lw.getBarcode()+" was created.");
        }
    }

    /**
     * Loads and the comments specified in the request
     * @param problems receptacle for problems
     * @param qcls request
     * @return comments mapped from their ids
     */
    public Map<Integer, Comment> checkComments(Collection<String> problems, List<QCLabware> qcls) {
        var commentIdStream = qcls.stream().flatMap(qcl -> qcl.getComments().stream());
        var comments = commentValidationService.validateCommentIds(problems, commentIdStream);
        for (QCLabware qcl : qcls) {
            if (!nullOrEmpty(qcl.getBarcode()) && !nullOrEmpty(qcl.getComments())) {
                int numComments = (int) qcl.getComments().stream()
                        .filter(Objects::nonNull)
                        .count();
                int numDistinct = (int) qcl.getComments().stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                if (numDistinct < numComments) {
                    problems.add("Duplicate comments specified for barcode "+qcl.getBarcode()+".");
                }
            }
        }
        return comments.stream().collect(inMap(Comment::getId));
    }

    /**
     * Records the operations
     * @param user user responsible for request
     * @param opType type of operation to record
     * @param qcls parts of request for each labware
     * @param lwMap the indicated labware mapped from barcodes
     * @param workMap the indicated work linked from work numbers
     * @param commentMap the indicated comments linked from ids
     * @return the labware and operations requested
     */
    public OperationResult record(User user, OperationType opType, List<QCLabware> qcls,
                                  UCMap<Labware> lwMap, UCMap<Work> workMap, Map<Integer, Comment> commentMap) {
        final UCMap<Operation> lwOps = new UCMap<>(qcls.size());
        final List<Operation> opsToSave = new ArrayList<>();
        for (QCLabware qcl : qcls) {
            Labware lw = lwMap.get(qcl.getBarcode());
            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            lwOps.put(lw.getBarcode(), op);
            if (qcl.getCompletion()!=null) {
                op.setPerformed(qcl.getCompletion());
                opsToSave.add(op);
            }
        }
        if (!opsToSave.isEmpty()) {
            opRepo.saveAll(opsToSave);
        }
        linkWorks(qcls, workMap, lwOps);
        linkComments(qcls, commentMap, lwOps, lwMap);
        return assembleResult(qcls, lwOps, lwMap);
    }

    /**
     * Links the works to the operations
     * @param qcls request
     * @param workMap map of work from work number
     * @param lwOps map of operations from labware barcode
     */
    public void linkWorks(List<QCLabware> qcls, UCMap<Work> workMap, UCMap<Operation> lwOps) {
        final UCMap<List<Operation>> wnOps = new UCMap<>(workMap.size());
        for (QCLabware qcl : qcls) {
            if (!nullOrEmpty(qcl.getWorkNumber())) {
                wnOps.computeIfAbsent(qcl.getWorkNumber(), k -> new ArrayList<>()).add(lwOps.get(qcl.getBarcode()));
            }
        }
        wnOps.forEach((wn, ops) -> {
            Work work = workMap.get(wn);
            workService.link(work, ops);
        });
    }

    /**
     * Links the indicated comments to the operations and labware
     * @param qcls request
     * @param commentMap map of comments from their ids
     * @param lwOps map of operations from their labware barcode
     * @param lwMap map of labware from barcodes
     */
    public void linkComments(List<QCLabware> qcls, Map<Integer, Comment> commentMap, UCMap<Operation> lwOps, UCMap<Labware> lwMap) {
        List<OperationComment> opcoms = new ArrayList<>();
        for (QCLabware qcl : qcls) {
            if (!nullOrEmpty(qcl.getComments())) {
                qcl.getComments().forEach(id -> {
                    Comment comment = commentMap.get(id);
                    Operation op = lwOps.get(qcl.getBarcode());
                    Labware lw = lwMap.get(qcl.getBarcode());
                    opcoms.add(new OperationComment(null, comment, op.getId(), null, null, lw.getId()));
                });
            }
        }
        if (!opcoms.isEmpty()) {
            opComRepo.saveAll(opcoms);
        }
    }

    /**
     * Assembles an OperationResult from the operations and labware.
     * @param qcls the request that describes the operations
     * @param lwOps the created operations mapped from labware barcodes
     * @param lwMap the indicated labware mapped from its barcodes
     * @return the labware and operations requested
     */
    public OperationResult assembleResult(List<QCLabware> qcls, UCMap<Operation> lwOps, UCMap<Labware> lwMap) {
        List<Operation> ops = new ArrayList<>(qcls.size());
        List<Labware> lws = new ArrayList<>(qcls.size());
        for (QCLabware qcl : qcls) {
            ops.add(lwOps.get(qcl.getBarcode()));
            lws.add(lwMap.get(qcl.getBarcode()));
        }
        return new OperationResult(ops, lws);
    }

}
