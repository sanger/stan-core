package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;

import java.util.Set;

/**
 * Service to record an operation that creates new labware
 * and copies the contents of existing labware by specified slot addresses
 */
public interface SlotCopyService {
    /**
     * In a transaction, validates and records the operation, creating new labware.
     * Post-transaction, unstores the source labware.
     * @param user the user responsible for the operation
     * @param request the specification of the operation
     * @return the result
     * @exception ValidationException if validation fails
     */
    OperationResult perform(User user, SlotCopyRequest request) throws ValidationException;

    /**
     * Records the specified operation.
     * This is called inside a transaction, after validation.
     * @param user the user responsible
     * @param data the information about the request
     * @param barcodesToUnstore receptacle for barcodes to unstore
     * @return the result
     */
    OperationResult record(User user, SlotCopyValidationService.Data data, Set<String> barcodesToUnstore);
}
