package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.VisiumAnalysisRequest;

/**
 * Service to record {@link VisiumAnalysisRequest}
 * @author dr6
 */
public interface VisiumAnalysisService {
    /**
     * Records visium analysis
     * @param user the user responsible for the request
     * @param request the request to record visium analysis
     * @return the operations and labware
     */
    OperationResult record(User user, VisiumAnalysisRequest request);
}
