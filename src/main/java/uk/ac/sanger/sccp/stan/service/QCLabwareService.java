package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.QCLabwareRequest;

/**
 * Service for validating and recording QCLabwareRequest
 * @author dr6
 */
public interface QCLabwareService {
    /**
     * Validates and records a QC labware request.
     * @param user the user responsible for the operations
     * @param request the details to record
     * @return the operations and labware
     * @exception ValidationException the request failed validation
     */
    OperationResult perform(User user, QCLabwareRequest request) throws ValidationException;
}
