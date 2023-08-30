package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationCommentRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCLabware;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.containsDupes;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class QCLabwareServiceImp implements QCLabwareService {
    private final Clock clock;
    private final ValidationHelperFactory valFactory;
    private final OperationRepo opRepo;
    private final OperationCommentRepo opComRepo;
    private final WorkService workService;
    private final OperationService opService;

    @Autowired
    public QCLabwareServiceImp(Clock clock, ValidationHelperFactory valFactory,
                               OperationRepo opRepo, OperationCommentRepo opComRepo,
                               WorkService workService, OperationService opService) {
        this.clock = clock;
        this.valFactory = valFactory;
        this.opRepo = opRepo;
        this.opComRepo = opComRepo;
        this.workService = workService;
        this.opService = opService;
    }


    @Override
    public OperationResult perform(User user, QCLabwareRequest request) throws ValidationException {
        ValidationHelper val = valFactory.getHelper();
        OperationType opType = val.checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        List<QCLabware> qcls = request.getLabware();
        if (nullOrEmpty(qcls)) {
            Set<String> problems = val.getProblems();
            problems.add("No labware specified.");
            throw new ValidationException(problems);
        }
        UCMap<Labware> lwMap = val.checkLabware(qcls.stream().map(QCLabware::getBarcode).collect(toList()));
        UCMap<Work> workMap = val.checkWork(qcls.stream().map(QCLabware::getWorkNumber).collect(toList()));
        checkTimestamps(val, qcls, lwMap, clock);
        Map<Integer, Comment> commentMap = checkComments(val, qcls);
        if (!val.getProblems().isEmpty()) {
            throw new ValidationException(val.getProblems());
        }

        return record(user, opType, qcls, lwMap, workMap, commentMap);
    }

    /**
     * Checks the timestamps in the request
     * @param val validation helper
     * @param qcls request
     * @param lwMap specified labware mapped from barcode
     * @param clock clock to get current time
     */
    public void checkTimestamps(ValidationHelper val, List<QCLabware> qcls, UCMap<Labware> lwMap, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        qcls.forEach(qcl -> val.checkTimestamp(qcl.getCompletion(), today, lwMap.get(qcl.getBarcode())));
    }

    /**
     * Loads and the comments specified in the request
     * @param val validation helper
     * @param qcls request
     * @return comments mapped from their ids
     */
    public Map<Integer, Comment> checkComments(ValidationHelper val, List<QCLabware> qcls) {
        Map<Integer, Comment> commentMap = val.checkCommentIds(qcls.stream().flatMap(qcl -> qcl.getComments().stream()));
        for (QCLabware qcl : qcls) {
            if (!nullOrEmpty(qcl.getBarcode()) && !nullOrEmpty(qcl.getComments()) && containsDupes(qcl.getComments())) {
                val.getProblems().add("Duplicate comments specified for barcode "+qcl.getBarcode()+".");
            }
        }
        return commentMap;
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
