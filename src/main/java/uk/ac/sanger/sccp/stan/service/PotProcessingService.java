package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.PotProcessingRequest;

/**
 * Service for an op that transfers an original sample to pots.
 */
public interface PotProcessingService {
    /**
     * Validate and perform pot processing.
     * @param user the user responsible
     * @param request the request
     * @return the operations and labware created
     * @exception ValidationException validation failed
     */
    OperationResult perform(User user, PotProcessingRequest request) throws ValidationException;
}
