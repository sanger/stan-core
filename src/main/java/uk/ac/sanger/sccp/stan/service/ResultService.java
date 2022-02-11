package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ResultRequest;

public interface ResultService {
    /**
     * Records the given stain QC.
     * @param user the user responsible for the request
     * @param request the request to record
     * @return an operation result (operations and labware)
     * @exception ValidationException if the request is invalid
     */
    OperationResult recordStainQC(User user, ResultRequest request);

    /**
     * Records the given result request for visium permeabilisation
     * @param user the user responsible for the request
     * @param request the request to record
     * @return an operation result (operations and labware)
     * @exception ValidationException if the request is invalid
     */
    OperationResult recordVisiumQC(User user, ResultRequest request);
}
