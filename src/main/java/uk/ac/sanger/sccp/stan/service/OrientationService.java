package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.OrientationRequest;

/** Service to perform orientation op */
public interface OrientationService {
    /**
     * Validates and records the request
     * @param user the user responsible
     * @param request the request to perform
     * @return the labware and operations
     * @exception ValidationException if the validation fails
     */
    OperationResult perform(User user, OrientationRequest request) throws ValidationException;
}
