package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.LibraryConRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

import java.util.List;

/**
 * Service for performing library construction, which comprises Dual index and Amplification ops.
 */
public interface LibraryConService {
    /**
     * Validates and records the requests.
     * @param user the user responsible
     * @param requests the requests to perform
     * @return the destination labware and operations recorded
     * @exception ValidationException if the requests fail validation
     */
    OperationResult perform(User user, List<LibraryConRequest> requests) throws ValidationException;
}
