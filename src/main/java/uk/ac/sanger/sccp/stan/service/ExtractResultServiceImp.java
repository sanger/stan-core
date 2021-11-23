package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.ExtractResultRequest;
import uk.ac.sanger.sccp.stan.request.ExtractResultRequest.ExtractResultLabware;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class ExtractResultServiceImp extends BaseResultService implements ExtractResultService {
    private final Sanitiser<String> concentrationSanitiser;
    private final WorkService workService;
    private final CommentValidationService commentValidationService;
    private final OperationService opService;
    private final OperationCommentRepo opCommentRepo;
    private final MeasurementRepo measurementRepo;
    private final ResultOpRepo resultOpRepo;

    public ExtractResultServiceImp(LabwareValidatorFactory labwareValidatorFactory,
                                   @Qualifier("concentrationSanitiser") Sanitiser<String> concentrationSanitiser,
                                   WorkService workService,
                                   CommentValidationService commentValidationService, OperationService opService,
                                   LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo,
                                   OperationCommentRepo opCommentRepo, MeasurementRepo measurementRepo,
                                   ResultOpRepo resultOpRepo) {
        super(labwareValidatorFactory, opTypeRepo, opRepo, lwRepo);
        this.concentrationSanitiser = concentrationSanitiser;
        this.workService = workService;
        this.commentValidationService = commentValidationService;
        this.opService = opService;
        this.opCommentRepo = opCommentRepo;
        this.measurementRepo = measurementRepo;
        this.resultOpRepo = resultOpRepo;
    }

    @Override
    public OperationResult recordExtractResult(User user, ExtractResultRequest request) {
        final Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user specified.");
        }

        OperationType resultOpType = loadOpType(problems, "Record result");
        UCMap<Labware> labware = validateLabware(problems, request.getLabware());
        validateResults(problems, request.getLabware());
        Map<Integer, Comment> commentMap = validateComments(problems, request.getLabware());
        validateMeasurements(problems, request.getLabware());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        Map<Integer, Integer> latestExtracts = lookUpExtracts(problems, labware.values());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return createResults(user, resultOpType, request.getLabware(), labware, latestExtracts, commentMap, work);
    }

    /**
     * Loads the indicated labware and records any problems. Uses {@link #loadLabware}.
     * @param problems receptacle for problems
     * @param requestLabware the parts of the request that specify the labware barcodes
     * @return the found labware mapped from their barcodes
     */
    public UCMap<Labware> validateLabware(Collection<String> problems, Collection<ExtractResultLabware> requestLabware) {
        if (requestLabware.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>();
        }
        List<String> barcodes = requestLabware.stream().map(ExtractResultLabware::getBarcode).collect(toList());
        return loadLabware(problems, barcodes);
    }

    /**
     * Checks that the correct result fields are supplied for the request labware.
     * <ul>
     *     <li>The result field should be {@code pass} or {@code fail}</li>
     *     <li>If the result is {@code pass}, then a concentration should be supplied, and no comment id</li>
     *     <li>If the result is {@code fail}, then a comment id should be supplied, and no concentration</li>
     * </ul>
     * @param problems receptacle for problems
     * @param labware the request labware to validate
     */
    public void validateResults(Collection<String> problems, Collection<ExtractResultLabware> labware) {
        for (ExtractResultLabware erl : labware) {
            if (erl.getResult()==null) {
                problems.add("No result specified for labware "+erl.getBarcode()+".");
            } else if (erl.getResult()==PassFail.pass) {
                if (erl.getCommentId()!=null) {
                    problems.add("Unexpected comment specified for pass on labware "+erl.getBarcode()+".");
                }
                if (erl.getConcentration()==null) {
                    problems.add("No concentration specified for pass on labware "+erl.getBarcode()+".");
                }
            } else {
                if (erl.getCommentId()==null) {
                    problems.add("No comment specified for fail on labware "+erl.getBarcode()+".");
                }
                if (erl.getConcentration()!=null) {
                    problems.add("Unexpected concentration specified for fail on labware "+erl.getBarcode()+".");
                }
            }
        }
    }

    /**
     * Validates the comment using {@link CommentValidationService}
     * @param problems the receptacle for problems found
     * @param labware the requests that include comment ids
     * @return the comments found, mapped from their ids
     */
    public Map<Integer, Comment> validateComments(Collection<String> problems, Collection<ExtractResultLabware> labware) {
        var commentIdStream = labware.stream()
                .map(ExtractResultLabware::getCommentId)
                .filter(Objects::nonNull);

        List<Comment> comments = commentValidationService.validateCommentIds(problems, commentIdStream);
        return comments.stream().collect(BasicUtils.toMap(Comment::getId));
    }

    /**
     * Checks the values given for concentration using {@link Sanitiser}.
     * Null values in requests are skipped.
     * @param problems receptacle for problems found
     * @param labware the requests including the given concentration values
     */
    public void validateMeasurements(Collection<String> problems, Collection<ExtractResultLabware> labware) {
        Set<String> badConcentrations = labware.stream()
                .map(ExtractResultLabware::getConcentration)
                .filter(value -> value!=null && !concentrationSanitiser.isValid(value))
                .collect(BasicUtils.toLinkedHashSet());

        if (!badConcentrations.isEmpty()) {
            problems.add(String.format("Invalid %s given for concentration: %s",
                    badConcentrations.size()==1 ? "value" : "values",
                    badConcentrations));
        }
    }

    /**
     * Looks up the latest extract ops on the given labware and returns a map of labware id to extract op id.
     * @param problems receptacle for any problems found
     * @param labware the labware to look up
     * @return a map of labware id to op id
     */
    public Map<Integer, Integer> lookUpExtracts(Collection<String> problems, Collection<Labware> labware) {
        OperationType extractOpType = loadOpType(problems, "Extract");
        if (extractOpType==null || labware.isEmpty()) {
            return Map.of();
        }
        return lookUpLatestOpIds(problems, extractOpType, labware);
    }

    /**
     * Records the operations, results, comments and measurements specified.
     * Uses {@link OperationService} to record the ops.
     * Uses {@link #addResultData} to create all the other information; then this method saves it all.
     * @param user the user responsible for the operations
     * @param resultOpType the type of operation to record
     * @param erls the requests specifying what to record
     * @param labwareMap a map to look up labware by its barcode
     * @param latestExtracts a map to look up a prior extract op from a labware id
     * @param commentMap a map to look up comment by its id
     * @param work the work to link to the op (or null)
     * @return the operations created and the labware involved
     */
    public OperationResult createResults(User user, OperationType resultOpType, Collection<ExtractResultLabware> erls,
                                         UCMap<Labware> labwareMap, Map<Integer, Integer> latestExtracts,
                                         Map<Integer, Comment> commentMap, Work work) {
        final List<Operation> ops = new ArrayList<>(erls.size());
        final List<ResultOp> resultOps = new ArrayList<>();
        final List<Labware> labwareList = new ArrayList<>(erls.size());
        final List<OperationComment> opComments = new ArrayList<>();
        final List<Measurement> measurements = new ArrayList<>();
        for (ExtractResultLabware erl : erls) {
            Labware lw = labwareMap.get(erl.getBarcode());
            labwareList.add(lw);
            Operation op = opService.createOperationInPlace(resultOpType, user, lw, null, null);
            ops.add(op);
            Integer refersToOpId = latestExtracts.get(lw.getId());
            String concentrationValue = (erl.getConcentration()==null ? null : concentrationSanitiser.sanitise(erl.getConcentration()));
            for (Slot slot : lw.getSlots()) {
                for (Sample sample : slot.getSamples()) {
                    addResultData(resultOps, opComments, measurements, erl, commentMap, op.getId(), refersToOpId,
                            concentrationValue, slot.getId(), sample.getId());
                }
            }
        }
        resultOpRepo.saveAll(resultOps);
        if (!opComments.isEmpty()) {
            opCommentRepo.saveAll(opComments);
        }
        if (!measurements.isEmpty()) {
            measurementRepo.saveAll(measurements);
        }
        if (work != null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, labwareList);
    }

    /**
     * Adds new unsaved results, op-comments and measurements to the given lists as appropriate for the given request.
     * @param resultOps receptacle for result ops
     * @param opComments receptacle for op-comments
     * @param measurements receptacle for measurements
     * @param erl the request details for the labware
     * @param commentMap a map to look up comments by their id
     * @param resultOpId the new operation to link the data to
     * @param refersToOpId the previous operation the results are linked to
     * @param concentration the sanitised value to record for concentration (or null)
     * @param slotId the id of the slot
     * @param sampleId the id of the sample
     */
    public void addResultData(final Collection<ResultOp> resultOps, final Collection<OperationComment> opComments,
                              final Collection<Measurement> measurements, ExtractResultLabware erl,
                              Map<Integer, Comment> commentMap, Integer resultOpId, Integer refersToOpId,
                              String concentration, Integer slotId, Integer sampleId) {
        ResultOp ro = new ResultOp(null, erl.getResult(), resultOpId, sampleId, slotId, refersToOpId);
        resultOps.add(ro);
        if (concentration!=null) {
            Measurement measurement = new Measurement(null, "Concentration", concentration, sampleId,
                    resultOpId, slotId);
            measurements.add(measurement);
        }
        if (erl.getCommentId()!=null) {
            Comment comment = commentMap.get(erl.getCommentId());
            OperationComment opCom = new OperationComment(null, comment, resultOpId, sampleId, slotId, null);
            opComments.add(opCom);
        }
    }
}
