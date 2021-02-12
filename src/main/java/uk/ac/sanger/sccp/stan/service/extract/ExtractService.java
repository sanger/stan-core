package uk.ac.sanger.sccp.stan.service.extract;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.ExtractRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

public interface ExtractService {
    /**
     * Performs an extraction (creating labware, recording goals etc.)
     * @param user the user responsible for the extraction
     * @param request the request of what to extract
     * @return the result of the extraction
     */
    OperationResult extract(User user, ExtractRequest request);
}
