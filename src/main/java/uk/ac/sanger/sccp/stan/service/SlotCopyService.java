package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;

/**
 * Service to record an operation that creates new labware
 * and copies the contents of existing labware by specified slot addresses
 */
public interface SlotCopyService {
    /**
     * In a transaction, validates and records the operation, creating new labware.
     * Post-transaction, unstored the source labware.
     * @param user the user responsible for the operation
     * @param request the specification of the operation
     * @return the result
     * @exception ValidationException if validation fails
     */
    OperationResult perform(User user, SlotCopyRequest request);
}
