package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
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
    private final Clock clock;
    private final ValidationHelperFactory valFactory;
    private final RoiMetricValidationService valService;
    private final OperationService opService;
    private final WorkService workService;
    private final RoiMetricRepo roiMetricRepo;

    @Autowired
    public RoiMetricServiceImp(Clock clock, ValidationHelperFactory valFactory,
                               RoiMetricValidationService valService, OperationService opService,
                               WorkService workService, RoiMetricRepo roiMetricRepo) {
        this.clock = clock;
        this.valFactory = valFactory;
        this.valService = valService;
        this.opService = opService;
        this.workService = workService;
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
        }
        val.work = helper.checkWork(request.getWorkNumber());
        val.opType = helper.checkOpType(request.getOperationType(), OperationTypeFlag.IN_PLACE);
        val.metrics = valService.validateMetrics(val.problems, val.labware, request.getMetrics());
        return val;
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
        List<SampleMetric> metrics;

        public MetricValidation(Set<String> problems) {
            this.problems = problems;
        }

        void addProblem(String problem) {
            this.problems.add(problem);
        }
    }
}
