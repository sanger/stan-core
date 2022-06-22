package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SolutionTransferRequest;

/**
 * Service to perform solution transfer.
 * @author dr6
 */
public interface SolutionTransferService {
    /**
     * Validates and performs a solution transfer request.
     * @param user the user responsible
     * @param request the request
     * @return the labware and operations
     * @exception ValidationException if the request is invalid
     */
    OperationResult perform(User user, SolutionTransferRequest request) throws ValidationException;
}
