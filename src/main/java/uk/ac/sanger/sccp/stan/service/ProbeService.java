package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ProbeOperationRequest;

/**
 * Service for recording an operation with probes
 * @author dr6
 */
public interface ProbeService {
    /**
     * Validate and record the given request involving probes
     * @param user the user responsible
     * @param request the request to record the operations
     * @return the labware and operations
     * @exception ValidationException if the request is invalid
     */
    OperationResult recordProbeOperation(User user, ProbeOperationRequest request) throws ValidationException;
}
