package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.BarcodeAndCommentId;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class InPlaceOpCommentServiceImp implements InPlaceOpCommentService {
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final OperationCommentRepo opComRepo;
    private final CommentValidationService commentValidationService;
    private final OperationService opService;
    private final LabwareValidatorFactory lwValFactory;

    public InPlaceOpCommentServiceImp(OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, OperationCommentRepo opComRepo,
                                      CommentValidationService commentValidationService, OperationService opService,
                                      LabwareValidatorFactory lwValFactory) {
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.opComRepo = opComRepo;
        this.commentValidationService = commentValidationService;
        this.opService = opService;
        this.lwValFactory = lwValFactory;
    }

    @Override
    public OperationResult perform(User user, String opTypeName, Collection<BarcodeAndCommentId> barcodesAndCommentIds)
            throws ValidationException {
        requireNonNull(user, "User is null.");
        Collection<String> problems = new LinkedHashSet<>();
        OperationType opType = loadOpType(problems, opTypeName);
        UCMap<Labware> labwareMap = loadLabware(problems, barcodesAndCommentIds);
        Map<Integer, Comment> commentMap = loadComments(problems, barcodesAndCommentIds);
        checkBarcodesAndCommentIds(problems, barcodesAndCommentIds);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }
        return record(user, opType, labwareMap, commentMap, barcodesAndCommentIds);
    }

    /**
     * Loads the operation type. Checks it seems suitable.
     * @param problems receptacle for problems
     * @param opTypeName the name of the operation type
     * @return the operation type found, or null
     */
    public OperationType loadOpType(Collection<String> problems, String opTypeName) {
        if (nullOrEmpty(opTypeName)) {
            problems.add("No operation type specified.");
            return null;
        }
        OperationType opType = opTypeRepo.findByName(opTypeName).orElse(null);
        if (opType==null) {
            problems.add("Operation type not found: "+repr(opTypeName));
        } else if (!opType.inPlace()) {
            problems.add("The operation type "+opType.getName()+" cannot be recorded in-place.");
        }
        return opType;
    }

    /**
     * Loads and validates the labware
     * @param problems receptacle for problems
     * @param barcodesAndCommentIds the map from labware barcodes to comment ids
     * @return the labware, mapped from its barcode
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, Collection<BarcodeAndCommentId> barcodesAndCommentIds) {
        if (barcodesAndCommentIds==null || barcodesAndCommentIds.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>();
        }
        boolean anyMissing = false;
        Set<String> barcodes = new HashSet<>(barcodesAndCommentIds.size());
        for (var bcom : barcodesAndCommentIds) {
            String bc = bcom.getBarcode();
            if (nullOrEmpty(bc)) {
                anyMissing = true;
            } else {
                barcodes.add(bc.toUpperCase());
            }
        }
        if (anyMissing) {
            problems.add("Missing labware barcode.");
        }
        if (barcodes.isEmpty()) {
            return new UCMap<>();
        }
        LabwareValidator val = lwValFactory.getValidator();
        val.loadLabware(lwRepo, barcodes);
        val.validateSources();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Loads the comments from their ids.
     * @param problems receptacle for problems
     * @param barcodesAndCommentIds the barcodes and comment ids.
     * @return map of comments from their ids
     */
    public Map<Integer, Comment> loadComments(Collection<String> problems, Collection<BarcodeAndCommentId> barcodesAndCommentIds) {
        if (barcodesAndCommentIds==null || barcodesAndCommentIds.isEmpty()) {
            return Map.of();
        }
        var comments = commentValidationService.validateCommentIds(problems,
                barcodesAndCommentIds.stream().map(BarcodeAndCommentId::getCommentId));
        return comments.stream()
                .collect(BasicUtils.toMap(Comment::getId));
    }

    /**
     * Checks for other problems with the barcodes and comment ids, such as repetition.
     * @param problems receptacle for problems
     * @param barcodesAndCommentIds barcodes and comment ids
     */
    public void checkBarcodesAndCommentIds(Collection<String> problems, Collection<BarcodeAndCommentId> barcodesAndCommentIds) {
        if (barcodesAndCommentIds==null || barcodesAndCommentIds.isEmpty()) {
            return;
        }
        Set<BarcodeAndCommentId> seen = new HashSet<>(barcodesAndCommentIds.size());
        Set<BarcodeAndCommentId> dupes = new LinkedHashSet<>();
        for (var bcom : barcodesAndCommentIds) {
            if (!nullOrEmpty(bcom.getBarcode()) && bcom.getCommentId()!=null) {
                BarcodeAndCommentId sanitised = new BarcodeAndCommentId(bcom.getBarcode().toUpperCase(), bcom.getCommentId());
                if (!seen.add(sanitised)) {
                    dupes.add(sanitised);
                }
            }
        }
        if (!dupes.isEmpty()) {
            problems.add("Duplicate barcode and comment IDs given: "+dupes);
        }
    }

    /**
     * Records an operation for each specified barcode. Links the operations to the indicates comments.
     * @param user the user responsible
     * @param opType the type of op to record
     * @param labwareMap the map to look up the labware
     * @param commentMap the map to look up the comments
     * @param barcodesAndCommentIds the specification of what comment ids to link to each labware
     * @return the operations created and the labware
     */
    public OperationResult record(User user, OperationType opType, UCMap<Labware> labwareMap, Map<Integer, Comment> commentMap,
                                  Collection<BarcodeAndCommentId> barcodesAndCommentIds) {
        UCMap<Operation> ops = createOperations(user, opType, labwareMap.values());
        createComments(ops, commentMap, barcodesAndCommentIds);
        return composeResult(barcodesAndCommentIds, ops, labwareMap);
    }

    /**
     * Creates operations in place for the given labware
     * @param user the user responsible
     * @param opType the type of ops
     * @param labware the labware
     * @return a map of operations from the labware's barcodes
     */
    public UCMap<Operation> createOperations(User user, OperationType opType, Collection<Labware> labware) {
        UCMap<Operation> ops = new UCMap<>(labware.size());
        for (Labware lw : labware) {
            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            ops.put(lw.getBarcode(), op);
        }
        return ops;
    }

    /**
     * Creates comments as described for the given operations
     * @param ops ops, mapped from the labware's barcode
     * @param commentMap comments mapped from their ids
     * @param barcodeAndCommentIds links between labware barcodes and comment ids
     */
    public Iterable<OperationComment> createComments(UCMap<Operation> ops, Map<Integer, Comment> commentMap,
                               Collection<BarcodeAndCommentId> barcodeAndCommentIds) {
        List<OperationComment> opComs = barcodeAndCommentIds.stream()
                .flatMap(bcom -> {
                    Operation op = ops.get(bcom.getBarcode());
                    return op.getActions().stream()
                            .map(ac -> new OperationComment(null, commentMap.get(bcom.getCommentId()),
                                    op.getId(), ac.getSample().getId(), ac.getDestination().getId(), null));
                })
                .collect(toList());
        return opComRepo.saveAll(opComs);
    }

    /**
     * Assembles the operations and labware into an operation result, in the same order the barcodes
     * appeared in the request (without repetitions).
     * @param barcodesAndCommentIds the requested barcodes and comment ids
     * @param ops the operations, mapped from labware barcodes
     * @param labwareMap the labware, mapped from their barcodes
     * @return the operations and labware, in the order requested but without repetitions
     */
    public OperationResult composeResult(Collection<BarcodeAndCommentId> barcodesAndCommentIds,
                                         UCMap<Operation> ops, UCMap<Labware> labwareMap) {
        Set<Integer> seenOps = new HashSet<>(ops.size());
        List<Operation> opList = new ArrayList<>(ops.size());
        List<Labware> lwList = new ArrayList<>(ops.size());
        for (var bcom : barcodesAndCommentIds) {
            final String barcode = bcom.getBarcode();
            Operation op = ops.get(barcode);
            if (seenOps.add(op.getId())) {
                opList.add(op);
                lwList.add(labwareMap.get(barcode));
            }
        }
        return new OperationResult(opList, lwList);
    }

}
