package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationCommentRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCLabware;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest.QCSampleComment;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp.RUN_NAME;
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
    private final LabwareNoteService lwNoteService;

    @Autowired
    public QCLabwareServiceImp(Clock clock, ValidationHelperFactory valFactory,
                               OperationRepo opRepo, OperationCommentRepo opComRepo,
                               WorkService workService, OperationService opService,
                               LabwareNoteService lwNoteService) {
        this.clock = clock;
        this.valFactory = valFactory;
        this.opRepo = opRepo;
        this.opComRepo = opComRepo;
        this.workService = workService;
        this.opService = opService;
        this.lwNoteService = lwNoteService;
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
        checkSampleComments(val, qcls, lwMap);
        checkRunNames(val.getProblems(), qcls, lwMap);
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
        Stream<Integer> commentIds = Stream.concat(
                qcls.stream().flatMap(qcl -> qcl.getComments().stream()),
                qcls.stream().map(QCLabware::getSampleComments)
                        .filter(Objects::nonNull)
                        .flatMap(scs -> scs.stream().map(QCSampleComment::getCommentId))
        );
        Map<Integer, Comment> commentMap = val.checkCommentIds(commentIds);
        for (QCLabware qcl : qcls) {
            if (nullOrEmpty(qcl.getBarcode())) {
                continue;
            }
            if (containsDupes(qcl.getComments()) || !nullOrEmpty(qcl.getSampleComments()) && containsDupes(qcl.getSampleComments())) {
                val.getProblems().add("Duplicate comments specified for barcode "+qcl.getBarcode()+".");
            }
        }
        return commentMap;
    }

    /**
     * Checks the validity of sample comments specified in the request
     * @param val validation helper
     * @param qcls labware in the request
     * @param labware map to look up labware
     */
    public void checkSampleComments(ValidationHelper val, List<QCLabware> qcls, UCMap<Labware> labware) {
        for (QCLabware qcl : qcls) {
            if (nullOrEmpty(qcl.getSampleComments())) {
                continue;
            }
            Labware lw = labware.get(qcl.getBarcode());
            for (QCSampleComment sc : qcl.getSampleComments()) {
                Slot slot = null;
                if (sc.getAddress()==null) {
                    val.getProblems().add("Missing slot address for sample comment.");
                } else if (lw!=null) {
                    slot = lw.optSlot(sc.getAddress()).orElse(null);
                    if (slot==null) {
                        val.getProblems().add(String.format("No slot at address %s in labware %s.",
                                sc.getAddress(), lw.getBarcode()));
                    }
                }
                if (sc.getSampleId()==null) {
                    val.getProblems().add("Missing sample ID for sample comment.");
                } else if (slot!=null && slot.getSamples().stream().noneMatch(sam -> sam.getId().equals(sc.getSampleId()))) {
                    val.getProblems().add(String.format("Sample ID %s is not present in slot %s of labware %s.",
                            sc.getSampleId(), sc.getAddress(), lw.getBarcode()));
                }
            }
        }
    }

    /**
     * Checks that run-names (if supplied) are valid for the indicated labware
     * @param problems receptacle for problems
     * @param qcls request
     * @param lwMap map of labware from barcode
     */
    public void checkRunNames(Collection<String> problems, List<QCLabware> qcls, UCMap<Labware> lwMap) {
        Set<Labware> notedLabware = qcls.stream()
                .filter(qcl -> !nullOrEmpty(qcl.getRunName()))
                .map(qcl -> lwMap.get(qcl.getBarcode()))
                .filter(Objects::nonNull)
                .collect(toSet());
        if (notedLabware.isEmpty()) {
            return;
        }
        var bcValues = lwNoteService.findNoteValuesForLabware(notedLabware, RUN_NAME);
        for (QCLabware qcl : qcls) {
            if (!nullOrEmpty(qcl.getRunName())) {
                Labware lw = lwMap.get(qcl.getBarcode());
                Set<String> values = bcValues.get(qcl.getBarcode());
                if (lw!=null && (values == null || !values.contains(qcl.getRunName()))) {
                    problems.add(String.format("%s is not a recorded run-name for labware %s.", qcl.getRunName(), lw.getBarcode()));
                }
            }
        }
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
        saveNotes(qcls, lwOps, lwMap);
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
            if (!nullOrEmpty(qcl.getSampleComments())) {
                qcl.getSampleComments().forEach(sc -> {
                    Comment comment = commentMap.get(sc.getCommentId());
                    Operation op = lwOps.get(qcl.getBarcode());
                    Labware lw = lwMap.get(qcl.getBarcode());
                    Slot slot = lw.getSlot(sc.getAddress());
                    opcoms.add(new OperationComment(null, comment, op.getId(), sc.getSampleId(), slot.getId(), null));
                });
            }
        }
        if (!opcoms.isEmpty()) {
            opComRepo.saveAll(opcoms);
        }
    }

    /**
     * Saves run names as notes
     * @param qcls the request
     * @param lwOps the new operations, mapped from lw barcode
     * @param lwMap the labware, mapped from barcode
     */
    public void saveNotes(List<QCLabware> qcls, UCMap<Operation> lwOps, UCMap<Labware> lwMap) {
        UCMap<String> noteValues = new UCMap<>();
        qcls.stream()
                .filter(qcl -> !nullOrEmpty(qcl.getRunName()))
                .forEach(qcl -> noteValues.put(qcl.getBarcode(), qcl.getRunName()));
        if (!noteValues.isEmpty()) {
            lwNoteService.createNotes(RUN_NAME, lwMap, lwOps, noteValues);
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
