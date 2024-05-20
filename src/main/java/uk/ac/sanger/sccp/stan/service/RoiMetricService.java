package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.RoiMetric;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest;

/** Service that records {@link RoiMetric ROI metrics} */
public interface RoiMetricService {
    /**
     * Validates and records ROI metrics
     * @param user the user responsible for the request
     * @param request the request describing the metrics
     * @return the result of the operations
     * @exception ValidationException the request fails validation
     */
    OperationResult perform(User user, SampleMetricsRequest request);
}

