package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.CompletionRequest;
import uk.ac.sanger.sccp.stan.request.CompletionRequest.LabwareSampleComments;
import uk.ac.sanger.sccp.stan.request.CompletionRequest.SampleAddressComment;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.operation.OpSearcher;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;

@Service
public class CompletionServiceImp extends BaseResultService implements CompletionService {
    /** The name of the probe hybridisation qc op type */
    public static final String PROBE_QC_NAME = "Probe hybridisation QC";
    /** The names of the preceding probe hybridisation op types */
    public static final List<String> PROBE_HYBRIDISATION_NAMES = List.of("Probe hybridisation Xenium", "Probe hybridisation Cytassist");

    private final Clock clock;

    private final OperationCommentRepo opComRepo;
    private final WorkService workService;
    private final CommentValidationService commentValidationService;
    private final OperationService opService;

    @Autowired
    public CompletionServiceImp(LabwareValidatorFactory lwValFactory,
                                OperationTypeRepo opTypeRepo, OperationRepo opRepo,
                                LabwareRepo lwRepo, OpSearcher opSearcher,
                                Clock clock,
                                OperationCommentRepo opComRepo,
                                WorkService workService, CommentValidationService commentValidationService,
                                OperationService opService) {
        super(lwValFactory, opTypeRepo, opRepo, lwRepo, opSearcher);
        this.clock = clock;
        this.opComRepo = opComRepo;
        this.workService = workService;
        this.commentValidationService = commentValidationService;
        this.opService = opService;
    }

    @Override
    public OperationResult perform(User user, CompletionRequest request) throws ValidationException {
        Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("User not specified.");
        }
        if (request==null) {
            problems.add("Request not specified.");
            throw new ValidationException(problems);
        }
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        OperationType opType = validateOpType(problems, request.getOperationType());
        UCMap<Labware> lwMap = loadLabware(problems, request.getLabware());
        List<OperationType> precedingOpTypes = getPrecedingOpTypes(problems, opType);
        Map<Integer, Operation> priorOpMap;
        if (precedingOpTypes!=null) {
            priorOpMap = lookUpLatestOps(problems, precedingOpTypes, lwMap.values(), true);
        } else {
            priorOpMap = Map.of();
        }
        validateTimestamps(problems, request.getLabware(), lwMap, priorOpMap);
        validateCommentLocations(problems, request.getLabware(), lwMap);
        Map<Integer, Comment> commentMap = validateCommentIds(problems, request.getLabware());

        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return execute(user, request.getLabware(), opType, work, lwMap, commentMap, priorOpMap);
    }

    /**
     * Checks that the specified op type exists and seems suitable
     * @param problems receptacle for problems
     * @param opName the name of the op type
     * @return the specified op type, if it exists
     */
    public OperationType validateOpType(Collection<String> problems, String opName) {
        OperationType opType = loadOpType(problems, opName);
        if (opType!=null && !opType.inPlace()) {
            problems.add("Operation type "+opType.getName()+" cannot be used in this operation.");
        }
        return opType;
    }

    /**
     * What are the types of op that must precede the given op type?
     * @param problems receptacle for problems
     * @param opType the op type being recorded
     * @return the required preceding op types
     */
    public List<OperationType> getPrecedingOpTypes(Collection<String> problems, OperationType opType) {
        if (opType==null) {
            return null;
        }
        if (!opType.getName().equalsIgnoreCase(PROBE_QC_NAME)) {
            problems.add("Operation type "+opType.getName()+" cannot be used in this operation.");
            return null;
        }
        List<String> precedingOpNames = PROBE_HYBRIDISATION_NAMES;
        List<OperationType> opTypes = opTypeRepo.findByNameIn(precedingOpNames);
        if (opTypes.isEmpty()) {
            problems.add("Operation type missing from database: "+precedingOpNames);
            return null;
        }
        return opTypes;
    }

    /**
     * Loads the indicated labware and checks it is usable
     * @param problems receptacle for problems
     * @param lscs the parts of the request specifying labware barcodes
     * @return the loaded labware, mapped from its barcode
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, List<LabwareSampleComments> lscs) {
        List<String> barcodes = lscs.stream()
                .map(LabwareSampleComments::getBarcode)
                .collect(toList());
        return loadLabware(problems, barcodes);
    }

    /**
     * Checks the timestamps (if given) are vaguely sensible
     * @param problems receptacle for problems
     * @param lscs parts of the request specifying labware and timestamps
     * @param lwMap the indicated labware mapped from its barcode
     * @param priorOpMap the preceding ops, mapped from labware id
     */
    public void validateTimestamps(Collection<String> problems, Collection<LabwareSampleComments> lscs,
                                   UCMap<Labware> lwMap, Map<Integer, Operation> priorOpMap) {
        Set<LocalDateTime> futureTimes = new LinkedHashSet<>();
        LocalDate today = LocalDate.now(clock);
        for (LabwareSampleComments lsc : lscs) {
            LocalDateTime time = lsc.getCompletion();
            if (time==null) {
                continue;
            }
            if (time.toLocalDate().isAfter(today)) {
                futureTimes.add(time);
                continue;
            }
            Labware lw = lwMap.get(lsc.getBarcode());
            Operation priorOp = (lw==null ? null : priorOpMap.get(lw.getId()));
            if (priorOp!=null && priorOp.getPerformed().isAfter(time)) {
                problems.add("The time given for labware "+lw.getBarcode()+" is before its prior operation.");
            }
        }
        if (!futureTimes.isEmpty()) {
            problems.add(pluralise("Specified date{s} {is|are} in the future: ", futureTimes.size()) + futureTimes);
        }
    }

    /**
     * Checks the sample ids and slot addresses specified for comments in each labware.
     * Problems include:<ul>
     *     <li>Missing sample id</li>
     *     <li>Missing slot address</li>
     *     <li>No such slot in labware</li>
     *     <li>No such sample in slot</li>
     *     <li>Same comment specified for the same sample and slot multiple times</li>
     * </ul>
     * @param problems receptacle for problems
     * @param lscs parts of the request specifying comments in labware
     * @param lwMap map of the indicated labware from its barcodes
     */
    public void validateCommentLocations(Collection<String> problems, Collection<LabwareSampleComments> lscs,
                                         UCMap<Labware> lwMap) {
        for (LabwareSampleComments lsc : lscs) {
            Labware lw = lwMap.get(lsc.getBarcode());
            if (lw==null) {
                continue;
            }
            Set<SampleAddressComment> repeatedSacs = new LinkedHashSet<>();
            Set<SampleAddressComment> sacSet = new HashSet<>();
            for (SampleAddressComment sac : lsc.getComments()) {
                Slot slot = null;
                if (sac.getAddress()==null) {
                    problems.add("Slot address for comment not specified.");
                } else {
                    slot = lw.optSlot(sac.getAddress()).orElse(null);
                    if (slot==null) {
                        problems.add("No slot "+sac.getAddress()+" in labware "+lw.getBarcode()+".");
                    }
                }
                if (sac.getSampleId()==null) {
                    problems.add("Sample id not specified.");
                } else if (slot!=null) {
                    if (slot.getSamples().stream().noneMatch(sam -> sam.getId().equals(sac.getSampleId()))) {
                        problems.add("Sample id "+sac.getSampleId()+" is not present in "+lw.getBarcode()+" slot "+sac.getAddress()+".");
                    }
                }
                if (slot != null && sac.getCommentId()!=null && !sacSet.add(sac)) {
                    repeatedSacs.add(sac);
                }
            }
            if (!repeatedSacs.isEmpty()) {
                problems.add("Repeated comment specified in "+lw.getBarcode()+": "+repeatedSacs);
            }
        }
    }

    /**
     * Validates and loads the specified comment ids
     * @param problems receptacle for problems
     * @param lscs parts of the request specifying comment ids
     * @return map of indicated comments from their ids
     */
    public Map<Integer, Comment> validateCommentIds(Collection<String> problems, Collection<LabwareSampleComments> lscs) {
        Stream<Integer> commentIdStream = lscs.stream()
                .flatMap(lsc -> lsc.getComments().stream().map(SampleAddressComment::getCommentId));
        return commentValidationService.validateCommentIds(problems, commentIdStream)
                .stream()
                .collect(BasicUtils.inMap(Comment::getId));
    }

    /**
     * Records the indicated operations and comments
     * @param user the user responsible for the operations
     * @param lscs the request parts
     * @param opType the specifying operation type
     * @param work the work to link the operations to
     * @param lwMap the specified labware, mapped from its barcode
     * @param commentMap the indicated comments, mapped from their its
     * @param priorOpMap map from labware id to prior operation
     * @return the labware and operations used in this request
     */
    public OperationResult execute(User user, Collection<LabwareSampleComments> lscs, OperationType opType, Work work,
                                   UCMap<Labware> lwMap, Map<Integer, Comment> commentMap, Map<Integer, Operation> priorOpMap) {
        UCMap<Operation> bcOps = makeOps(user, opType, lscs, lwMap, priorOpMap);
        recordComments(lscs, lwMap, bcOps, commentMap);
        workService.link(work, bcOps.values());
        return composeResult(lscs, lwMap, bcOps);
    }

    /**
     * Create the specified operations in place
     * @param user the user responsible for the operations
     * @param opType the type of operation to record
     * @param lscs the parts of the request indicating each labware and optional timestamp
     * @param lwMap map of the indicated labware from its barcode
     * @param priorOpMap map from labware id to prior operation
     * @return the created operations, mapped from the labware barcode
     */
    public UCMap<Operation> makeOps(User user, OperationType opType, Collection<LabwareSampleComments> lscs,
                                    UCMap<Labware> lwMap, Map<Integer, Operation> priorOpMap) {
        List<Operation> opsToUpdate = new ArrayList<>();
        UCMap<Operation> opsToReturn = new UCMap<>(lscs.size());
        for (LabwareSampleComments lsc : lscs) {
            Labware lw = lwMap.get(lsc.getBarcode());
            Operation newOp = createOp(opType, user, lw, priorOpMap.get(lw.getId()));
            if (lsc.getCompletion()!=null) {
                newOp.setPerformed(lsc.getCompletion());
                opsToUpdate.add(newOp);
            }
            opsToReturn.put(lw.getBarcode(), newOp);
        }
        if (!opsToUpdate.isEmpty()) {
            opRepo.saveAll(opsToUpdate);
        }
        return opsToReturn;
    }

    /**
     * Creates the new operation based on actions from the prior operation
     * @param opType type of op to create
     * @param user user responsible for op
     * @param lw the labware involved in the op
     * @param priorOp prior op for labware
     * @return newly created operation
     */
    public Operation createOp(OperationType opType, User user, Labware lw, Operation priorOp) {
        requireNonNull(priorOp, "No prior operation for labware "+lw.getBarcode());
        List<Action> actions = priorOp.getActions().stream()
                .filter(ac -> ac.getDestination().getLabwareId().equals(lw.getId()))
                .map(ac -> new Action(null, null, ac.getDestination(), ac.getDestination(), ac.getSample(), ac.getSample()))
                .distinct()
                .toList();
        return opService.createOperation(opType, user, actions, null);
    }

    /**
     * Records the indicated comments against each operation
     * @param lscs the specification of labware and comments
     * @param lwMap the labware mapped from its barcodes
     * @param bcOps the operation mapped from the labware barcodes
     * @param commentMap the indicated comments mapped from their ids
     */
    public void recordComments(Collection<LabwareSampleComments> lscs, UCMap<Labware> lwMap,
                               UCMap<Operation> bcOps, Map<Integer, Comment> commentMap) {
        final List<OperationComment> newOpComments = new ArrayList<>();
        for (LabwareSampleComments lsc : lscs) {
            Labware lw = lwMap.get(lsc.getBarcode());
            Operation op = bcOps.get(lw.getBarcode());
            for (SampleAddressComment sac : lsc.getComments()) {
                OperationComment opCom = new OperationComment(null, commentMap.get(sac.getCommentId()),
                        op.getId(), sac.getSampleId(), lw.getSlot(sac.getAddress()).getId(), null);
                newOpComments.add(opCom);
            }
        }
        opComRepo.saveAll(newOpComments);
    }

    /**
     * Assembles the operations and labware into an OperationResult, matching the order of the given request
     * @param lscs the parts of the request specifying the labware
     * @param lwMap the indicated labware, mapped from its barcodes
     * @param bcOps the created operations, mapped from the labware barcode
     * @return the labware and operations in the appropriate order
     */
    public OperationResult composeResult(Collection<LabwareSampleComments> lscs, UCMap<Labware> lwMap,
                                         UCMap<Operation> bcOps) {
        OperationResult opres = new OperationResult();
        for (LabwareSampleComments lsc : lscs) {
            opres.getLabware().add(lwMap.get(lsc.getBarcode()));
            opres.getOperations().add(bcOps.get(lsc.getBarcode()));
        }
        return opres;
    }
}
