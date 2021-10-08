package uk.ac.sanger.sccp.stan.service.analysis;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.RNAAnalysisRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Service for performing an analysis request.
 * @author dr6
 */
public interface RNAAnalysisService {
    /**
     * Records analysis operations according to the given request.
     * @param user the user responsible for the request
     * @param request the specification of what to record
     * @return the operations and labware recorded in the request
     * @exception ValidationException validation of the request failed
     */
    OperationResult perform(User user, RNAAnalysisRequest request);
}
