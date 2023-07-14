package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.FFPEProcessingRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for performing FFPE processing.
 */
public interface FFPEProcessingService {
    /**
     * Validates and records ffpe processing.
     * @param user the user responsible
     * @param request the specification of the request
     * @return the operations and affected labware
     * @exception ValidationException if the request was invalid
     */
    OperationResult perform(User user, FFPEProcessingRequest request) throws ValidationException;
}
