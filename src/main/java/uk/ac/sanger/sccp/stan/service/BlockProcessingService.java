package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;

/**
 * Service dealing with {@link TissueBlockRequest}
 * @author dr6
 */
public interface BlockProcessingService {
    /**
     * Validates and performs the given request.
     * @param user the used responsible for the request
     * @param request the details of the request
     * @return the labware and operations created
     * @exception ValidationException the request validation failed
     */
    OperationResult perform(User user, TissueBlockRequest request) throws ValidationException;
}
