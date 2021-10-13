package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.UnreleaseRequest;

/**
 * Service for processing an {@link UnreleaseRequest}
 * @author dr6
 */
public interface UnreleaseService {
    /**
     * Checks the request, performs the unrelease, returns the operations and updated labware
     * @param user the user responsible for the request
     * @param request the request to unrelease some labware
     * @return the unrelease operations and the affected labware
     * @exception ValidationException if there is a problem with the request
     */
    OperationResult unrelease(User user, UnreleaseRequest request) throws ValidationException;
}
