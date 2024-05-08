package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.CleanOutRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

public interface CleanOutService {
    /**
     * Validates and records the clean out operation
     * @param user the user responsible for the request
     * @param request the request to clean out
     * @return the result of the operation
     * @exception ValidationException the request failed validation
     */
    OperationResult perform(User user, CleanOutRequest request) throws ValidationException;
}
