package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

public interface ConfirmOperationService {
    /**
     * Validates and performs a confirm operation request from the given user
     * @param user the user confirming the operation
     * @param request the confirmation request
     * @return a result
     * @exception ValidationException if the request validation failed
     */
    ConfirmOperationResult confirmOperation(User user, ConfirmOperationRequest request) throws ValidationException;
}
