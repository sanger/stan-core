package uk.ac.sanger.sccp.stan.service.operation;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.InPlaceOpRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for handling {@link InPlaceOpRequest}
 * @author dr6
 */
public interface InPlaceOpService {
    /**
     * Records the indicated request
     * @param user the user recording the operation
     * @param request the specification of the operation to record
     * @return the result
     */
    OperationResult record(User user, InPlaceOpRequest request);
}
