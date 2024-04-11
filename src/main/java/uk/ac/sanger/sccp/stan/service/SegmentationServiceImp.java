package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SegmentationRequest;
import uk.ac.sanger.sccp.stan.request.SegmentationRequest.SegmentationLabware;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.stan.service.work.WorkService.WorkOp;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class SegmentationServiceImp implements SegmentationService {
    public static final String CELL_SEGMENTATION_OP_NAME = "Cell segmentation";

    private final Clock clock;
    private final ValidationHelperFactory valHelperFactory;

    private final OperationService opService;
    private final WorkService workService;

    private final OperationRepo opRepo;
    private final OperationCommentRepo opComRepo;
    private final LabwareNoteRepo noteRepo;

    public SegmentationServiceImp(Clock clock, ValidationHelperFactory valHelperFactory,
                                  OperationService opService, WorkService workService,
                                  OperationRepo opRepo, OperationCommentRepo opComRepo, LabwareNoteRepo noteRepo) {
        this.clock = clock;
        this.valHelperFactory = valHelperFactory;
        this.opService = opService;
        this.workService = workService;
        this.opRepo = opRepo;
        this.opComRepo = opComRepo;
        this.noteRepo = noteRepo;
    }

    @Override
    public OperationResult perform(User user, SegmentationRequest request) throws ValidationException {
        SegmentationData data = validate(user, request);
        if (!data.problems.isEmpty()) {
            throw new ValidationException(data.problems);
        }
        return record(request.getLabware(), user, data);
    }

    /**
     * Validates the request and loads information into the returned structure
     * @param user the user responsible for the request
     * @param request the details of the request
     * @return the data loaded during validation
     */
    SegmentationData validate(User user, SegmentationRequest request) {
        ValidationHelper val = valHelperFactory.getHelper();
        Set<String> problems = val.getProblems();
        SegmentationData data = new SegmentationData(problems);
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            return data;
        }
        data.opType = val.checkOpType(request.getOperationType(), EnumSet.of(OperationTypeFlag.IN_PLACE),
                null, ot -> ot.getName().equalsIgnoreCase(CELL_SEGMENTATION_OP_NAME));
        if (nullOrEmpty(request.getLabware())) {
            problems.add("No labware specified.");
            return data;
        }
        data.labware = loadLabware(val, request.getLabware());
        data.works = loadWorks(val, request.getLabware());
        data.comments = loadComments(val, request.getLabware());
        checkCostings(problems, request.getLabware());
        checkTimestamps(val, clock, request.getLabware(), data.labware);
        return data;
    }

    /**
     * Loads and checks the labware
     * @param val validation helper
     * @param lwReqs details of the request
     * @return the loaded labware, mapped from barcodes
     */
    UCMap<Labware> loadLabware(ValidationHelper val, List<SegmentationLabware> lwReqs) {
        List<String> barcodes = lwReqs.stream()
                .map(SegmentationLabware::getBarcode)
                .toList();
        return val.checkLabware(barcodes);
    }

    /**
     * Loads and checks the works
     * @param val validation helper
     * @param lwReqs details of the request
     * @return the loaded works, mapped from work number
     */
    UCMap<Work> loadWorks(ValidationHelper val, List<SegmentationLabware> lwReqs) {
        List<String> workNumbers = lwReqs.stream()
                .map(SegmentationLabware::getWorkNumber)
                .toList();
        return val.checkWork(workNumbers);
    }

    /**
     * Loads and checks the comments
     * @param val validation helper
     * @param lwReqs details of the request
     * @return the loaded comments, mapped from ids
     */
    Map<Integer, Comment> loadComments(ValidationHelper val, List<SegmentationLabware> lwReqs) {
        Stream<Integer> commentIds = lwReqs.stream()
                .filter(lwReq -> lwReq.getCommentIds()!=null)
                .flatMap(lwReq -> lwReq.getCommentIds().stream());
        return val.checkCommentIds(commentIds);
    }

    /**
     * Checks the costings
     * @param problems receptacle for problems found
     * @param lwReqs details of the request
     */
    void checkCostings(Collection<String> problems, List<SegmentationLabware> lwReqs) {
        if (lwReqs.stream().anyMatch(lwReq -> lwReq.getCosting()==null)) {
            problems.add("Costing missing from request.");
        }
    }

    /**
     * Checks the timestamps
     * @param val validation helper
     * @param clock to get current time
     * @param lwReqs details of the request
     * @param lwMap map to look up labware from its barcode
     */
    void checkTimestamps(ValidationHelper val, Clock clock, List<SegmentationLabware> lwReqs, UCMap<Labware> lwMap) {
        LocalDate today = LocalDate.now(clock);
        for (SegmentationLabware lwReq : lwReqs) {
            if (lwReq.getPerformed()!=null) {
                val.checkTimestamp(lwReq.getPerformed(), today, lwMap==null ? null : lwMap.get(lwReq.getBarcode()));
            }
        }
    }

    /**
     * Records the operations and all associated information for the request
     * @param lwReqs details of the request
     * @param user the user responsible
     * @param data data loaded during validation
     * @return the labware involved and operations created
     */
    OperationResult record(List<SegmentationLabware> lwReqs, User user, SegmentationData data) {
        List<Operation> ops = new ArrayList<>(lwReqs.size());
        List<Labware> labware = new ArrayList<>(lwReqs.size());
        List<WorkOp> newWorkOps = new ArrayList<>(lwReqs.size());
        List<Operation> opsToUpdate = new ArrayList<>();
        List<LabwareNote> newNotes = new ArrayList<>();
        List<OperationComment> newOpComs = new ArrayList<>();
        for (SegmentationLabware lwReq: lwReqs) {
            Labware lw = data.labware.get(lwReq.getBarcode());
            Operation op = recordOp(user, data, lwReq, lw, opsToUpdate, newNotes, newOpComs, newWorkOps);
            labware.add(lw);
            ops.add(op);
        }
        if (!opsToUpdate.isEmpty()) {
            opRepo.saveAll(opsToUpdate);
        }
        if (!newNotes.isEmpty()) {
            noteRepo.saveAll(newNotes);
        }
        if (!newOpComs.isEmpty()) {
            opComRepo.saveAll(newOpComs);
        }
        if (!newWorkOps.isEmpty()) {
            workService.linkWorkOps(newWorkOps.stream());
        }

        return new OperationResult(ops, labware);
    }

    /**
     * Records an operation and updates various lists with information to be saved
     * @param user the user responsible
     * @param data data loaded during validation
     * @param lwReq details of the request for this labware
     * @param lw the labware being operated on
     * @param opsToUpdate receptacle for ops that need to be updated
     * @param newNotes receptacle for new notes that need to be saved
     * @param newOpComs receptacle for new operation comments that need to be saved
     * @param newWorkOps receptacle for new work-operation links that need to be saved
     * @return the operation created
     */
    Operation recordOp(User user, SegmentationData data, SegmentationLabware lwReq, Labware lw,
                       final List<Operation> opsToUpdate, final List<LabwareNote> newNotes,
                       final List<OperationComment> newOpComs, final List<WorkOp> newWorkOps) {
        final Operation op = opService.createOperationInPlace(data.opType, user, lw, null, null);
        if (lwReq.getPerformed() != null) {
            op.setPerformed(lwReq.getPerformed());
            opsToUpdate.add(op);
        }
        if (lwReq.getCosting()!=null) {
            newNotes.add(new LabwareNote(null, lw.getId(), op.getId(), "costing",
                    lwReq.getCosting().name()));
        }
        if (!lwReq.getCommentIds().isEmpty()) {
            lwReq.getCommentIds().stream()
                    .map(data.comments::get)
                    .flatMap(com -> op.getActions().stream()
                            .map(ac -> new OperationComment(null, com, op.getId(), ac.getSample().getId(),
                                    ac.getDestination().getId(), null)))
                    .forEach(newOpComs::add);
        }
        if (lwReq.getWorkNumber()!=null) {
            newWorkOps.add(new WorkOp(data.works.get(lwReq.getWorkNumber()), op));
        }

        return op;
    }

    /** Data loaded during validation */
    static class SegmentationData {
        Collection<String> problems;
        OperationType opType;
        UCMap<Work> works;
        UCMap<Labware> labware;
        Map<Integer, Comment> comments;

        public SegmentationData(Collection<String> problems) {
            this.problems = problems;
        }
    }
}
