package uk.ac.sanger.sccp.stan.service.operation;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.AliquotRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Service to perform an operation from one labware into multiple new labware (first slot).
 */
public interface AliquotService {
    /**
     * Records the specified operations.
     * @param user the user responsible for the operations
     * @param request the specification of the operations
     * @return the result of the operations
     * @exception ValidationException if the request is invalid
     */
    OperationResult perform(User user, AliquotRequest request) throws ValidationException;
}
