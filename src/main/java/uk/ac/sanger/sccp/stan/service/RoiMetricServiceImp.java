package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationTypeRepo;
import uk.ac.sanger.sccp.stan.repo.RoiMetricRepo;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest.SampleMetric;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * @author dr6
 */
@Service
public class RoiMetricServiceImp implements RoiMetricService {
    public static final String RUN_NAME = "run";
    public static final String XEN_METRICS_OP = "Xenium metrics",
            XEN_QC_OP = "Xenium analyser QC";

    private final Clock clock;
    private final OperationTypeRepo opTypeRepo;
    private final ValidationHelperFactory valFactory;
    private final RoiMetricValidationService valService;
    private final OperationService opService;
    private final WorkService workService;
    private final RoiMetricRepo roiMetricRepo;
    private final LabwareNoteService lwNoteService;

    @Autowired
    public RoiMetricServiceImp(Clock clock, OperationTypeRepo opTypeRepo,
                               ValidationHelperFactory valFactory,
                               RoiMetricValidationService valService, OperationService opService,
                               WorkService workService, LabwareNoteService lwNoteService,
                               RoiMetricRepo roiMetricRepo) {
        this.clock = clock;
        this.opTypeRepo = opTypeRepo;
        this.valFactory = valFactory;
        this.valService = valService;
        this.opService = opService;
        this.workService = workService;
        this.lwNoteService = lwNoteService;
        this.roiMetricRepo = roiMetricRepo;
    }

    @Override
    public OperationResult perform(User user, SampleMetricsRequest request) {
        MetricValidation val = validate(user, request);
        if (!val.problems.isEmpty()) {
            throw new ValidationException(val.problems);
        }
        return record(user, val);
    }

    /**
     * Validates the given request
     * @param user the user responsible for the request
     * @param request the metrics request
     * @return the result of the validation
     */
    MetricValidation validate(User user, SampleMetricsRequest request) {
        ValidationHelper helper = valFactory.getHelper();
        MetricValidation val = new MetricValidation(helper.getProblems());
        if (user==null) {
            val.addProblem("No user supplied.");
        }
        if (request==null) {
            val.addProblem("No request supplied.");
            return val;
        }

        UCMap<Labware> lwMap = helper.checkLabware(Collections.singletonList(request.getBarcode()));
        if (!lwMap.isEmpty()) {
            val.labware = lwMap.values().iterator().next();
            val.runName = validateRunName(val.problems, val.labware, request.getRunName());
        }
        val.work = helper.checkWork(request.getWorkNumber());
        val.opType = helper.checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        val.metrics = valService.validateMetrics(val.problems, val.labware, request.getMetrics());
        checkForPriorOp(val);
        return val;
    }

    /**
     * Checks for problems with the given run name.
     * @param problems receptacle for problems
     * @param lw the indicated labware
     * @param runName the given run name
     * @return sanitised run name
     */
    public String validateRunName(Collection<String> problems, Labware lw, String runName) {
        if (runName==null) {
            return null;
        }
        runName = runName.trim();
        if (runName.isEmpty()) {
            return null;
        }
        Set<String> runNames = lwNoteService.findNoteValuesForLabware(lw, RUN_NAME);
        if (!runNames.contains(runName)) {
            problems.add(String.format("%s is not a recorded run-name for labware %s.",
                    runName, lw.getBarcode()));
        }
        return runName;
    }

    /**
     * For xenium metrics, check that xenium qc has been recorded on the same labware with the same
     * run name and work number. If any of those pieces of information is missing, the request
     * has already failed validation, so do nothing. If the pieces of information exist and the
     * prior operation is missing, add a problem.
     * @param val loaded validation info
     */
    void checkForPriorOp(MetricValidation val) {
        if (val.opType != null && val.opType.getName().equalsIgnoreCase(XEN_METRICS_OP)
                && val.labware != null && val.runName != null && val.work != null) {
            OperationType opType = opTypeRepo.getByName(XEN_QC_OP);
            if (!priorOpExists(val.labware, opType, val.runName, val.work)) {
                val.addProblem(String.format("%s has not been recorded for labware %s, work %s, run %s.",
                        XEN_QC_OP, val.labware.getBarcode(), val.work.getWorkNumber(), val.runName));
            }
        }
    }

    /**
     * Is there an operation of the given type with the given labware, work and run name?
     * @param lw the labware
     * @param opType the operation type to look for
     * @param runName the run name required
     * @param work the work required
     * @return true if such an operation exists; false otherwise
     */
    boolean priorOpExists(Labware lw, OperationType opType, String runName, Work work) {
        List<LabwareNote> notes = lwNoteService.findNamedNotesForLabwareAndOperationType(RUN_NAME, lw, opType);
        if (notes.isEmpty()) {
            return false;
        }
        Set<Integer> matchingOpIds = notes.stream()
                .filter(note -> runName.equalsIgnoreCase(note.getValue()))
                .map(LabwareNote::getOperationId)
                .collect(toSet());
        if (matchingOpIds.isEmpty()) {
            return false;
        }
        Map<Integer, Set<String>> opWorkNumbers = workService.loadWorkNumbersForOpIds(matchingOpIds);
        return opWorkNumbers.values().stream()
                .flatMap(Set::stream)
                .anyMatch(wn -> wn.equalsIgnoreCase(work.getWorkNumber()));
    }

    /**
     * Records the given request
     * @param user the user responsible for the request
     * @param val the information loaded during validation
     * @return the operations and labware from the request
     */
    OperationResult record(User user, MetricValidation val) {
        Labware lw = val.labware;
        Operation op = opService.createOperationInPlace(val.opType, user, lw, null, null);

        deprecateOldMetrics(clock, lw.getId(), val.metrics);
        recordMetrics(lw.getId(), op.getId(), val.metrics);

        if (val.runName!=null) {
            lwNoteService.createNote(RUN_NAME, lw, op, val.runName);
        }

        List<Operation> ops = List.of(op);
        workService.link(val.work, ops);
        return new OperationResult(ops, List.of(lw));
    }

    /**
     * Deprecate the metrics that are being replaced
     * @param clock clock to get current time from
     * @param lwId labware id
     * @param newMetrics the new metrics
     */
    void deprecateOldMetrics(Clock clock, Integer lwId, List<SampleMetric> newMetrics) {
        Set<String> rois = newMetrics.stream()
                .map(SampleMetric::getRoi)
                .collect(toSet());
        roiMetricRepo.deprecateMetrics(lwId, rois, LocalDateTime.now(clock));
    }

    /**
     * Records the indicated metrics
     * @param lwId the labware id to link to the metrics
     * @param opId the operation id to link to the metrics
     * @param sampleMetrics the details of the metrics
     */
    void recordMetrics(Integer lwId, Integer opId, List<SampleMetric> sampleMetrics) {
        List<RoiMetric> roiMetrics = sampleMetrics.stream()
                .map(sm -> new RoiMetric(lwId, opId, sm.getRoi(), sm.getName(), sm.getValue()))
                .toList();
        roiMetricRepo.saveAll(roiMetrics);
    }

    /** Struct containing information gathered during validation */
    static class MetricValidation {
        Set<String> problems;
        Labware labware;
        Work work;
        OperationType opType;
        String runName;
        List<SampleMetric> metrics;

        public MetricValidation(Set<String> problems) {
            this.problems = problems;
        }

        void addProblem(String problem) {
            this.problems.add(problem);
        }
    }
}
