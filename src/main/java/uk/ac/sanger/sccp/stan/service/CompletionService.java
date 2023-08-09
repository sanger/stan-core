package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.CompletionRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for executing {@link CompletionRequest}
 */
public interface CompletionService {
    /**
     * Validate and execute the given request
     * @param user the user responsible for the request
     * @param request the completion request
     * @return the operations and labware
     * @exception ValidationException validation fails
     */
    OperationResult perform(User user, CompletionRequest request) throws ValidationException;
}
