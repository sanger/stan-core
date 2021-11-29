package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.stain.ComplexStainRequest;

/**
 * Service to deal with complex stain request
 */
public interface ComplexStainService {
    /**
     * Validates and records the complex stain request
     * @param user the user responsible for the request
     * @param request the specification of the request
     * @return the operations and labware
     * @exception ValidationException if the request fails validation
     */
    OperationResult perform(User user, ComplexStainRequest request) throws ValidationException;
}
