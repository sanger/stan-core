package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.OpWithSlotCommentsRequest.LabwareWithSlotCommentsRequest;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

@Service
public class OpWithSlotCommentsServiceImp implements OpWithSlotCommentsService {
    private final WorkService workService;
    private final CommentValidationService commentValidationService;
    private final OperationService opService;
    private final LabwareValidatorFactory lwValidatorFactory;
    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationCommentRepo opCommentRepo;

    @Autowired
    public OpWithSlotCommentsServiceImp(WorkService workService, CommentValidationService commentValidationService,
                                        OperationService opService, LabwareValidatorFactory lwValidatorFactory,
                                        LabwareRepo lwRepo, OperationTypeRepo opTypeRepo,
                                        OperationCommentRepo opCommentRepo) {
        this.workService = workService;
        this.commentValidationService = commentValidationService;
        this.opService = opService;
        this.lwValidatorFactory = lwValidatorFactory;
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.opCommentRepo = opCommentRepo;
    }

    @Override
    public OperationResult perform(User user, OpWithSlotCommentsRequest request) throws ValidationException {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");

        Collection<String> problems = new LinkedHashSet<>();

        OperationType opType = loadOpType(problems, request.getOperationType());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        UCMap<Labware> labwareMap = loadLabware(problems, request.getLabware());
        validateAddresses(problems, labwareMap, request.getLabware());
        Map<Integer, Comment> commentMap = loadComments(problems, request.getLabware());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return record(user, opType, request.getLabware(), work, labwareMap, commentMap);
    }

    /**
     * Loads and validates the operation type
     * @param problems receptacle for problems
     * @param opName the name of the operation type
     * @return the operation type loaded, if any
     */
    public OperationType loadOpType(Collection<String> problems, String opName) {
        if (nullOrEmpty(opName)) {
            problems.add("No operation type specified.");
            return null;
        }
        OperationType opType = opTypeRepo.findByName(opName).orElse(null);
        if (opType==null) {
            problems.add("Unknown operation type: "+repr(opName));
        } else if (!opType.inPlace()) {
            problems.add("Operation " + opType.getName() + " cannot be recorded in-place.");
        }
        return opType;
    }

    /**
     * Loads and validates the labware specified
     * @param problems receptacle for problems
     * @param lrs the requests specifying labware barcodes
     * @return the labware loaded, mapped from barcodes
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, List<LabwareWithSlotCommentsRequest> lrs) {
        if (lrs.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>(0);
        }
        LabwareValidator val = lwValidatorFactory.getValidator();
        List<String> barcodes = lrs.stream().map(LabwareWithSlotCommentsRequest::getBarcode).collect(toList());
        val.loadLabware(lwRepo, barcodes);
        val.setUniqueRequired(true);
        val.validateSources();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Validates the addresses specified, and checks for duplicate comment/slots
     * @param problems receptacle for problems
     * @param labwareMap map to look up labware by barcode
     * @param lrs the requests specifying addresses in particular labware
     */
    public void validateAddresses(Collection<String> problems, UCMap<Labware> labwareMap,
                                  List<LabwareWithSlotCommentsRequest> lrs) {
        for (var lr : lrs) {
            Labware lw = labwareMap.get(lr.getBarcode());
            if (lw == null) {
                return;
            }
            var acs = lr.getAddressComments();
            if (acs == null || acs.isEmpty()) {
                problems.add("Labware specified without comments: " + lw.getBarcode());
            } else {
                Set<AddressCommentId> acSet = new HashSet<>(acs.size());
                for (var ac : acs) {
                    if (ac.getAddress() == null) {
                        problems.add("Null given as address with labware " + lw.getBarcode() + ".");
                    } else {
                        final Optional<Slot> optSlot = lw.optSlot(ac.getAddress());
                        if (optSlot.isEmpty()) {
                            problems.add("No such slot as " + ac.getAddress() + " in labware " + lw.getBarcode() + ".");
                        } else if (optSlot.get().getSamples().isEmpty()) {
                            problems.add("Slot " + ac.getAddress() + " in labware " + lw.getBarcode() + " is empty.");
                        }
                        if (ac.getCommentId() != null && !acSet.add(ac)) {
                            problems.add("Comment and slot repeated: " + ac.getCommentId() + " in slot " + ac.getAddress()
                                    + " of " + lw.getBarcode() + ".");
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads and validates the comments specified by ids
     * @param problems receptacle for problems
     * @param lrs the request specifying the comment ids
     * @return the comments loaded, mapped from their IDs
     */
    public Map<Integer, Comment> loadComments(Collection<String> problems, List<LabwareWithSlotCommentsRequest> lrs) {
        List<Comment> comments = commentValidationService.validateCommentIds(problems, lrs.stream()
                .flatMap(lr -> lr.getAddressComments().stream())
                .map(AddressCommentId::getCommentId));
        return comments.stream().collect(BasicUtils.toMap(Comment::getId));
    }

    /**
     * Records the operation and comments as specified
     * @param user the user responsible
     * @param opType the operation type
     * @param lrs the specification of comments and labware
     * @param work the work (if any) to associate with the operations
     * @param lwMap map to look up labware by barcode
     * @param commentMap map to look up comments by ID
     * @return the operations and labware
     */
    public OperationResult record(User user, OperationType opType, List<LabwareWithSlotCommentsRequest> lrs,
                                  Work work, UCMap<Labware> lwMap, Map<Integer, Comment> commentMap) {
        List<Operation> ops = new ArrayList<>(lrs.size());
        List<Labware> lws = new ArrayList<>(lrs.size());
        List<OperationComment> opComs = new ArrayList<>();
        for (var lr : lrs) {
            Labware lw = lwMap.get(lr.getBarcode());
            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            ops.add(op);
            lws.add(lw);
            streamOpComs(op.getId(), lw, lr.getAddressComments(), commentMap).forEach(opComs::add);
        }
        opCommentRepo.saveAll(opComs);
        if (work != null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, lws);
    }

    /**
     * Creates new (unsaved) OperationComments as specified
     * @param opId the ID of the operation
     * @param lw the labware
     * @param acs the specification of comments and addresses for this labware
     * @param commentMap the map to look up comments by ID
     * @return a stream of OperationComments to be saved
     */
    public Stream<OperationComment> streamOpComs(Integer opId, Labware lw, List<AddressCommentId> acs,
                                                 Map<Integer, Comment> commentMap) {
        return acs.stream()
                .flatMap(ac -> {
                    Slot slot = lw.getSlot(ac.getAddress());
                    Comment comment = commentMap.get(ac.getCommentId());
                    return slot.getSamples().stream()
                            .map(sam -> new OperationComment(null, comment, opId,
                                    sam.getId(), slot.getId(), null));
                });
    }
}
