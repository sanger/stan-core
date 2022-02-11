package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.RecordPermRequest;

/**
 * Service to record permeabilisation
 */
public interface PermService {
    /**
     * Records the perm indicated by the request
     * @param user the user responsible for the request
     * @param request the specification
     * @return the ops and labware used in the request
     */
    OperationResult recordPerm(User user, RecordPermRequest request);
}
