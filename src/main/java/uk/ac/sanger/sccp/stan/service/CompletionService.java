package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.CompletionRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

import javax.persistence.EntityNotFoundException;
import java.util.List;

/**
 * Service for executing {@link CompletionRequest}
 */
public interface CompletionService {
    /**
     * Validate and execute the given request
     * @param user the user responsible for the request
     * @param request the completion request
     * @return the operations and labware
     * @exception ValidationException validation fails
     */
    OperationResult perform(User user, CompletionRequest request) throws ValidationException;

    /**
     * Gets slot addresses used in the preceding probe hyb op
     * @param barcode labware barcode
     * @return list of relevant addresses
     */
    List<Address> getProbeHybSlotAddresses(String barcode) throws EntityNotFoundException;
}
