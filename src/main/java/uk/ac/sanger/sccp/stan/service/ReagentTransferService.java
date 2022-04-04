package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest;

/**
 * Service for recording a reagent transfer operation
 * @author dr6
 */
public interface ReagentTransferService {
    /**
     * Validates and performs a reagent transfer request.
     * @param user the user responsible for the request
     * @param request the transfer request
     * @return the operations recorded and labware
     * @exception ValidationException the request validation failed
     */
    OperationResult perform(User user, ReagentTransferRequest request) throws ValidationException;
}
