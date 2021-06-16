package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmSectionRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Service for confirming section plans
 * @author dr6
 */
public interface ConfirmSectionService {
    /**
     * Validates and performs a confirm operation request from the given user
     * @param user the user confirming the operation
     * @param request the confirmation request
     * @return a result
     * @exception ValidationException if the request validation failed
     */
    OperationResult confirmOperation(User user, ConfirmSectionRequest request) throws ValidationException;
}
