package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.LibraryConRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for performing library construction, which comprises Dual index and Amplification ops.
 */
public interface LibraryConService {
    /**
     * Validates and records the request.
     * @param user the user responsible
     * @param request the request to perform
     * @return the destination labware and operations recorded
     * @exception ValidationException if the request fails validation
     */
    OperationResult perform(User user, LibraryConRequest request) throws ValidationException;
}
