package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OpWithSlotCommentsRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for recording an operation in-place with comments on slots.
 */
public interface OpWithSlotCommentsService {
    /**
     * Validates and records the operation in-place as described
     * @param user the user responsible for the operation
     * @param request the specification of the operation
     * @return the operations and labware
     * @exception ValidationException the request fails validation
     */
    OperationResult perform(User user, OpWithSlotCommentsRequest request) throws ValidationException;
}
