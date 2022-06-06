package uk.ac.sanger.sccp.stan.service.analysis;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.RNAAnalysisRequest.RNAAnalysisLabware;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.analysis.AnalysisMeasurementValidator.AnalysisType;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * @author dr6
 */
@Service
public class RNAAnalysisServiceImp extends BaseResultService implements RNAAnalysisService {
    public static final String RIN_OP_NAME = "RIN analysis";
    public static final String DV200_OP_NAME = "DV200 analysis";

    private final AnalysisMeasurementValidatorFactory measurementValidatorFactory;
    private final WorkService workService;
    private final OperationService opService;
    private final CommentValidationService commentValidationService;
    private final MeasurementRepo measurementRepo;
    private final OperationCommentRepo opComRepo;

    protected RNAAnalysisServiceImp(LabwareValidatorFactory labwareValidatorFactory,
                                    AnalysisMeasurementValidatorFactory measurementValidatorFactory,
                                    WorkService workService, OperationService opService,
                                    CommentValidationService commentValidationService,
                                    OperationTypeRepo opTypeRepo, OperationRepo opRepo, LabwareRepo lwRepo,
                                    MeasurementRepo measurementRepo, OperationCommentRepo opComRepo) {
        super(labwareValidatorFactory, opTypeRepo, opRepo, lwRepo);
        this.measurementValidatorFactory = measurementValidatorFactory;
        this.workService = workService;
        this.opService = opService;
        this.commentValidationService = commentValidationService;
        this.measurementRepo = measurementRepo;
        this.opComRepo = opComRepo;
    }

    @Override
    public OperationResult perform(User user, RNAAnalysisRequest request) {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        final Collection<String> problems = new LinkedHashSet<>();
        OperationType opType = validateOpType(problems, request.getOperationType());
        UCMap<Labware> lwMap = validateLabware(problems, request.getLabware());
        UCMap<List<StringMeasurement>> measurementMap = validateMeasurements(problems, opType, request.getLabware());
        Map<Integer, Comment> commentMap = validateComments(problems, request.getLabware());
        UCMap<Work> workMap = validateWork(problems, request.getLabware());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return recordAnalysis(user, request, opType, lwMap, measurementMap, commentMap, workMap);
    }

    /**
     * Loads the given op type and checks that it is an analysis op type.
     * @param problems receptacle for problems
     * @param opTypeName the specified name of the operation type
     * @return the loaded operation type, if any
     */
    public OperationType validateOpType(Collection<String> problems, String opTypeName) {
        if (opTypeName==null || opTypeName.isEmpty()) {
            problems.add("No operation type specified.");
            return null;
        }
        OperationType opType = loadOpType(problems, opTypeName);
        if (opType!=null && !opType.has(OperationTypeFlag.ANALYSIS)) {
            problems.add("Not an analysis operation: "+opType.getName());
        }
        return opType;
    }

    /**
     * Loads the labware and validates it using {@link #loadLabware}
     * @param problems receptacle for problems
     * @param requestLabware the parts of the request that include barcodes
     * @return the loaded labware mapped from its barcode
     */
    public UCMap<Labware> validateLabware(Collection<String> problems, Collection<RNAAnalysisLabware> requestLabware) {
        if (requestLabware.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>();
        }
        List<String> barcodes = requestLabware.stream().map(RNAAnalysisLabware::getBarcode).collect(toList());
        return loadLabware(problems, barcodes);
    }

    /**
     * Checks and sanitises the measurements specified in the request using an {@link AnalysisMeasurementValidator}.
     * The operation type should be RIN or DV200 because we know what measurements we expect for those.
     * @param problems receptacle for problems
     * @param opType the type of operation being recorded
     * @param requestLabware the request that includes the measurements
     * @return a map from labware barcode to the sanitised measurements
     */
    public UCMap<List<StringMeasurement>> validateMeasurements(Collection<String> problems, OperationType opType,
                                                               Collection<RNAAnalysisLabware> requestLabware) {
        UCMap<List<StringMeasurement>> measMap = new UCMap<>();
        if (opType==null || requestLabware.stream().allMatch(r -> r.getMeasurements().isEmpty())) {
            return measMap;
        }
        final AnalysisType analysisType;
        if (opType.getName().equalsIgnoreCase(RIN_OP_NAME)) {
            analysisType = AnalysisType.RIN;
        } else if (opType.getName().equalsIgnoreCase(DV200_OP_NAME)) {
            analysisType = AnalysisType.DV200;
        } else {
            problems.add("Unexpected measurements for operation type "+opType.getName()+".");
            return measMap;
        }
        var validator = measurementValidatorFactory.makeValidator(analysisType);
        for (var requestLw : requestLabware) {
            List<StringMeasurement> sms = requestLw.getMeasurements();
            if (sms==null || sms.isEmpty()) {
                continue;
            }
            List<StringMeasurement> sanSms = validator.validateMeasurements(sms);
            if (sanSms!=null && !sanSms.isEmpty()) {
                measMap.put(requestLw.getBarcode(), sanSms);
            }
        }
        problems.addAll(validator.compileProblems());
        return measMap;
    }

    /**
     * Loads and validates the comments specified in the requests.
     * Uses {@link CommentValidationService}
     * @param problems receptacle for problems
     * @param requestLabware the parts of the request that may specify comment ids
     * @return a map of comments from their ids
     */
    public Map<Integer, Comment> validateComments(Collection<String> problems,
                                                  Collection<RNAAnalysisLabware> requestLabware) {
        Stream<Integer> commentIdStream = requestLabware.stream()
                .map(RNAAnalysisLabware::getCommentId)
                .filter(Objects::nonNull);
        return commentValidationService.validateCommentIds(problems, commentIdStream).stream()
                .collect(BasicUtils.toMap(Comment::getId));
    }

    /**
     * Loads and validates the work specified in the requests.
     * Uses {@link WorkService}.
     * @param problems receptacle for problems
     * @param requestLabware the parts of the request that may specify work numbers
     * @return a map of work from its work number
     */
    public UCMap<Work> validateWork(Collection<String> problems, Collection<RNAAnalysisLabware> requestLabware) {
        Set<String> workNumbers = requestLabware.stream()
                .map(RNAAnalysisLabware::getWorkNumber)
                .collect(toSet());
        return workService.validateUsableWorks(problems, workNumbers);
    }

    /**
     * Records the analysis specified.
     * The includes operations, measurements, comments, and links to work numbers.
     * @param user the user responsible
     * @param request the request
     * @param opType the type of op to record
     * @param lwMap a map to look up labware from its barcode
     * @param smMap a map from barcode to the measurement names and values that need to be recorded
     * @param commentMap a map to look up comments from their id
     * @param workMap a map to look up work from its work number
     * @return the labware and the operations recorded
     */
    public OperationResult recordAnalysis(User user, RNAAnalysisRequest request, OperationType opType,
                                          UCMap<Labware> lwMap, UCMap<List<StringMeasurement>> smMap,
                                          Map<Integer, Comment> commentMap, UCMap<Work> workMap) {
        int numLabware = request.getLabware().size();
        List<Operation> ops = new ArrayList<>(numLabware);
        List<Labware> labware = new ArrayList<>(numLabware);
        Map<Work, List<Operation>> workOps = new HashMap<>();
        List<Measurement> measurements = new ArrayList<>();
        List<OperationComment> opComs = new ArrayList<>();
        for (var requestLw : request.getLabware()) {
            Labware lw = lwMap.get(requestLw.getBarcode());
            labware.add(lw);
            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            ops.add(op);
            Work work = workMap.get(requestLw.getWorkNumber());
            workOps.computeIfAbsent(work, k -> new ArrayList<>()).add(op);
            List<StringMeasurement> sms = smMap.get(lw.getBarcode());
            if (sms!=null) {
                addMeasurements(measurements, op.getId(), lw, sms);
            }
            if (requestLw.getCommentId()!=null) {
                addOpComs(opComs, commentMap.get(requestLw.getCommentId()), op.getId(), lw);
            }
        }

        // Link work to new operations
        for (var entry : workOps.entrySet()) {
            workService.link(entry.getKey(), entry.getValue());
        }
        // Create measurements
        if (!measurements.isEmpty()) {
            measurementRepo.saveAll(measurements);
        }
        // Create opComs
        if (!opComs.isEmpty()) {
            opComRepo.saveAll(opComs);
        }

        return new OperationResult(ops, labware);
    }

    /**
     * Makes new (unsaved) measurements and puts then in the given measurements collection.
     * @param measurements receptacle for new measurements
     * @param opId the operation id for the measurements
     * @param lw the labware for the measurements
     * @param sms the names and values for the measurements
     */
    public void addMeasurements(Collection<Measurement> measurements, Integer opId, Labware lw, Collection<StringMeasurement> sms) {
        for (Slot slot : lw.getSlots()) {
            for (Sample sample : slot.getSamples()) {
                for (StringMeasurement sm : sms) {
                    Measurement meas = new Measurement(null, sm.getName(), sm.getValue(), sample.getId(), opId, slot.getId());
                    measurements.add(meas);
                }
            }
        }
    }

    /**
     * Makes new operation-comments and puts them in the given collection.
     * @param opComs receptacle for new opComs
     * @param comment the comment being recorded
     * @param opId the operation id for the comment
     * @param lw the labware being commented upon
     */
    public void addOpComs(Collection<OperationComment> opComs, Comment comment, Integer opId, Labware lw) {
        for (Slot slot : lw.getSlots()) {
            for (Sample sample : slot.getSamples()) {
                OperationComment opCom = new OperationComment(null, comment, opId, sample.getId(), slot.getId(), null);
                opComs.add(opCom);
            }
        }
    }

}
