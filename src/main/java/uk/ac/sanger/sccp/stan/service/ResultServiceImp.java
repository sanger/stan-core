package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.ResultRequest.LabwareResult;
import uk.ac.sanger.sccp.stan.request.ResultRequest.SampleResult;
import uk.ac.sanger.sccp.stan.service.measurements.SlotMeasurementValidator;
import uk.ac.sanger.sccp.stan.service.measurements.SlotMeasurementValidatorFactory;
import uk.ac.sanger.sccp.stan.service.operation.OpSearcher;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;

@Service
public class ResultServiceImp extends BaseResultService implements ResultService {
    private final OperationCommentRepo opCommentRepo;
    private final ResultOpRepo resOpRepo;
    private final MeasurementRepo measurementRepo;
    private final LabwareNoteRepo lwNoteRepo;

    private final Sanitiser<String> coverageSanitiser;

    private final OperationService opService;
    private final WorkService workService;
    private final CommentValidationService commentValidationService;
    private final SlotMeasurementValidatorFactory slotMeasurementValidatorFactory;
    private final Validator<String> lotValidator;

    @Autowired
    public ResultServiceImp(OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, OperationRepo opRepo,
                            OperationCommentRepo opCommentRepo, ResultOpRepo resOpRepo,
                            MeasurementRepo measurementRepo, LabwareNoteRepo lwNoteRepo,
                            @Qualifier("tissueCoverageSanitiser") Sanitiser<String> coverageSanitiser,
                            LabwareValidatorFactory labwareValidatorFactory,
                            OperationService opService, WorkService workService,
                            CommentValidationService commentValidationService,
                            SlotMeasurementValidatorFactory slotMeasurementValidatorFactory,
                            OpSearcher opSearcher,
                            @Qualifier("lotNumberValidator") Validator<String> lotValidator) {
        super(labwareValidatorFactory, opTypeRepo, opRepo, lwRepo, opSearcher);
        this.opCommentRepo = opCommentRepo;
        this.resOpRepo = resOpRepo;
        this.measurementRepo = measurementRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.coverageSanitiser = coverageSanitiser;
        this.opService = opService;
        this.workService = workService;
        this.commentValidationService = commentValidationService;
        this.slotMeasurementValidatorFactory = slotMeasurementValidatorFactory;
        this.lotValidator = lotValidator;
    }

    @Override
    public OperationResult recordStainQC(User user, ResultRequest request) {
        if (request!=null && request.getOperationType()==null) {
            request.setOperationType("Record result");
        }
        return recordResultForOperation(user, request, "Stain", true, true);
    }

    @Override
    public OperationResult recordVisiumQC(User user, ResultRequest request) {
        return recordResultForOperation(user, request, "Visium permeabilisation", false, false);
    }

    /**
     * Validates and records the results specified
     * @param user the user responsible
     * @param request the request
     * @param refersToOpName the name of the prior operation that this result refers to
     * @param refersRequired whether the prior operation is required to exist
     * @param refersAncestral whether the prior operation should be searched on ancestral labware
     * @return the labware and operations produced
     */
    public OperationResult recordResultForOperation(User user, ResultRequest request, String refersToOpName,
                                                    boolean refersRequired, boolean refersAncestral) {
        Set<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user specified.");
        }
        OperationType opType;
        if (request.getOperationType()==null) {
            opType = null;
            problems.add("No operation type specified.");
        } else {
            opType = loadOpType(problems, request.getOperationType());
        }
        OperationType refersToOpType = loadOpType(problems, refersToOpName);
        if (opType != null && (!opType.inPlace() || !opType.has(OperationTypeFlag.RESULT))) {
            problems.add("The operation type "+opType.getName()+" cannot be used in this operation.");
        }
        UCMap<Labware> labware = validateLabware(problems, request.getLabwareResults());
        boolean sampleResultsRequired = (opType != null && !opType.getName().equalsIgnoreCase("Tissue coverage"));
        boolean resultsRequired = (opType != null && !opType.getName().equalsIgnoreCase("Pretreatment QC"));
        validateLabwareContents(problems, labware, request.getLabwareResults(), sampleResultsRequired, resultsRequired);
        validateLotNumbers(problems, request.getLabwareResults());
        UCMap<List<SlotMeasurementRequest>> measurementMap = validateMeasurements(problems, labware, request.getLabwareResults());
        Map<Integer, Comment> commentMap = validateComments(problems, request.getLabwareResults());
        validateSampleIdsInSampleComments(problems, labware, request.getLabwareResults());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        Map<Integer, Integer> referredOpIds = lookUpPrecedingOps(problems, refersToOpType, labware.values(), refersRequired, refersAncestral);

        if (!problems.isEmpty()) {
            throw new ValidationException("The result request could not be validated.", problems);
        }

        return createResults(user, opType, request.getLabwareResults(), labware, referredOpIds,
                commentMap, measurementMap, work);
    }

    /**
     * Validates the labware. Labware must exist and be nonempty, without repeats.
     * @param problems receptacle for problems
     * @param labwareResults the requested labware results
     * @return a map of labware from its barcode
     */
    public UCMap<Labware> validateLabware(Collection<String> problems, Collection<LabwareResult> labwareResults) {
        List<String> barcodes = labwareResults.stream()
                .map(LabwareResult::getBarcode)
                .collect(toList());
        return loadLabware(problems, barcodes);
    }

    /**
     * Checks the specified labware results
     * @param problems receptacle for problems
     * @param labware map to look up labware by barcode
     * @param labwareResults the labware results to validate
     * @param sampleResultsRequired a boolean indicating whether sample results are required for validation.
     * @param resultsRequired a boolean indicating whether the results field within the sample results is required for validation.
     */
    public void validateLabwareContents(Collection<String> problems, UCMap<Labware> labware,
                                        Collection<LabwareResult> labwareResults,
                                        boolean sampleResultsRequired, boolean resultsRequired) {
        for (LabwareResult lr : labwareResults) {
            Labware lw = labware.get(lr.getBarcode());
            if (lw==null) {
                continue;
            }
            if (lr.getSampleResults().isEmpty()) {
                if (sampleResultsRequired) {
                    problems.add("No results specified for labware " + lw.getBarcode() + ".");
                }
                continue;
            }
            Set<Integer> slotIds = new HashSet<>(lr.getSampleResults().size());
            for (SampleResult sr : lr.getSampleResults()) {
                validateSampleResult(problems, lw, slotIds, sr, resultsRequired);
            }
        }
    }

    /**
     * Checks the lot numbers, where present
     * @param problems receptacle for problems
     * @param lrs labware results
     */
    public void validateLotNumbers(Collection<String> problems, Collection<LabwareResult> lrs) {
        final Consumer<String> addProblem = problems::add;
        lrs.stream()
                .map(LabwareResult::getReagentLot)
                .filter(lot -> lot!=null && !lot.isEmpty())
                .distinct()
                .forEach(lot -> lotValidator.validate(lot, addProblem));
    }

    /**
     * Checks the requested measurements on all the labware
     * @param problems receptacle for problems
     * @param labware map of labware from its barcode
     * @param labwareResults the requests for each labware
     * @return sanitised measurements for each labware barcode
     */
    public UCMap<List<SlotMeasurementRequest>> validateMeasurements(Collection<String> problems, UCMap<Labware> labware,
                                                                    Collection<LabwareResult> labwareResults) {
        if (labwareResults.stream().allMatch(lr -> lr.getSlotMeasurements().isEmpty())) {
            return new UCMap<>(); // no measurements given
        }
        String coverageMeasurementName = MeasurementType.Tissue_coverage.friendlyName();
        SlotMeasurementValidator val = slotMeasurementValidatorFactory.getSlotMeasurementValidator(List.of(coverageMeasurementName));
        val.setValueSanitiser(coverageMeasurementName, coverageSanitiser);
        UCMap<List<SlotMeasurementRequest>> sanMeasurements = new UCMap<>(labware.size());
        for (LabwareResult lr : labwareResults) {
            if (!lr.getSlotMeasurements().isEmpty()) {
                Labware lw = labware.get(lr.getBarcode());
                var sm = val.validateSlotMeasurements(lw, lr.getSlotMeasurements());
                if (lw != null) {
                    sanMeasurements.put(lw.getBarcode(), sm);
                }
            }
        }
        problems.addAll(val.compileProblems());
        return sanMeasurements;
    }

    /**
     * Validates the specified sample result.
     * Possible problems include<ul>
     *     <li>Missing fields in the sample result</li>
     *     <li>Slot already seen in this request</li>
     *     <li>Invalid or empty slot</li>
     * </ul>
     * @param problems receptacle for problems
     * @param lw the labware of this result
     * @param slotIds the accumulated set of slot ids for this labware
     * @param sr the sample result to validate
     * @param resultsRequired a boolean indicating whether the results field within the sample results is required for validation
     */
    public void validateSampleResult(Collection<String> problems, Labware lw, Set<Integer> slotIds, SampleResult sr, boolean resultsRequired) {
        if (resultsRequired && sr.getResult()==null) {
            problems.add("Sample result is missing a result.");
        }
        if (sr.getAddress()==null) {
            problems.add("Sample result is missing a slot address.");
            return;
        }
        Slot slot = getNonemptySlot(problems, lw, sr.getAddress());
        if (slot==null) {
            return;
        }
        if (!slotIds.add(slot.getId())) {
            problems.add("Multiple results specified for slot "+sr.getAddress()+" in labware "+ lw.getBarcode()+".");
        }
    }

    /**
     * Gets the indicated slot from the given labware
     * @param problems receptacle for problems
     * @param lw the labware
     * @param address the address of the slot
     * @return the indicated slot, if it exists and contains samples; otherwise null
     */
    public Slot getNonemptySlot(Collection<String> problems, Labware lw, Address address) {
        Optional<Slot> optSlot = lw.optSlot(address);
        if (optSlot.isEmpty()) {
            problems.add("No slot in labware " + lw.getBarcode() + " has address " + address + ".");
            return null;
        }
        Slot slot = optSlot.get();
        if (slot.getSamples().isEmpty()) {
            problems.add("There are no samples in slot "+address+" of labware "+lw.getBarcode()+".");
            return null;
        }
        return slot;
    }

    /**
     * Checks that comment ids exist
     * @param problems receptacle for problems
     * @param lrs the labware results we are validating
     * @return comments mapped from their ids
     */
    public Map<Integer, Comment> validateComments(Collection<String> problems, Collection<LabwareResult> lrs) {
        var commentIdStream = lrs.stream()
                        .flatMap(lr -> lr.getSampleResults().stream().flatMap(ResultServiceImp::streamCommentIds));
        List<Comment> comments = commentValidationService.validateCommentIds(problems, commentIdStream);
        return comments.stream().collect(BasicUtils.inMap(Comment::getId));
    }

    private static Stream<Integer> streamCommentIds(SampleResult sr) {
        Stream<Integer> stream = sr.getSampleComments().stream().map(SampleIdCommentId::getCommentId);
        if (sr.getCommentId()==null) {
            return stream;
        }
        return Stream.concat(stream, Stream.of(sr.getCommentId()));
    }

    /**
     * Checks that sample ids referenced in {@link SampleResult#getSampleComments() getSampleComments}
     * are appropriate to the labware
     * @param problems receptacle for problems
     * @param lwMap map to look up labware
     * @param lrs the labware results we are validating
     */
    public void validateSampleIdsInSampleComments(Collection<String> problems, UCMap<Labware> lwMap,
                                                  Collection<LabwareResult> lrs) {
        for (LabwareResult lr : lrs) {
            Labware lw = lwMap.get(lr.getBarcode());
            if (lw==null) {
                continue;
            }
            for (SampleResult sr : lr.getSampleResults()) {
                if (sr.getSampleComments().isEmpty()) {
                    continue;
                }
                Slot slot = lw.optSlot(sr.getAddress()).orElse(null);
                if (slot==null) {
                    continue;
                }
                Set<Integer> sampleIds = sr.getSampleComments().stream()
                        .map(SampleIdCommentId::getSampleId)
                        .collect(toCollection(HashSet::new));
                slot.getSamples().forEach(sam -> sampleIds.remove(sam.getId()));
                if (!sampleIds.isEmpty()) {
                    problems.add(String.format(pluralise("Comment{s} specified for sample{s} %s that {is|are} not present in slot %s of labware %s.",
                            sampleIds.size()), sampleIds, slot.getAddress(), lw.getBarcode()));
                }
            }
        }
    }

    /**
     * Gets the latest op of the given op type on the given labware
     * @param problems receptacle for problems
     * @param opType the type of op to look up
     * @param labware the labware to look up ops for
     * @param required whether the preceding op missing constitutes a problem
     * @param ancestral whether to look up operations on ancestral labware
     * @return a map of labware id to the operation id
     */
    public Map<Integer, Integer> lookUpPrecedingOps(Collection<String> problems, OperationType opType,
                                                    Collection<Labware> labware, boolean required, boolean ancestral) {
        if (opType==null || labware.isEmpty()) {
            return Map.of();
        }
        if (ancestral) {
            return lookUpAncestralOpIds(problems, opType, labware, required);
        } else {
            return lookUpLatestOpIds(problems, opType, labware, required);
        }
    }

    /**
     * Creates the operations and records the results and comments
     * @param user the user responsible for recording the results
     * @param opType the result operation type
     * @param lrs the list of labware results to record
     * @param labware the map of barcode to labware
     * @param referredToOpIds the map from labware id to previously recorded op id
     * @param commentMap comments mapped from their ids
     * @param measurementMap (optional) map from labware barcode to requested measurements
     * @param work the work to link the operations to (optional)
     * @return the new operations and labware
     */
    public OperationResult createResults(User user, OperationType opType, Collection<LabwareResult> lrs,
                                         UCMap<Labware> labware, Map<Integer, Integer> referredToOpIds,
                                         Map<Integer, Comment> commentMap, UCMap<List<SlotMeasurementRequest>> measurementMap,
                                         Work work) {
        List<Operation> ops = new ArrayList<>(lrs.size());
        List<ResultOp> resultOps = new ArrayList<>();
        List<Labware> labwareList = new ArrayList<>(lrs.size());
        List<OperationComment> opComments = new ArrayList<>();
        List<Measurement> measurements = new ArrayList<>();
        List<LabwareNote> notes = new ArrayList<>();
        for (LabwareResult lr : lrs) {
            Labware lw = labware.get(lr.getBarcode());

            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            for (SampleResult sr : lr.getSampleResults()) {
                Map<Integer, Set<Comment>> sampleIdComments = sr.getSampleComments().stream()
                        .collect(groupingBy(SampleIdCommentId::getSampleId,
                                mapping((SampleIdCommentId sc) -> commentMap.get(sc.getCommentId()), toSet())));
                Slot slot = lw.getSlot(sr.getAddress());
                Integer refersToOpId = referredToOpIds.get(lw.getId());
                Comment singleComment = (sr.getCommentId()!=null ? commentMap.get(sr.getCommentId()) : null);
                for (Sample sample : slot.getSamples()) {
                    if (sr.getResult()!=null) {
                        ResultOp resOp = new ResultOp(null, sr.getResult(), op.getId(), sample.getId(), slot.getId(), refersToOpId);
                        resultOps.add(resOp);
                    }
                    if (singleComment != null) {
                        opComments.add(new OperationComment(null, singleComment, op.getId(), sample.getId(), slot.getId(), null));
                    }
                    Set<Comment> sampleComments = sampleIdComments.get(sample.getId());
                    if (!nullOrEmpty(sampleComments)) {
                        for (Comment com : sampleComments) {
                            if (com != singleComment) {
                                opComments.add(new OperationComment(null, com, op.getId(), sample.getId(), slot.getId(), null));
                            }
                        }
                    }
                }
            }
            var sms = measurementMap.get(lw.getBarcode());
            if (!nullOrEmpty(sms)) {
                makeMeasurements(measurements, lw, op.getId(), sms);
            }
            ops.add(op);
            labwareList.add(lw);
            if (lr.getCosting()!=null) {
                notes.add(new LabwareNote(null, lw.getId(), op.getId(), "costing", lr.getCosting().name()));
            }
            if (!nullOrEmpty(lr.getReagentLot())) {
                notes.add(new LabwareNote(null, lw.getId(), op.getId(), "reagent lot", lr.getReagentLot()));
            }
        }

        if (!opComments.isEmpty()) {
            opCommentRepo.saveAll(opComments);
        }
        if (!resultOps.isEmpty()) {
            resOpRepo.saveAll(resultOps);
        }

        if (!measurements.isEmpty()) {
            measurementRepo.saveAll(measurements);
        }

        if (!notes.isEmpty()) {
            lwNoteRepo.saveAll(notes);
        }

        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, labwareList);
    }

    /**
     * Makes (unsaved) measurements for the given SlotMeasurementRequests.
     * For each SlotMeasurementRequest there will be a measurement for each
     * sample in the indicated slot.
     * @param measurements receptacle for the new measurements
     * @param lw the labware for the measurements
     * @param opId the operation id for the measurements
     * @param sms the measurement requests
     */
    public void makeMeasurements(List<Measurement> measurements, Labware lw, Integer opId,
                                 Collection<SlotMeasurementRequest> sms) {
        for (SlotMeasurementRequest sm : sms) {
            Slot slot = lw.getSlot(sm.getAddress());
            for (Sample sample : slot.getSamples()) {
                Measurement measurement = new Measurement(null, sm.getName(), sm.getValue(), sample.getId(), opId, slot.getId());
                measurements.add(measurement);
            }
        }
    }
}
