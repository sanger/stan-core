package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.LibraryPrepRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for performing library prep, which comprises Transfer, Dual index and Amplification ops.
 */
public interface LibraryPrepService {
    /**
     * Validates and records the request.
     * This method internally executes a transaction and may discard labware from storage.
     * @param user the user responsible
     * @param request the request to perform
     * @return the destination labware and operations recorded
     * @exception ValidationException if the request fails validation
     */
    OperationResult perform(User user, LibraryPrepRequest request) throws ValidationException;
}
