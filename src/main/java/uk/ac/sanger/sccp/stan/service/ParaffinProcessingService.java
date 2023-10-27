package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ParaffinProcessingRequest;

/**
 * Service for performing paraffin processing.
 */
public interface ParaffinProcessingService {
    /**
     * Validates and records paraffin processing.
     * @param user the user responsible
     * @param request the specification of the request
     * @return the operations and affected labware
     * @exception ValidationException if the request was invalid
     */
    OperationResult perform(User user, ParaffinProcessingRequest request) throws ValidationException;
}
