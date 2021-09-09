package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ResultRequest;
import uk.ac.sanger.sccp.stan.request.ResultRequest.LabwareResult;
import uk.ac.sanger.sccp.stan.request.ResultRequest.SampleResult;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

@Service
public class ResultServiceImp implements ResultService {
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final OperationRepo opRepo;
    private final OperationCommentRepo opCommentRepo;
    private final ResultOpRepo resOpRepo;

    private final LabwareValidatorFactory labwareValidatorFactory;
    private final OperationService opService;
    private final WorkService workService;
    private final CommentValidationService commentValidationService;

    @Autowired
    public ResultServiceImp(OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, OperationRepo opRepo,
                            OperationCommentRepo opCommentRepo, ResultOpRepo resOpRepo,
                            LabwareValidatorFactory labwareValidatorFactory,
                            OperationService opService, WorkService workService,
                            CommentValidationService commentValidationService) {
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.opRepo = opRepo;
        this.opCommentRepo = opCommentRepo;
        this.resOpRepo = resOpRepo;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.opService = opService;
        this.workService = workService;
        this.commentValidationService = commentValidationService;
    }

    @Override
    public OperationResult recordStainResult(User user, ResultRequest request) {
        Set<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user specified.");
        }
        OperationType opType = loadOpType(problems, "Record result");
        UCMap<Labware> labware = validateLabware(problems, request.getLabwareResults());
        validateLabwareContents(problems, labware, request.getLabwareResults());
        Map<Integer, Comment> commentMap = validateComments(problems, request.getLabwareResults());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        Map<Integer, Integer> latestStain = lookUpStains(problems, labware.values());

        if (!problems.isEmpty()) {
            throw new ValidationException("The result request could not be validated.", problems);
        }

        return createResults(user, opType, request.getLabwareResults(), labware, latestStain, commentMap, work);
    }

    /**
     * Loads an operation type by name
     * @param problems receptacle for problems
     * @param opTypeName the name of the op type
     * @return the op type loaded
     */
    public OperationType loadOpType(Collection<String> problems, String opTypeName) {
        Optional<OperationType> opt = opTypeRepo.findByName(opTypeName);
        if (opt.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opTypeName));
            return null;
        }
        return opt.get();
    }

    /**
     * Validates the labware. Labware must exist and be nonempty, without repeats.
     * @param problems receptacle for problems
     * @param labwareResults the requested labware results
     * @return a map of labware from its barcode
     */
    public UCMap<Labware> validateLabware(Collection<String> problems, Collection<LabwareResult> labwareResults) {
        LabwareValidator validator = labwareValidatorFactory.getValidator();
        List<String> barcodes = labwareResults.stream()
                .map(LabwareResult::getBarcode)
                .collect(toList());
        validator.loadLabware(lwRepo, barcodes);
        problems.addAll(validator.getErrors());
        return UCMap.from(validator.getLabware(), Labware::getBarcode);
    }

    /**
     * Checks the specified labware results
     * @param problems receptacle for problems
     * @param labware map to look up labware by barcode
     * @param labwareResults the labware results to validate
     */
    public void validateLabwareContents(Collection<String> problems, UCMap<Labware> labware,
                                                        Collection<LabwareResult> labwareResults) {
        for (LabwareResult lr : labwareResults) {
            Labware lw = labware.get(lr.getBarcode());
            if (lw==null) {
                continue;
            }
            if (lr.getSampleResults().isEmpty()) {
                problems.add("No results specified for labware "+lw.getBarcode()+".");
                continue;
            }
            Set<List<Integer>> slotSampleIds = new HashSet<>(lr.getSampleResults().size());
            for (SampleResult sr : lr.getSampleResults()) {
                validateSampleResult(problems, lw, slotSampleIds, sr);
            }
        }
    }

    /**
     * Validates the specified sample result.
     * Possible problems include<ul>
     *     <li>Missing fields in the sample result</li>
     *     <li>Comment id missing for fail result</li>
     *     <li>Same slot/sample combination given for this labware</li>
     *     <li>Invalid slot or sample</li>
     * </ul>
     * @param problems receptacle for problems
     * @param lw the labware of this result
     * @param slotSampleIds the accumulated set of slot and sample ids for this labware
     * @param sr the sample result to validate
     * @return the sample
     */
    public Sample validateSampleResult(Collection<String> problems, Labware lw, Set<List<Integer>> slotSampleIds, SampleResult sr) {
        if (sr.getResult()==null) {
            problems.add("Sample result is missing a result.");
        } else if (sr.getResult()==PassFail.fail && sr.getCommentId()==null) {
            problems.add("Missing comment ID for a fail result.");
        }
        if (sr.getAddress()==null || sr.getSampleId()==null) {
            if (sr.getAddress()==null) {
                problems.add("Sample result is missing a slot address.");
            }
            if (sr.getSampleId()==null) {
                problems.add("Sample result is missing a sample ID.");
            }
            return null;
        }
        Slot slot = getSlot(problems, lw, sr.getAddress());
        if (slot==null) {
            return null;
        }
        Sample sample = getSample(problems, lw.getBarcode(), slot, sr.getSampleId());
        if (sample==null) {
            return null;
        }
        List<Integer> slotSampleId = List.of(slot.getId(), sample.getId());
        if (!slotSampleIds.add(slotSampleId)) {
            problems.add("Multiple results specified for slot "+slot.getAddress()+" in labware "+ lw.getBarcode()
                    +" and sample ID "+sample.getId()+".");
        }
        return sample;
    }

    /**
     * Gets the indicated slot from the given labware
     * @param problems receptacle for problems
     * @param lw the labware
     * @param address the address of the slot
     * @return the indicated slot, or null if it does not exist
     */
    public Slot getSlot(Collection<String> problems, Labware lw, Address address) {
        Optional<Slot> optSlot = lw.optSlot(address);
        if (optSlot.isEmpty()) {
            problems.add("No slot in labware " + lw.getBarcode() + " has address " + address + ".");
        }
        return optSlot.orElse(null);
    }

    /**
     * Gets the specified sample from the given slot
     * @param problems receptacle for problems
     * @param barcode the barcode of the labware (used in problem messages)
     * @param slot the slot that should contain the sample
     * @param sampleId the id of the sample we're looking for
     * @return the sample indicated, or null if it was not found
     */
    public Sample getSample(Collection<String> problems, String barcode, Slot slot, Integer sampleId) {
        var optSample = slot.getSamples().stream()
                .filter(sam -> sam.getId().equals(sampleId))
                .findAny();
        if (optSample.isEmpty()) {
            problems.add("Slot "+slot.getAddress()+" in labware "+ barcode +" does not contain a sample with ID "+sampleId+".");
        }
        return optSample.orElse(null);
    }

    /**
     * Checks that comment ids exist
     * @param problems receptacle for problems
     * @param lrs the labware results we are validating
     * @return comments mapped from their ids
     */
    public Map<Integer, Comment> validateComments(Collection<String> problems, Collection<LabwareResult> lrs) {
        var commentIdStream = lrs.stream()
                        .flatMap(lr -> lr.getSampleResults().stream()
                                .map(SampleResult::getCommentId)
                                .filter(Objects::nonNull)
                        );
        List<Comment> comments = commentValidationService.validateCommentIds(problems, commentIdStream);
        return comments.stream().collect(BasicUtils.toMap(Comment::getId));
    }

    /**
     * Gets the latest stain on each of the given labware
     * @param problems receptacle for problems
     * @param labware the labware to look up stains for
     * @return a map of labware id to the stain operation id
     */
    public Map<Integer, Integer> lookUpStains(Collection<String> problems, Collection<Labware> labware) {
        OperationType stainOpType = loadOpType(problems, "Stain");
        if (stainOpType==null || labware.isEmpty()) {
            return Map.of();
        }
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(stainOpType, labwareIds);
        Map<Integer, Integer> opsMap = makeLabwareOpIdMap(ops);
        List<String> unstainedBarcodes = labware.stream()
                .filter(lw -> !opsMap.containsKey(lw.getId()))
                .map(Labware::getBarcode)
                .collect(toList());
        if (!unstainedBarcodes.isEmpty()) {
            problems.add("No stain has been recorded on the following labware: "+unstainedBarcodes);
        }
        return opsMap;
    }

    /**
     * This is used when making the labware-op map to decide whether the op under consideration
     * takes precedence over the current op listed (if any)
     * @param a the op under consideration
     * @param b the op already listed (or null)
     * @return True if any of the following:<ul>
     *     <li>{@code b} is null</li>
     *     <li>the timestamp of {@code a} is later than that of {@code b}</li>
     *     <li>they have the same timestamp and {@code a} has a higher ID</li>
     * </ul>
     * False otherwise
     */
    public boolean supersedes(Operation a, Operation b) {
        if (b==null) {
            return true;
        }
        int c = a.getPerformed().compareTo(b.getPerformed());
        if (c == 0) {
            c = a.getId().compareTo(b.getId());
        }
        return (c > 0);
    }

    /**
     * Makes a map of labware id to op id from the given operations.
     * Where a labware id is linked to multiple operations, the latest op is selected.
     * @see #supersedes(Operation, Operation)
     * @param ops the operations
     * @return a map of labware id to operation id
     */
    public Map<Integer, Integer> makeLabwareOpIdMap(Collection<Operation> ops) {
        Map<Integer, Operation> opMap = new HashMap<>(ops.size());
        for (Operation op : ops) {
            Set<Integer> labwareIds = op.getActions().stream()
                    .map(a -> a.getDestination().getLabwareId())
                    .collect(toSet());
            for (Integer lwId : labwareIds) {
                if (supersedes(op, opMap.get(lwId))) {
                    opMap.put(lwId, op);
                }
            }
        }
        return opMap.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().getId()));
    }

    /**
     * Creates the operations and records the results and comments
     * @param user the user responsible for recording the results
     * @param opType the result operation type
     * @param lrs the list of labware results to record
     * @param labware the map of barcode to labware
     * @param latestStain the map from labware id to previously recorded stain op id
     * @param commentMap comments mapped from their ids
     * @param work the work to link the operations to (optional)
     * @return the new operations and labware
     */
    public OperationResult createResults(User user, OperationType opType, Collection<LabwareResult> lrs,
                                         UCMap<Labware> labware, Map<Integer, Integer> latestStain,
                                         Map<Integer, Comment> commentMap, Work work) {
        List<Operation> ops = new ArrayList<>(lrs.size());
        List<ResultOp> resultOps = new ArrayList<>();
        List<Labware> labwareList = new ArrayList<>(lrs.size());
        List<OperationComment> opComments = new ArrayList<>();
        for (LabwareResult lr : lrs) {
            Labware lw = labware.get(lr.getBarcode());

            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            for (SampleResult sr : lr.getSampleResults()) {
                Slot slot = lw.getSlot(sr.getAddress());
                Integer refersToOpId = latestStain.get(lw.getId());
                ResultOp resOp = new ResultOp(null, sr.getResult(), op.getId(), sr.getSampleId(), slot.getId(), refersToOpId);
                resultOps.add(resOp);

                if (sr.getCommentId()!=null) {
                    Comment comment = commentMap.get(sr.getCommentId());
                    opComments.add(new OperationComment(null, comment, op.getId(), sr.getSampleId(), slot.getId(), null));
                }
            }
            ops.add(op);
            labwareList.add(lw);
        }

        opCommentRepo.saveAll(opComments);
        resOpRepo.saveAll(resultOps);

        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, labwareList);
    }
}
