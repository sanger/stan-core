package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.stain.StainRequest;
import uk.ac.sanger.sccp.stan.request.stain.TimeMeasurement;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class StainServiceImp implements StainService {
    private final StainTypeRepo stainTypeRepo;
    private final LabwareRepo labwareRepo;
    private final OperationTypeRepo opTypeRepo;
    private final MeasurementRepo measurementRepo;
    private final OperationCommentRepo opComRepo;

    private final LabwareValidatorFactory labwareValidatorFactory;
    private final WorkService workService;
    private final OperationService opService;
    private final CommentValidationService commentValidationService;

    public StainServiceImp(StainTypeRepo stainTypeRepo, LabwareRepo labwareRepo, OperationTypeRepo opTypeRepo,
                           MeasurementRepo measurementRepo, OperationCommentRepo opComRepo,
                           LabwareValidatorFactory labwareValidatorFactory, WorkService workService,
                           OperationService opService, CommentValidationService commentValidationService) {
        this.stainTypeRepo = stainTypeRepo;
        this.labwareRepo = labwareRepo;
        this.opTypeRepo = opTypeRepo;
        this.measurementRepo = measurementRepo;
        this.opComRepo = opComRepo;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.workService = workService;
        this.opService = opService;
        this.commentValidationService = commentValidationService;
    }

    @Override
    public List<StainType> getEnabledStainTypes() {
        return stainTypeRepo.findAllByEnabled(true);
    }

    /**
     * Looks up the labware and checks for any problems
     * @param problems the receptacle for problems
     * @param barcodes the labware barcodes to look up
     * @return the loaded labware
     */
    public Collection<Labware> validateLabware(Collection<String> problems, Collection<String> barcodes) {
        if (barcodes==null || barcodes.isEmpty()) {
            problems.add("No barcodes supplied.");
            return List.of();
        }
        LabwareValidator validator = labwareValidatorFactory.getValidator();
        validator.setUniqueRequired(true);
        validator.loadLabware(labwareRepo, barcodes);
        validator.validateSources();
        problems.addAll(validator.getErrors());
        return validator.getLabware();
    }

    /**
     * Loads the indicated stain type
     * @param problems the receptacle for problems
     * @param name the name of the stain type
     * @return the stain type, if found; null if it is not found
     */
    public StainType validateStainType(Collection<String> problems, String name) {
        if (name==null || name.isEmpty()) {
            problems.add("No stain type specified.");
            return null;
        }
        Optional<StainType> optSt = stainTypeRepo.findByName(name);
        if (optSt.isEmpty()) {
            problems.add("Stain type not found: "+repr(name));
            return null;
        }
        return optSt.get();
    }

    /**
     * Validates the measurements
     * @param problems the receptacle for problems
     * @param stainType the stain type requested
     * @param timeMeasurements the requested time measurements
     * @return the time measurements, with the name sanitised to the expected form
     */
    public List<TimeMeasurement> validateMeasurements(Collection<String> problems, StainType stainType, Collection<TimeMeasurement> timeMeasurements) {
        if (stainType==null) {
            return List.of(); // cannot validate measurements without a stain type
        }
        if (timeMeasurements==null || timeMeasurements.isEmpty()) {
            if (!stainType.getMeasurementTypes().isEmpty()) {
                problems.add("No measurements supplied for stain.");
            }
            return List.of();
        }
        List<String> expectedMeasurementTypes = stainType.getMeasurementTypes();
        if (expectedMeasurementTypes.isEmpty()) {
            problems.add("Measurements are not expected for stain type "+stainType.getName()+".");
            return List.of();
        }
        UCMap<String> caseMap = UCMap.from(expectedMeasurementTypes, Function.identity());
        Set<String> repeated = new HashSet<>();
        Map<String, Integer> measurementMap = new HashMap<>();
        Set<String> unknown = new HashSet<>();
        boolean anyBadValues = false;
        for (TimeMeasurement tm : timeMeasurements) {
            String mt = caseMap.get(tm.getName());
            if (mt==null) {
                unknown.add(tm.getName());
                continue;
            }
            if (tm.getSeconds() <= 0) {
                anyBadValues = true;
            }
            if (measurementMap.containsKey(mt)) {
                repeated.add(mt);
            }
            measurementMap.put(mt, tm.getSeconds());
        }
        Set<String> missing = caseMap.values().stream()
                .filter(mt -> !measurementMap.containsKey(mt))
                .collect(toSet());
        if (!repeated.isEmpty()) {
            problems.add("Repeated measurement: "+repeated);
        }
        if (!unknown.isEmpty()) {
            problems.add("Unexpected measurement given: "+BasicUtils.reprCollection(unknown));
        }
        if (!missing.isEmpty()) {
            problems.add("Missing measurements: "+missing);
        }
        if (anyBadValues) {
            problems.add("Time measurements must be greater than zero.");
        }
        return measurementMap.entrySet().stream()
                .map(e -> new TimeMeasurement(e.getKey(), e.getValue()))
                .collect(toList());
    }

    /**
     * Creates the specified stain operations
     * @param user the user recording the operations
     * @param labware the labware
     * @param stainTypes the stain types
     * @return the newly created operations
     */
    public List<Operation> createOperations(User user, Collection<Labware> labware, Collection<StainType> stainTypes) {
        OperationType opType = opTypeRepo.getByName("Stain");
        return labware.stream()
                .map(lw -> createOperation(user, lw, opType, stainTypes))
                .collect(toList());
    }

    /**
     * Creates an operation with a stain type
     * @param user the user recording the operation
     * @param labware the item of labware
     * @param opType the operation type
     * @param stainTypes the stain types for the operation
     * @return the newly created operation
     */
    public Operation createOperation(User user, Labware labware, OperationType opType, Collection<StainType> stainTypes) {
        Operation op = opService.createOperationInPlace(opType, user, labware, null, null);
        stainTypeRepo.saveOperationStainTypes(op.getId(), stainTypes);
        return op;
    }

    /**
     * Records measurements against the given operations
     * @param ops the operations
     * @param tms the specification of the time measurements
     * @return the newly created measurements
     */
    public Iterable<Measurement> recordMeasurements(Collection<Operation> ops, Collection<TimeMeasurement> tms) {
        if (tms.isEmpty()) {
            return List.of();
        }
        List<Measurement> measurements = ops.stream().map(Operation::getId)
                .flatMap(opId -> tms.stream().map(tm ->
                        new Measurement(null, tm.getName(), String.valueOf(tm.getSeconds()), null, opId, null)
                ))
                .collect(toList());
        return measurementRepo.saveAll(measurements);
    }

    /**
     * Records the given comments against every slot/sample in the given operations.
     * @param ops the created operations
     * @param comments the comments to record
     */
    public void recordComments(List<Operation> ops, List<Comment> comments) {
        List<OperationComment> opComs = ops.stream()
                .flatMap(op -> op.getActions().stream())
                .flatMap(ac -> comments.stream()
                        .map(com -> new OperationComment(null, com, ac.getOperationId(), ac.getSample().getId(), ac.getDestination().getId(), null))
                ).toList();
        opComRepo.saveAll(opComs);
    }

    @Override
    public OperationResult recordStain(User user, StainRequest request) {
        Collection<String> problems = new LinkedHashSet<>();
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        Collection<Labware> labware = validateLabware(problems, request.getBarcodes());
        StainType stainType = validateStainType(problems, request.getStainType());
        List<TimeMeasurement> measurements = validateMeasurements(problems, stainType, request.getTimeMeasurements());
        List<Comment> comments;
        if (nullOrEmpty(request.getCommentIds())) {
            comments = List.of();
        } else {
            comments = commentValidationService.validateCommentIds(problems, request.getCommentIds().stream());
        }
        if (!problems.isEmpty()) {
            throw new ValidationException("The stain request could not be validated.", problems);
        }

        List<Operation> ops = createOperations(user, labware, List.of(stainType));
        recordMeasurements(ops, measurements);
        if (!comments.isEmpty()) {
            recordComments(ops, comments);
        }
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, labware);
    }
}
