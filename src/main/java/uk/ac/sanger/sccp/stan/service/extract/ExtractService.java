package uk.ac.sanger.sccp.stan.service.extract;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.ExtractRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for performing extractions.
 * This creates new labware, populates it, creates operations, discards the sources, and unstores them.
 */
public interface ExtractService {
    /**
     * Performs an extraction (creating labware, recording goals etc.) in a transaction,
     * then unstores the discarded source labware.
     * @param user the user responsible for the extraction
     * @param request the request of what to extract
     * @return the result of the extraction
     */
    OperationResult extractAndUnstore(User user, ExtractRequest request);
}
