package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.*;

/**
 * A service to deal with sample processing
 * @author bt8
 */
public interface SampleProcessingService {
    /**
     * Adds an external id to a samples tissue
     * @param user the user responsible for the request
     * @param request the request to record add external id
     * @return the operations and labware
     */
    OperationResult addExternalID(User user, AddExternalIDRequest request) throws ValidationException;

    /**
     * Adds external ids to tissue in particular slots of labware
     * @param user the user responsible for the request
     * @param request the request to record add external id
     * @return the operations and labware
     */
    OperationResult addExternalIds(User user, AddExternalIdsRequest request) throws ValidationException;
}
